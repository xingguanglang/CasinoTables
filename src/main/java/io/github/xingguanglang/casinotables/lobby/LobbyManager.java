package io.github.xingguanglang.casinotables.lobby;

import io.github.xingguanglang.casinotables.CasinoTablesPlugin;
import io.github.xingguanglang.casinotables.GameType;
import io.github.xingguanglang.casinotables.Messages;
import io.github.xingguanglang.casinotables.Text;
import io.github.xingguanglang.casinotables.invite.GameInvite;
import io.github.xingguanglang.casinotables.arena.ArenaShape;
import io.github.xingguanglang.casinotables.poker.PokerArenaStyle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LobbyManager {
    private final CasinoTablesPlugin plugin;
    private final Map<UUID, GameLobby> byMember = new HashMap<>();
    private final Map<UUID, GameLobby> byHost = new HashMap<>();
    private final Map<UUID, int[]> blindPreferences = new HashMap<>();
    private final Map<UUID, Integer> buyInPreferences = new HashMap<>();
    private final Map<UUID, Integer> flightPiecePreferences = new HashMap<>();
    private final Map<UUID, PokerArenaStyle> pokerArenaStylePreferences = new HashMap<>();
    /** 赌场轮廓与材质是两个独立维度，这里只按房主记偏好，建场时由各 Manager 取用。 */
    private final Map<UUID, ArenaShape> arenaShapePreferences = new HashMap<>();

    public LobbyManager(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
    }

    public GameLobby create(Player host, GameType type, double bet) {
        if (type.usesRealCurrency()) bet = 0.0;
        GameLobby current = byMember.get(host.getUniqueId());
        if (current != null) {
            if (current.host().equals(host.getUniqueId()) && current.type() == type
                    && Math.abs(current.bet() - bet) < 0.0001) return current;
            Text.send(host, Messages.msg("lobby.create.already-in-room"));
            return null;
        }
        if (plugin.isActiveGame(host.getUniqueId())) {
            Text.send(host, Messages.msg("lobby.create.in-game"));
            return null;
        }
        double min = plugin.getConfig().getDouble("economy.min-bet", 1.0);
        double max = plugin.getConfig().getDouble("economy.max-bet", 10000.0);
        if (!type.usesRealCurrency() && (!Double.isFinite(bet) || bet < min || bet > max)) {
            Text.send(host, Messages.msg("economy.bet.out-of-range",
                    "min", plugin.economy().format(min), "max", plugin.economy().format(max)));
            return null;
        }
        int maximum = type.usesRealCurrency() ? 6 : 4;
        int[] blinds = blindPreferences.getOrDefault(host.getUniqueId(), new int[]{
                plugin.getConfig().getInt("poker.small-blind", 10),
                plugin.getConfig().getInt("poker.big-blind", 20)});
        int buyIn = buyInPreferences.getOrDefault(host.getUniqueId(), defaultCarryLimit());
        int handCap = Math.min(maxCarryLimit(), Math.max(1, blinds[0]) * 200);
        buyIn = Math.max(blinds[1], Math.min(buyIn, handCap));
        int flightPieces = flightPiecePreferences.getOrDefault(host.getUniqueId(),
                Math.max(2, Math.min(4, plugin.getConfig().getInt("flight-chess.default-pieces", 4))));
        PokerArenaStyle pokerArenaStyle = pokerArenaStylePreferences.getOrDefault(host.getUniqueId(),
                PokerArenaStyle.byId(plugin.getConfig().getInt("poker.default-arena-style", 1)));
        GameLobby lobby = new GameLobby(host.getUniqueId(), type, bet, maximum, blinds[0], blinds[1],
                buyIn, flightPieces, pokerArenaStyle);
        byHost.put(host.getUniqueId(), lobby);
        byMember.put(host.getUniqueId(), lobby);
        Text.send(host, type.usesRealCurrency()
                ? Messages.msg("lobby.create.done.real", "game", type.display(), "maximum", maximum)
                : Messages.msg("lobby.create.done.wager", "game", type.display(), "maximum", maximum,
                        "amount", plugin.economy().format(bet)));
        Text.send(host, Messages.msg(type.usesRealCurrency()
                ? "lobby.create.hint.real" : "lobby.create.hint.wager"));
        if (type == GameType.POKER) {
            Text.send(host, Messages.msg("lobby.create.stakes.poker",
                    "small", blinds[0], "big", blinds[1], "buyin", buyIn,
                    "decor", pokerArenaStyle.display(), "styles", PokerArenaStyle.count()));
        } else if (type == GameType.BLACKJACK) {
            // 21 点不吃盲注，下注上下限来自 config 的 blackjack.min-bet / max-bet。
            // 以前这里照搬了德州的盲注，房主改了 /casino blinds 却发现桌上毫无变化。
            int minBet = Math.max(1, plugin.getConfig().getInt("blackjack.min-bet", 10));
            int maxBet = Math.max(minBet, plugin.getConfig().getInt("blackjack.max-bet", 2000));
            Text.send(host, Messages.msg("lobby.create.stakes.blackjack",
                    "min", minBet, "max", maxBet, "buyin", buyIn,
                    "decor", pokerArenaStyle.display(), "styles", PokerArenaStyle.count()));
        }
        if (type == GameType.FLIGHT) {
            Text.send(host, Messages.msg("lobby.create.pieces", "pieces", flightPieces));
        }
        return lobby;
    }

    public GameLobby ensureForInvite(Player host, GameType type, double bet) {
        GameLobby lobby = byHost.get(host.getUniqueId());
        if (lobby == null) return create(host, type, bet);
        if (lobby.type() != type || (!type.usesRealCurrency() && Math.abs(lobby.bet() - bet) >= 0.0001)) {
            Text.send(host, lobby.type().usesRealCurrency()
                    ? Messages.msg("lobby.invite.mismatch.real", "game", lobby.type().display())
                    : Messages.msg("lobby.invite.mismatch.wager", "game", lobby.type().display(),
                            "amount", plugin.economy().format(lobby.bet())));
            return null;
        }
        return lobby;
    }

    public boolean join(Player player, GameInvite invite) {
        GameLobby lobby = byHost.get(invite.sender());
        if (lobby == null || lobby.type() != invite.type()) {
            Text.send(player, Messages.msg("lobby.join.gone"));
            return false;
        }
        return joinLobby(player, lobby);
    }

    public boolean joinOpen(Player player, String hostKey) {
        GameLobby lobby = null;
        try {
            lobby = byHost.get(UUID.fromString(hostKey));
        } catch (IllegalArgumentException ignored) {
            for (GameLobby candidate : byHost.values()) {
                String name = Bukkit.getOfflinePlayer(candidate.host()).getName();
                if (name != null && name.equalsIgnoreCase(hostKey)) {
                    lobby = candidate;
                    break;
                }
            }
        }
        if (lobby == null) {
            boolean activeExists = plugin.activeRoomExists(hostKey);
            if (plugin.joinActiveRoom(player, hostKey)) return true;
            if (activeExists) return false;
            Text.send(player, Messages.msg("lobby.join.unavailable"));
            return false;
        }
        return joinLobby(player, lobby);
    }

    private boolean joinLobby(Player player, GameLobby lobby) {
        if (byMember.containsKey(player.getUniqueId()) || plugin.isActiveGame(player.getUniqueId())) {
            Text.send(player, Messages.msg("lobby.join.busy"));
            return false;
        }
        if (!lobby.add(player.getUniqueId())) {
            Text.send(player, Messages.msg("lobby.full", "maximum", lobby.maximum()));
            return false;
        }
        byMember.put(player.getUniqueId(), lobby);
        broadcast(lobby, Messages.msg("lobby.joined", "player", player.getName(),
                "size", lobby.size(), "maximum", lobby.maximum()));
        Player host = Bukkit.getPlayer(lobby.host());
        if (host != null) {
            Text.send(host, Messages.msg("lobby.host-start-hint"));
            if (lobby.size() < lobby.maximum()) plugin.menus().openInvitePanel(host);
        }
        return true;
    }

    public List<GameLobby> openLobbies() {
        return byHost.values().stream().filter(lobby -> lobby.size() < lobby.maximum()).toList();
    }

    public void start(Player player) {
        GameLobby lobby = byHost.get(player.getUniqueId());
        if (lobby == null) {
            Text.send(player, Messages.msg("lobby.start.not-host"));
            return;
        }
        int minimum = lobby.type().usesRealCurrency() ? 1 : 2;
        if (lobby.size() < minimum) {
            Text.send(player, Messages.msg("lobby.start.too-few", "minimum", minimum));
            return;
        }
        List<Player> online = new ArrayList<>();
        for (UUID id : lobby.members()) {
            Player member = Bukkit.getPlayer(id);
            if (member == null) {
                Text.send(player, Messages.msg("lobby.start.member-offline"));
                return;
            }
            online.add(member);
        }
        if (plugin.startGroup(online, lobby.type(), lobby.bet(), lobby.smallBlind(), lobby.bigBlind(),
                lobby.buyIn(), lobby.flightPieces(), lobby.pokerArenaStyle())) {
            removeLobby(lobby);
        }
    }

    public boolean setPokerArenaStyle(Player player, PokerArenaStyle style) {
        if (style == null) {
            Text.send(player, Messages.msg("lobby.decor.invalid", "count", PokerArenaStyle.count()));
            return false;
        }
        GameLobby lobby = byMember.get(player.getUniqueId());
        if (lobby != null && !lobby.host().equals(player.getUniqueId())) {
            Text.send(player, Messages.msg("lobby.decor.not-host"));
            return false;
        }
        if (lobby != null && !lobby.type().usesRealCurrency()) {
            Text.send(player, Messages.msg("lobby.not-casino-room"));
            return false;
        }
        pokerArenaStylePreferences.put(player.getUniqueId(), style);
        if (lobby != null) {
            lobby.pokerArenaStyle(style);
            broadcast(lobby, Messages.msg("lobby.decor.changed", "id", style.id(),
                    "name", style.display(), "description", style.description()));
        } else {
            Text.send(player, Messages.msg("lobby.decor.preference", "id", style.id(),
                    "name", style.display()));
        }
        return true;
    }

    public boolean setFlightPieces(Player player, int pieces) {
        if (pieces < 2 || pieces > 4) {
            Text.send(player, Messages.msg("pieces.invalid"));
            return false;
        }
        GameLobby lobby = byMember.get(player.getUniqueId());
        if (lobby != null && !lobby.host().equals(player.getUniqueId())) {
            Text.send(player, Messages.msg("lobby.pieces.not-host"));
            return false;
        }
        if (lobby != null && lobby.type() != GameType.FLIGHT) {
            Text.send(player, Messages.msg("lobby.not-flight-room"));
            return false;
        }
        flightPiecePreferences.put(player.getUniqueId(), pieces);
        if (lobby != null) {
            lobby.flightPieces(pieces);
            broadcast(lobby, Messages.msg("lobby.pieces.changed", "pieces", pieces));
        } else {
            Text.send(player, Messages.msg("lobby.pieces.preference", "pieces", pieces));
        }
        return true;
    }

    public boolean setBlinds(Player player, int smallBlind, int bigBlind) {
        int max = maxCarryLimit();
        if (smallBlind < 1 || smallBlind > 10000 || bigBlind <= smallBlind
                || bigBlind > max || bigBlind > (long) smallBlind * 200L) {
            Text.send(player, Messages.msg("lobby.blinds.invalid", "maximum", max));
            return false;
        }
        GameLobby lobby = byMember.get(player.getUniqueId());
        if (lobby != null && !lobby.host().equals(player.getUniqueId())) {
            Text.send(player, Messages.msg("lobby.blinds.not-host"));
            return false;
        }
        if (lobby != null && !lobby.type().usesRealCurrency()) {
            Text.send(player, Messages.msg("lobby.not-casino-room"));
            return false;
        }
        blindPreferences.put(player.getUniqueId(), new int[]{smallBlind, bigBlind});
        if (lobby != null) {
            lobby.blinds(smallBlind, bigBlind);
            lobby.buyIn(Math.max(bigBlind, Math.min(lobby.buyIn(), smallBlind * 200)));
            broadcast(lobby, Messages.msg("lobby.blinds.changed", "small", smallBlind, "big", bigBlind));
        } else {
            Text.send(player, Messages.msg("lobby.blinds.preference", "small", smallBlind, "big", bigBlind));
        }
        return true;
    }

    public boolean setBuyIn(Player player, int buyIn) {
        int max = maxCarryLimit();
        GameLobby lobby = byMember.get(player.getUniqueId());
        if (lobby != null && !lobby.host().equals(player.getUniqueId())) {
            Text.send(player, Messages.msg("lobby.buyin.not-host"));
            return false;
        }
        if (lobby != null && !lobby.type().usesRealCurrency()) {
            Text.send(player, Messages.msg("lobby.not-casino-room"));
            return false;
        }
        int minimum = lobby == null ? blinds(player.getUniqueId())[1] : lobby.bigBlind();
        int smallBlind = lobby == null ? blinds(player.getUniqueId())[0] : lobby.smallBlind();
        max = Math.min(max, smallBlind * 200);
        if (buyIn < minimum || buyIn > max) {
            Text.send(player, Messages.msg("lobby.buyin.out-of-range", "minimum", minimum, "maximum", max));
            return false;
        }
        buyInPreferences.put(player.getUniqueId(), buyIn);
        if (lobby != null) {
            lobby.buyIn(buyIn);
            broadcast(lobby, Messages.msg("lobby.buyin.changed", "buyin", buyIn));
        } else {
            Text.send(player, Messages.msg("lobby.buyin.preference", "buyin", buyIn));
        }
        return true;
    }

    public boolean setBet(Player player, double bet) {
        double min = plugin.getConfig().getDouble("economy.min-bet", 1.0);
        double max = plugin.getConfig().getDouble("economy.max-bet", 10000.0);
        if (!Double.isFinite(bet) || bet < min || bet > max) {
            Text.send(player, Messages.msg("economy.bet.out-of-range",
                    "min", plugin.economy().format(min), "max", plugin.economy().format(max)));
            return false;
        }
        GameLobby lobby = byMember.get(player.getUniqueId());
        if (lobby == null) return true;
        if (lobby.type().usesRealCurrency()) {
            Text.send(player, Messages.msg("lobby.bet.real-currency"));
            return false;
        }
        if (!lobby.host().equals(player.getUniqueId())) {
            Text.send(player, Messages.msg("lobby.bet.not-host"));
            return false;
        }
        lobby.bet(bet);
        plugin.invites().removeAll(player.getUniqueId());
        broadcast(lobby, Messages.msg("lobby.bet.changed", "amount", plugin.economy().format(bet)));
        return true;
    }

    public int[] blinds(UUID player) {
        GameLobby lobby = byMember.get(player);
        if (lobby != null && lobby.type().usesRealCurrency()) {
            return new int[]{lobby.smallBlind(), lobby.bigBlind()};
        }
        int[] value = blindPreferences.getOrDefault(player, new int[]{
                plugin.getConfig().getInt("poker.small-blind", 10),
                plugin.getConfig().getInt("poker.big-blind", 20)});
        return value.clone();
    }

    public int buyIn(UUID player) {
        GameLobby lobby = byMember.get(player);
        if (lobby != null && lobby.type().usesRealCurrency()) return lobby.buyIn();
        return buyInPreferences.getOrDefault(player,
                defaultCarryLimit());
    }

    public int flightPieces(UUID player) {
        GameLobby lobby = byMember.get(player);
        if (lobby != null && lobby.type() == GameType.FLIGHT) return lobby.flightPieces();
        return flightPiecePreferences.getOrDefault(player,
                Math.max(2, Math.min(4, plugin.getConfig().getInt("flight-chess.default-pieces", 4))));
    }

    public ArenaShape arenaShape(UUID player) {
        return arenaShapePreferences.getOrDefault(player, ArenaShape.RECTANGLE);
    }

    public boolean setArenaShape(Player player, ArenaShape shape) {
        if (shape == null) {
            Text.send(player, Messages.msg("lobby.shape.invalid"));
            return false;
        }
        arenaShapePreferences.put(player.getUniqueId(), shape);
        Text.send(player, Messages.msg("lobby.shape.changed",
                "name", shape.display(), "description", shape.description()));
        return true;
    }

    public PokerArenaStyle pokerArenaStyle(UUID player) {
        GameLobby lobby = byMember.get(player);
        if (lobby != null && lobby.type().usesRealCurrency()) return lobby.pokerArenaStyle();
        return pokerArenaStylePreferences.getOrDefault(player,
                PokerArenaStyle.byId(plugin.getConfig().getInt("poker.default-arena-style", 1)));
    }

    public void leave(Player player) {
        GameLobby lobby = byMember.get(player.getUniqueId());
        if (lobby == null) {
            Text.send(player, Messages.msg("lobby.leave.not-in-room"));
            return;
        }
        if (lobby.host().equals(player.getUniqueId())) {
            broadcast(lobby, Messages.msg("lobby.disbanded"));
            removeLobby(lobby);
            plugin.invites().removeAll(player.getUniqueId());
            return;
        }
        lobby.remove(player.getUniqueId());
        byMember.remove(player.getUniqueId());
        broadcast(lobby, Messages.msg("lobby.left", "player", player.getName(),
                "size", lobby.size(), "maximum", lobby.maximum()));
    }

    public void handleQuit(Player player) {
        GameLobby lobby = byMember.get(player.getUniqueId());
        if (lobby == null) return;
        if (lobby.host().equals(player.getUniqueId())) {
            broadcast(lobby, Messages.msg("lobby.host-quit"));
            removeLobby(lobby);
        } else {
            lobby.remove(player.getUniqueId());
            byMember.remove(player.getUniqueId());
            broadcast(lobby, Messages.msg("lobby.member-quit", "player", player.getName()));
        }
    }

    public boolean has(UUID player) { return byMember.containsKey(player); }
    public GameLobby get(UUID player) { return byMember.get(player); }

    public void clear() {
        byMember.clear();
        byHost.clear();
        blindPreferences.clear();
        buyInPreferences.clear();
        flightPiecePreferences.clear();
        pokerArenaStylePreferences.clear();
        arenaShapePreferences.clear();
    }

    private void removeLobby(GameLobby lobby) {
        byHost.remove(lobby.host());
        for (UUID member : lobby.members()) byMember.remove(member);
    }

    private void broadcast(GameLobby lobby, String message) {
        for (UUID id : lobby.members()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) Text.send(player, message);
        }
    }

    private int defaultCarryLimit() {
        return plugin.getConfig().getInt("poker.default-carry-limit",
                plugin.getConfig().getInt("poker.default-buy-in", 2000));
    }

    private int maxCarryLimit() {
        return Math.max(1000, plugin.getConfig().getInt("poker.max-carry-limit",
                plugin.getConfig().getInt("poker.max-buy-in", 1000000)));
    }
}
