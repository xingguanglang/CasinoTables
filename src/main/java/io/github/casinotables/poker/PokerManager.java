package io.github.casinotables.poker;

import io.github.casinotables.ActiveRoom;
import io.github.casinotables.CasinoTablesPlugin;
import io.github.casinotables.GameType;
import io.github.casinotables.Messages;
import io.github.casinotables.Text;
import io.github.casinotables.arena.ArenaShape;
import io.github.casinotables.arena.ArenaWorld;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PokerManager {
    private final CasinoTablesPlugin plugin;
    private final Map<UUID, PokerGame> byPlayer = new HashMap<>();
    private final Set<PokerGame> games = new HashSet<>();
    private final Set<PokerGame> finishing = new HashSet<>();
    private final ArenaWorld arenaWorld;
    private final PokerRecords records;
    private final PokerPayouts payouts;
    private final BukkitTask task;

    public PokerManager(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
        this.arenaWorld = new ArenaWorld(plugin, "poker-arena", "casinotables_poker");
        this.records = new PokerRecords(plugin);
        this.payouts = new PokerPayouts(plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean start(List<Player> players, int smallBlind, int bigBlind, int carryLimit,
                         PokerArenaStyle arenaStyle) {
        int handCap = Math.max(1, smallBlind) * 200;
        if (bigBlind > handCap) {
            for (Player player : players) Text.send(player,
                    Messages.msg("poker.start.big-blind-too-large"));
            return false;
        }
        carryLimit = Math.min(carryLimit, handCap);
        int[] buyIns = calculateBuyIns(players, bigBlind, carryLimit);
        if (buyIns == null || !withdrawBuyIns(players, buyIns)) return false;
        int slot = -1;
        PokerGame game = null;
        PokerArena arena = null;
        try {
            slot = arenaWorld.allocate();
            game = new PokerGame(this, plugin, players, buyIns, smallBlind, bigBlind, carryLimit);
            ArenaShape shape = plugin.lobbies().arenaShape(players.getFirst().getUniqueId());
            arena = new PokerArena(plugin, arenaWorld, slot, game.seatPlayers(), game.seatNames(),
                    players, game.holeCards(), arenaStyle, shape);
            game.attachArena(arena);
            games.add(game);
            for (Player player : players) byPlayer.put(player.getUniqueId(), game);
            game.start();
            return true;
        } catch (Throwable throwable) {
            if (game != null) {
                games.remove(game);
                unregister(game);
            }
            if (arena != null) arena.close();
            else if (slot >= 0) arenaWorld.release(slot);
            refundAmounts(players.stream().map(Player::getUniqueId).toList(), buyIns,
                    "table start failure rollback");
            plugin.getLogger().severe("Failed to start a Hold'em table: " + throwable);
            for (Player player : players) Text.send(player, Messages.msg("poker.start.failed"));
            return false;
        }
    }

    public boolean has(UUID player) { return byPlayer.containsKey(player); }

    public boolean joinActive(Player player, String hostKey) {
        PokerGame game = findByHost(hostKey);
        if (game == null || !game.joinable()) return false;
        if (plugin.isBusy(player)) {
            Text.send(player, Messages.msg("poker.join.busy"));
            return false;
        }
        return game.join(player);
    }

    public List<ActiveRoom> activeRooms() {
        return games.stream().filter(PokerGame::joinable)
                .map(game -> new ActiveRoom(game.hostId(), game.hostName(), GameType.POKER,
                        game.playerCount(), PokerGame.MAX_SEATS, true,
                        Messages.msg("poker.room.detail", "small", game.smallBlind(),
                                "big", game.bigBlind(), "limit", game.carryLimit())))
                .toList();
    }

    private PokerGame findByHost(String hostKey) {
        UUID id = null;
        try { id = UUID.fromString(hostKey); } catch (IllegalArgumentException ignored) { }
        for (PokerGame game : games) {
            if (id != null && game.hostId().equals(id)) return game;
            if (game.hostName().equalsIgnoreCase(hostKey)) return game;
        }
        return null;
    }
    public boolean protectedPlayer(UUID player) {
        PokerGame game = byPlayer.get(player);
        return game != null && game.protects(player);
    }
    public boolean handleMove(Player player, Location to) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.protects(player.getUniqueId()) && !game.contains(to);
    }
    public boolean handleTeleport(Player player, Location to) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.protects(player.getUniqueId()) && !game.contains(to);
    }
    public void handleJoin(Player player) {
        payouts.retry(player.getUniqueId());
        for (PokerGame game : games) game.hidePrivateFrom(player);
        for (PokerGame game : finishing) game.hidePrivateFrom(player);
    }

    public void refreshPrivateVisibility(Player player) {
        for (PokerGame game : games) game.hidePrivateFrom(player);
        for (PokerGame game : finishing) game.hidePrivateFrom(player);
    }
    PokerRecords.Stats stats(UUID player) { return records.stats(player); }
    public void showHistory(Player player) { records.showHistory(player); }

    public boolean placeChip(Player player, Material material, Location location) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.placeChip(player, material, location);
    }

    public boolean control(Player player, Material material) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.control(player, material);
    }

    public boolean interactExitButton(Player player, Block block) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.interactExitButton(player, block);
    }

    public boolean interactControlButton(Player player, Block block) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.interactControlButton(player, block);
    }

    public boolean interactAtm(Player player, Block block) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.interactAtm(player, block);
    }

    public boolean splitChip(Player player, Material material) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.splitChip(player, material);
    }

    public boolean chipAction(Player player, Material material, boolean merge) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.chipAction(player, material, merge);
    }

    public void requestSplit(Player player) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        if (game == null) {
            Text.send(player, Messages.msg("poker.error.not-in-game"));
            return;
        }
        Material material = player.getInventory().getItemInMainHand().getType();
        if (PokerChips.value(material) <= 0) {
            Text.send(player, Messages.msg("poker.split.hold-chip"));
            return;
        }
        game.splitChip(player, material);
    }

    public void requestMerge(Player player) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        if (game == null) {
            Text.send(player, Messages.msg("poker.error.not-in-game"));
            return;
        }
        game.mergeChips(player);
    }

    public boolean openIfActive(Player player) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        if (game == null) return false;
        game.showStatus(player);
        return true;
    }

    public boolean forfeit(Player player) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        if (game == null) return false;
        game.leave(player, Messages.msg("poker.reason.forfeit"));
        return true;
    }

    public boolean requestDraw(Player player) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        if (game == null) return false;
        game.requestDraw(player);
        return true;
    }

    public boolean quit(Player player) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        if (game == null) return false;
        game.leave(player, Messages.msg("poker.reason.quit"));
        return true;
    }

    public boolean disconnect(Player player) {
        PokerGame game = byPlayer.get(player.getUniqueId());
        if (game == null) return false;
        game.disconnect(player);
        return true;
    }

    void detached(PokerGame game, UUID player) {
        if (byPlayer.get(player) == game) byPlayer.remove(player);
    }

    void attached(PokerGame game, UUID player) { byPlayer.put(player, game); }

    boolean cashOut(UUID player, int amount, String reason) {
        return payouts.pay(player, amount, reason);
    }

    void collectRake(int amount) {
        if (amount <= 0) return;
        String feeAccount = plugin.getConfig().getString("economy.fee-account", "").trim();
        if (feeAccount.isEmpty()) return;
        if (!plugin.economy().deposit(Bukkit.getOfflinePlayer(feeAccount), amount)) {
            plugin.getLogger().warning("Could not deposit the rake of " + amount
                    + " into the fee account " + feeAccount + ".");
        }
    }

    void recordHand(PokerGame game, List<UUID> winnerIds, String reason) {
        int[] handInitial = game.handInitialStacks();
        int[] finalStacks = game.stacks();
        List<UUID> players = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Integer> initial = new ArrayList<>();
        List<Integer> result = new ArrayList<>();
        Set<UUID> realIds = new HashSet<>();
        for (int side = 0; side < game.seatCapacity(); side++) {
            if (!game.realSeat(side)) continue;
            realIds.add(game.playerAt(side));
            if (handInitial[side] <= 0) continue;
            players.add(game.playerAt(side));
            names.add(game.nameAt(side));
            initial.add(handInitial[side]);
            result.add(finalStacks[side]);
        }
        // BOT 的虚拟 UUID 不写入历史，否则胜者列表会残留查无此人的 ID。
        List<UUID> realWinners = winnerIds.stream().filter(realIds::contains).toList();
        records.record(players, names, realWinners, game.smallBlind(), game.bigBlind(),
                game.carryLimit(), initial.stream().mapToInt(Integer::intValue).toArray(),
                new int[players.size()], game.board(), result.stream().mapToInt(Integer::intValue).toArray(),
                Messages.msg("poker.history.hand-reason", "hand", game.handNumber(), "reason", reason),
                game.handStartedAt());
    }

    void finishSession(PokerGame game, List<UUID> winnerIds, String reason) {
        if (!games.remove(game)) return;
        finishing.add(game);
        boolean multipleWinners = winnerIds.size() > 1;
        int[] finalStacks = game.stacks();
        for (int side = 0; side < game.seatCapacity(); side++) {
            if (!game.realSeat(side)) continue;
            UUID id = game.playerAt(side);
            boolean paid = payouts.pay(id, finalStacks[side], "normal table settlement");
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            String amount = plugin.economy().format(finalStacks[side]);
            Text.send(player, Messages.msg(paid ? "poker.session.paid" : "poker.session.pending",
                    "amount", amount));
        }
        String separator = Messages.msg("poker.common.name-separator");
        String winners = winnerIds.stream().map(id -> {
            String name = Bukkit.getOfflinePlayer(id).getName();
            return name == null ? id.toString() : name;
        }).reduce((first, second) -> first + separator + second)
                .orElse(Messages.msg("poker.common.nobody"));
        Bukkit.broadcast(Text.prefixed(Messages.msg("poker.session.broadcast-result",
                "winners", winners, "reason", reason)));
        game.refreshArena();
        for (int side = 0; side < game.seatCapacity(); side++) {
            if (!game.realSeat(side)) continue;
            UUID id = game.playerAt(side);
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            if (winnerIds.contains(id)) {
                Text.send(player, Messages.msg(multipleWinners
                        ? "poker.session.you-won-shared" : "poker.session.you-won", "reason", reason));
            } else Text.send(player, Messages.msg("poker.session.you-lost", "reason", reason));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> finish(game), 40L);
    }

    void settleDraw(PokerGame game, int totalFee, String reason) {
        if (!games.remove(game)) return;
        finishing.add(game);
        int[] finalStacks = game.stacks();
        List<UUID> recordPlayers = new ArrayList<>();
        List<String> recordNames = new ArrayList<>();
        List<Integer> recordInitial = new ArrayList<>();
        List<Integer> recordCashOuts = new ArrayList<>();
        List<Integer> recordFinal = new ArrayList<>();
        for (int side = 0; side < game.seatCapacity(); side++) {
            if (!game.realSeat(side)) continue;
            UUID id = game.playerAt(side);
            boolean paid = payouts.pay(id, finalStacks[side], "unanimous draw settlement");
            Player player = Bukkit.getPlayer(id);
            if (player != null && game.protects(id)) {
                Text.send(player, paid
                        ? Messages.msg("poker.draw.paid", "amount",
                                plugin.economy().format(finalStacks[side]))
                        : Messages.msg("poker.draw.pending"));
            }
            recordPlayers.add(id);
            recordNames.add(game.nameAt(side));
            recordInitial.add(game.initialStacks()[side]);
            recordCashOuts.add(game.cashOuts()[side]);
            recordFinal.add(finalStacks[side]);
        }
        String feeAccount = plugin.getConfig().getString("economy.fee-account", "").trim();
        if (totalFee > 0 && !feeAccount.isEmpty()) {
            plugin.economy().deposit(Bukkit.getOfflinePlayer(feeAccount), totalFee);
        }
        records.record(recordPlayers, recordNames, List.of(), game.smallBlind(), game.bigBlind(),
                game.carryLimit(), recordInitial.stream().mapToInt(Integer::intValue).toArray(),
                recordCashOuts.stream().mapToInt(Integer::intValue).toArray(), game.board(),
                recordFinal.stream().mapToInt(Integer::intValue).toArray(),
                Messages.msg("poker.history.draw-reason", "reason", reason), game.startedAt());
        game.refreshArena();
        Bukkit.broadcast(Text.prefixed(Messages.msg("poker.draw.broadcast")));
        Bukkit.getScheduler().runTaskLater(plugin, () -> finish(game), 80L);
    }

    private void finish(PokerGame game) {
        if (!finishing.remove(game)) return;
        unregister(game);
        game.closeArena();
    }

    private void unregister(PokerGame game) {
        for (int side = 0; side < game.seatCapacity(); side++) {
            if (!game.realSeat(side)) continue;
            UUID id = game.playerAt(side);
            if (byPlayer.get(id) == game) byPlayer.remove(id);
        }
    }

    private void tick() {
        for (PokerGame game : new ArrayList<>(games)) game.tick();
    }

    public void shutdown() {
        task.cancel();
        for (PokerGame game : new ArrayList<>(games)) {
            games.remove(game);
            unregister(game);
            int[] refund = game.abortRefunds();
            for (int side = 0; side < game.seatCapacity(); side++) {
                if (!game.realSeat(side)) continue;
                UUID id = game.playerAt(side);
                payouts.pay(id, refund[side], "server shutdown aborted the table");
                Player player = Bukkit.getPlayer(id);
                if (player != null) Text.send(player, Messages.msg("poker.session.shutdown-refund"));
            }
            game.closeArena();
        }
        for (PokerGame game : new ArrayList<>(finishing)) {
            finishing.remove(game);
            unregister(game);
            game.closeArena();
        }
        records.save();
        payouts.save();
    }

    private int[] calculateBuyIns(List<Player> players, int bigBlind, int carryLimit) {
        int[] result = new int[players.size()];
        for (int side = 0; side < players.size(); side++) {
            Player player = players.get(side);
            result[side] = PokerMoney.carryAmount(plugin.economy().balance(player), carryLimit);
            int minimum = Math.max(bigBlind, PokerChips.MIN_OPENING_COUNT);
            if (result[side] >= minimum) continue;
            for (Player member : players) Text.send(member,
                    Messages.msg("poker.start.insufficient-balance", "player", player.getName(),
                            "minimum", minimum));
            return null;
        }
        return result;
    }

    private boolean withdrawBuyIns(List<Player> players, int[] buyIns) {
        int charged = 0;
        for (int side = 0; side < players.size(); side++) {
            if (!plugin.economy().withdraw(players.get(side), buyIns[side])) {
                refundAmounts(players.subList(0, charged).stream().map(Player::getUniqueId).toList(),
                        java.util.Arrays.copyOf(buyIns, charged), "buy-in charge failure rollback");
                for (Player player : players) Text.send(player, Messages.msg("poker.start.charge-failed"));
                return false;
            }
            charged++;
        }
        return true;
    }

    private void refundAmounts(List<UUID> players, int[] amounts, String reason) {
        for (int side = 0; side < players.size() && side < amounts.length; side++) {
            payouts.pay(players.get(side), amounts[side], reason);
        }
    }
}
