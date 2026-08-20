package io.github.casinotables.blackjack;

import io.github.casinotables.ActiveRoom;
import io.github.casinotables.CasinoTablesPlugin;
import io.github.casinotables.GameType;
import io.github.casinotables.Messages;
import io.github.casinotables.Text;
import io.github.casinotables.arena.ArenaWorld;
import io.github.casinotables.poker.PokerArenaStyle;
import io.github.casinotables.poker.PokerMoney;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BlackjackManager {
    private final CasinoTablesPlugin plugin;
    private final Map<UUID, BlackjackGame> byPlayer = new HashMap<>();
    private final Set<BlackjackGame> games = new HashSet<>();
    private final Set<BlackjackGame> finishing = new HashSet<>();
    private final ArenaWorld arenaWorld;
    private final BlackjackPayouts payouts;
    private final BlackjackRecords records;
    private final BukkitTask task;

    public BlackjackManager(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
        this.arenaWorld = new ArenaWorld(plugin, "blackjack-arena", "casinotables_blackjack");
        this.payouts = new BlackjackPayouts(plugin);
        this.records = new BlackjackRecords(plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean start(List<Player> players, int carryLimit, PokerArenaStyle style) {
        int minBet = Math.max(1, plugin.getConfig().getInt("blackjack.min-bet", 10));
        int maxBet = Math.max(minBet, plugin.getConfig().getInt("blackjack.max-bet", 2000));
        int limit = Math.max(minBet, Math.min(carryLimit,
                Math.max(minBet, plugin.getConfig().getInt("blackjack.max-carry-limit", 1000000))));
        int[] buyIns = calculateBuyIns(players, minBet, limit);
        if (buyIns == null || !withdrawBuyIns(players, buyIns)) return false;
        int slot = -1;
        BlackjackGame game = null;
        try {
            slot = arenaWorld.allocate();
            game = new BlackjackGame(plugin, this, arenaWorld, slot, players, buyIns, limit,
                    minBet, maxBet, style);
            games.add(game);
            for (Player player : players) byPlayer.put(player.getUniqueId(), game);
            return true;
        } catch (Throwable throwable) {
            if (game != null) {
                games.remove(game);
                unregister(game);
                game.closeArena();
            } else if (slot >= 0) {
                arenaWorld.release(slot);
            }
            refund(players.stream().map(Player::getUniqueId).toList(), buyIns, "refund after failed start");
            plugin.getLogger().severe("Failed to start a blackjack table: " + throwable);
            for (Player player : players) Text.send(player, Messages.msg("blackjack.start.failed"));
            return false;
        }
    }

    public boolean has(UUID player) { return byPlayer.containsKey(player); }

    public boolean joinActive(Player player, String hostKey) {
        BlackjackGame game = findByHost(hostKey);
        if (game == null || game.ended()) return false;
        if (plugin.isBusy(player)) {
            Text.send(player, Messages.msg("blackjack.join.busy"));
            return false;
        }
        return game.join(player);
    }

    public List<ActiveRoom> activeRooms() {
        return games.stream().filter(game -> !game.ended())
                .map(game -> new ActiveRoom(game.hostId(), game.nameAt(0), GameType.BLACKJACK,
                        game.playerCount(), BlackjackGame.MAX_SEATS, true,
                        Messages.msg("blackjack.room.description", "hand", game.handNumber())))
                .toList();
    }

    private BlackjackGame findByHost(String hostKey) {
        UUID id = null;
        try { id = UUID.fromString(hostKey); } catch (IllegalArgumentException ignored) { }
        for (BlackjackGame game : games) {
            if (id != null && game.hostId().equals(id)) return game;
            for (int side = 0; side < game.seatCapacity(); side++) {
                if (game.seatedAt(side) && game.nameAt(side).equalsIgnoreCase(hostKey)) return game;
            }
        }
        return null;
    }

    public boolean protectedPlayer(UUID player) {
        BlackjackGame game = byPlayer.get(player);
        return game != null && game.protects(player);
    }

    public boolean handleMove(Player player, Location to) {
        BlackjackGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.protects(player.getUniqueId()) && !game.contains(to);
    }

    public boolean handleTeleport(Player player, Location to) { return handleMove(player, to); }

    public boolean interact(Player player, Block block) {
        BlackjackGame game = byPlayer.get(player.getUniqueId());
        if (game == null) return false;
        BlackjackAction action = game.actionAt(block);
        return action != null && game.act(player, action);
    }

    /** 快捷栏物品右键；材料与 BlackjackArena.syncInventories 一一对应。 */
    public boolean control(Player player, Material held) {
        BlackjackGame game = byPlayer.get(player.getUniqueId());
        if (game == null) return false;
        BlackjackAction action = switch (held) {
            case YELLOW_DYE -> BlackjackAction.BET_MIN;
            case LIME_DYE -> BlackjackAction.BET_CONFIRM;
            case ORANGE_DYE -> BlackjackAction.BET_RECLAIM;
            case FEATHER -> BlackjackAction.HIT;
            case SHIELD -> BlackjackAction.STAND;
            default -> null;
        };
        return action != null && game.act(player, action);
    }

    /** 放筹码进下注区。 */
    public boolean placeChip(Player player, Material material, Location location) {
        BlackjackGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.placeChip(player, material, location);
    }

    /** 手持筹码右键空气：分解；潜行右键：合并。 */
    public boolean chipAction(Player player, Material material, boolean merge) {
        BlackjackGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.chipAction(player, material, merge);
    }

    public boolean forfeit(Player player) {
        BlackjackGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.leave(player, "left the table voluntarily");
    }

    public boolean quit(Player player) { return forfeit(player); }

    public boolean disconnect(Player player) {
        BlackjackGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.leave(player, "left the table on disconnect");
    }

    public void handleJoin(Player player) {
        for (BlackjackGame game : games) game.hidePrivateFrom(player);
    }

    public void refreshPrivateVisibility(Player player) {
        for (BlackjackGame game : games) game.hidePrivateFrom(player);
        for (BlackjackGame game : finishing) game.hidePrivateFrom(player);
    }

    public void showHistory(Player player) { records.show(player); }

    void attached(BlackjackGame game, UUID player) { byPlayer.put(player, game); }

    void detached(BlackjackGame game, UUID player) {
        if (byPlayer.get(player) == game) byPlayer.remove(player);
    }

    boolean cashOut(UUID player, int amount, String reason) { return payouts.pay(player, amount, reason); }

    /** 21 点的庄家优势就是赌场收益；净盈利归入手续费账户，亏损不倒扣。 */
    void collectHouseProfit(int amount) {
        if (amount <= 0) return;
        String account = plugin.getConfig().getString("economy.fee-account", "").trim();
        if (!account.isEmpty()) plugin.economy().deposit(Bukkit.getOfflinePlayer(account), amount);
    }

    void recordHand(BlackjackGame game, List<UUID> winners, int wagered, int houseProfit, String reason) {
        List<UUID> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int side = 0; side < game.seatCapacity(); side++) {
            if (!game.seatedAt(side)) continue;
            ids.add(game.playerAt(side));
            names.add(game.nameAt(side));
        }
        List<UUID> realWinners = winners.stream().filter(ids::contains).toList();
        records.record(ids, names, realWinners, wagered, houseProfit,
                Messages.msg("blackjack.history.reason.hand", "hand", game.handNumber(), "reason", reason),
                game.startedAt());
    }

    void finishSession(BlackjackGame game, String reason) {
        if (!games.remove(game)) return;
        finishing.add(game);
        game.markEnded();
        int[] stacks = game.stacks();
        for (int side = 0; side < game.seatCapacity(); side++) {
            if (!game.seatedAt(side)) continue;
            UUID id = game.playerAt(side);
            boolean paid = payouts.pay(id, stacks[side], "blackjack table settlement");
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                Text.send(player, paid
                        ? Messages.msg("blackjack.session.paid",
                                "amount", plugin.economy().format(stacks[side]))
                        : Messages.msg("blackjack.session.pending"));
            }
        }
        Bukkit.broadcast(Text.prefixed(Messages.msg("blackjack.session.ended", "reason", reason)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> finish(game), 40L);
    }

    private void finish(BlackjackGame game) {
        if (!finishing.remove(game)) return;
        unregister(game);
        game.closeArena();
    }

    private void unregister(BlackjackGame game) {
        for (int side = 0; side < game.seatCapacity(); side++) {
            UUID id = game.playerAt(side);
            if (byPlayer.get(id) == game) byPlayer.remove(id);
        }
    }

    private void tick() {
        for (BlackjackGame game : new ArrayList<>(games)) game.tick();
    }

    public void shutdown() {
        task.cancel();
        for (BlackjackGame game : new ArrayList<>(games)) {
            games.remove(game);
            unregister(game);
            int[] refunds = game.abortRefunds();
            for (int side = 0; side < game.seatCapacity(); side++) {
                if (game.seatedAt(side)) payouts.pay(game.playerAt(side), refunds[side], "server shutdown refund");
            }
            game.closeArena();
        }
        for (BlackjackGame game : new ArrayList<>(finishing)) {
            finishing.remove(game);
            unregister(game);
            game.closeArena();
        }
        records.save();
        payouts.save();
    }

    private int[] calculateBuyIns(List<Player> players, int minBet, int carryLimit) {
        int[] result = new int[players.size()];
        for (int side = 0; side < players.size(); side++) {
            result[side] = PokerMoney.carryAmount(plugin.economy().balance(players.get(side)), carryLimit);
            if (result[side] >= minBet) continue;
            for (Player member : players) {
                Text.send(member, Messages.msg("blackjack.start.min-buyin",
                        "player", players.get(side).getName(), "amount", minBet));
            }
            return null;
        }
        return result;
    }

    private boolean withdrawBuyIns(List<Player> players, int[] buyIns) {
        int charged = 0;
        for (int side = 0; side < players.size(); side++) {
            if (!plugin.economy().withdraw(players.get(side), buyIns[side])) {
                refund(players.subList(0, charged).stream().map(Player::getUniqueId).toList(),
                        Arrays.copyOf(buyIns, charged), "rollback after failed buy-in");
                return false;
            }
            charged++;
        }
        return true;
    }

    private void refund(List<UUID> players, int[] amounts, String reason) {
        for (int side = 0; side < players.size() && side < amounts.length; side++) {
            payouts.pay(players.get(side), amounts[side], reason);
        }
    }
}
