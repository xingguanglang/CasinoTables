package io.github.xingguanglang.casinotables.flight;

import io.github.xingguanglang.casinotables.ActiveRoom;
import io.github.xingguanglang.casinotables.CasinoTablesPlugin;
import io.github.xingguanglang.casinotables.GameType;
import io.github.xingguanglang.casinotables.Messages;
import io.github.xingguanglang.casinotables.Text;
import io.github.xingguanglang.casinotables.arena.ArenaWorld;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FlightManager {
    private final CasinoTablesPlugin plugin;
    private final Map<UUID, FlightGame> byPlayer = new HashMap<>();
    private final Map<UUID, FlightGame> spectators = new HashMap<>();
    private final Set<FlightGame> games = new HashSet<>();
    private final ArenaWorld arenaWorld;
    private final BukkitTask task;

    public FlightManager(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
        this.arenaWorld = new ArenaWorld(plugin, "flight-arena", "casinotables_flight");
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void start(List<Player> players, double bet, int pieceCount) {
        int slot = arenaWorld.allocate();
        int[] colors = FlightGame.colorsFor(players.size());
        FlightArena arena;
        try {
            arena = new FlightArena(plugin, arenaWorld, slot, players, colors, pieceCount);
        } catch (Throwable throwable) {
            arenaWorld.release(slot);
            throw throwable;
        }
        FlightGame game = new FlightGame(this, plugin, players, bet, arena, colors, pieceCount);
        games.add(game);
        for (Player player : players) byPlayer.put(player.getUniqueId(), game);
        game.start();
    }

    public boolean has(UUID player) { return byPlayer.containsKey(player) || spectators.containsKey(player); }

    public boolean joinSpectator(Player player, String hostKey) {
        FlightGame game = findByHost(hostKey);
        if (game == null) return false;
        if (plugin.isBusy(player)) {
            Text.send(player, Messages.msg("flight.error.busy"));
            return false;
        }
        game.addSpectator(player);
        spectators.put(player.getUniqueId(), game);
        return true;
    }

    public List<ActiveRoom> activeRooms() {
        return games.stream().map(game -> new ActiveRoom(game.hostId(), game.hostName(), GameType.FLIGHT,
                game.playerCount(), game.playerCount(), false,
                Messages.msg("flight.room.status", "count", game.playerCount()))).toList();
    }

    private FlightGame findByHost(String hostKey) {
        UUID id = null;
        try { id = UUID.fromString(hostKey); } catch (IllegalArgumentException ignored) { }
        for (FlightGame game : games) {
            if (id != null && game.hostId().equals(id)) return game;
            if (game.hostName().equalsIgnoreCase(hostKey)) return game;
        }
        return null;
    }
    public boolean protectedPlayer(UUID player) {
        FlightGame game = byPlayer.containsKey(player) ? byPlayer.get(player) : spectators.get(player);
        return game != null && game.protects(player);
    }
    public boolean handleMove(Player player, Location to) {
        FlightGame game = byPlayer.containsKey(player.getUniqueId())
                ? byPlayer.get(player.getUniqueId()) : spectators.get(player.getUniqueId());
        return game != null && game.protects(player.getUniqueId()) && !game.contains(to);
    }
    public boolean handleTeleport(Player player, Location to) {
        FlightGame game = byPlayer.containsKey(player.getUniqueId())
                ? byPlayer.get(player.getUniqueId()) : spectators.get(player.getUniqueId());
        return game != null && game.protects(player.getUniqueId()) && !game.contains(to);
    }

    public boolean openIfActive(Player player) {
        FlightGame game = byPlayer.get(player.getUniqueId());
        if (game == null) {
            if (spectators.containsKey(player.getUniqueId())) {
                Text.send(player, Messages.msg("flight.spectate.active"));
                return true;
            }
            return false;
        }
        game.showStatus(player);
        return true;
    }

    public boolean interactButton(Player player, Block block) {
        FlightGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.interactButton(player, block);
    }

    public boolean interactPiece(Player player, Entity clicked) {
        FlightGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.interactPiece(player, clicked);
    }

    public boolean interactControl(Player player, Material material) {
        FlightGame game = byPlayer.get(player.getUniqueId());
        return game != null && game.interactControl(player, material);
    }

    public boolean forfeit(Player player) {
        if (spectators.containsKey(player.getUniqueId())) return quitSpectator(player);
        FlightGame game = byPlayer.get(player.getUniqueId());
        if (game == null) return false;
        game.eliminate(player.getUniqueId(), Messages.msg("flight.reason.forfeit"));
        return true;
    }

    public boolean requestDraw(Player player) {
        if (spectators.containsKey(player.getUniqueId())) {
            Text.send(player, Messages.msg("flight.draw.spectator"));
            return true;
        }
        FlightGame game = byPlayer.get(player.getUniqueId());
        if (game == null) return false;
        game.requestDraw(player);
        return true;
    }

    public boolean quit(Player player) {
        if (spectators.containsKey(player.getUniqueId())) return quitSpectator(player);
        FlightGame game = byPlayer.get(player.getUniqueId());
        if (game == null) return false;
        game.eliminate(player.getUniqueId(), Messages.msg("flight.reason.quit"));
        return true;
    }

    public boolean disconnect(Player player) {
        if (spectators.containsKey(player.getUniqueId())) return quitSpectator(player);
        FlightGame game = byPlayer.get(player.getUniqueId());
        if (game == null) return false;
        game.eliminate(player.getUniqueId(), Messages.msg("flight.reason.disconnect"));
        // PlayerQuitEvent 仍提供玩家对象；立即恢复，避免离线后遗留小游戏快捷栏。
        game.release(player);
        return true;
    }

    private boolean quitSpectator(Player player) {
        FlightGame game = spectators.remove(player.getUniqueId());
        if (game == null) return false;
        game.removeSpectator(player.getUniqueId());
        game.release(player);
        Text.send(player, Messages.msg("flight.spectate.left"));
        return true;
    }

    void eliminated(FlightGame game, UUID player) {
        if (byPlayer.get(player) == game) byPlayer.remove(player);
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            game.release(online);
        }
    }

    void placed(FlightGame game, UUID player) {
        // 已完成的玩家留在房间内观战；最终结算或主动离开时再恢复快照。
    }

    void rank(FlightGame game, List<UUID> standings, String reason) {
        if (!games.remove(game)) return;
        unregister(game);
        List<OfflinePlayer> rankedPlayers = standings.stream().map(Bukkit::getOfflinePlayer).toList();
        double[] payouts = plugin.wagers().payRanked(rankedPlayers, game.bet(), game.playerCount(),
                payoutRates(game.playerCount()));
        StringBuilder result = new StringBuilder(Messages.msg("flight.result.header"));
        for (int rank = 0; rank < standings.size(); rank++) {
            OfflinePlayer ranked = rankedPlayers.get(rank);
            if (rank > 0) result.append(Messages.msg("flight.result.separator"));
            result.append(Messages.msg("flight.result.entry." + rankKey(rank), "rank", rank + 1,
                    "player", ranked.getName() == null ? standings.get(rank) : ranked.getName()));
        }
        result.append(Messages.msg("flight.result.reason", "reason", reason));
        Bukkit.broadcast(Text.prefixed(result.toString()));
        for (int rank = 0; rank < standings.size(); rank++) {
            UUID id = standings.get(rank);
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            double payout = rank < payouts.length ? payouts[rank] : 0.0;
            Text.send(player, Messages.msg("flight.result.payout", "rank", rank + 1,
                    "percent", payoutPercent(game.playerCount(), rank),
                    "amount", plugin.wagers().format(payout)));
        }
        game.closeArena();
    }

    private double[] payoutRates(int playerCount) {
        List<Double> configured = plugin.getConfig().getDoubleList("flight-chess.payout-rates-" + playerCount);
        if (configured.size() == playerCount) {
            double[] rates = new double[playerCount];
            for (int index = 0; index < playerCount; index++) rates[index] = configured.get(index);
            return rates;
        }
        return switch (playerCount) {
            case 2 -> new double[]{0.95, 0.0};
            case 3 -> new double[]{0.78, 0.19, 0.0};
            default -> new double[]{0.55, 0.283, 0.094, 0.0};
        };
    }

    private String payoutPercent(int playerCount, int rank) {
        double[] rates = payoutRates(playerCount);
        if (rank < 0 || rank >= rates.length) return "0%";
        double percent = rates[rank] * 100.0;
        return percent == Math.rint(percent) ? (long) percent + "%" : percent + "%";
    }

    /** 前三名各有自己的配色，具体措辞与颜色都写在语言文件里。 */
    private String rankKey(int rank) {
        return switch (rank) {
            case 0 -> "first";
            case 1 -> "second";
            case 2 -> "third";
            default -> "other";
        };
    }

    void draw(FlightGame game, String reason) {
        if (!games.remove(game)) return;
        unregister(game);
        plugin.wagers().refund(offlinePlayers(game), game.bet());
        Bukkit.broadcast(Text.prefixed(Messages.msg("flight.draw.broadcast", "reason", reason)));
        for (UUID id : game.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                Text.send(player, Messages.msg("flight.draw.refunded", "reason", reason));
            }
        }
        game.closeArena();
    }

    void mutualDraw(FlightGame game, String reason) {
        if (!games.remove(game)) return;
        unregister(game);
        double rate = plugin.getConfig().getDouble("economy.draw-fee-rate", 0.05);
        double refund = plugin.wagers().refundWithFee(offlinePlayers(game), game.bet(), rate);
        Bukkit.broadcast(Text.prefixed(Messages.msg("flight.draw.mutual-broadcast")));
        for (UUID id : game.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) Text.send(player, Messages.msg("flight.draw.mutual-refunded",
                    "amount", plugin.wagers().format(refund)));
        }
        game.closeArena();
    }

    private List<OfflinePlayer> offlinePlayers(FlightGame game) {
        return game.players().stream().map(Bukkit::getOfflinePlayer).toList();
    }

    private void unregister(FlightGame game) {
        for (UUID id : game.players()) if (byPlayer.get(id) == game) byPlayer.remove(id);
        spectators.entrySet().removeIf(entry -> entry.getValue() == game);
    }

    private void tick() {
        for (FlightGame game : new ArrayList<>(games)) game.tick();
    }

    public void shutdown() {
        task.cancel();
        for (FlightGame game : new ArrayList<>(games)) {
            games.remove(game);
            game.cancel();
            unregister(game);
            plugin.wagers().refund(offlinePlayers(game), game.bet());
            for (UUID id : game.players()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) Text.send(player, Messages.msg("flight.shutdown.refunded"));
            }
            game.closeArena();
        }
    }
}
