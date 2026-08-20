package io.github.casinotables;

import io.github.casinotables.command.GameCommand;
import io.github.casinotables.economy.EconomyHook;
import io.github.casinotables.economy.WagerService;
import io.github.casinotables.flight.FlightManager;
import io.github.casinotables.invite.InviteManager;
import io.github.casinotables.listener.GameListener;
import io.github.casinotables.lobby.LobbyManager;
import io.github.casinotables.luck.LuckService;
import io.github.casinotables.menu.MenuManager;
import io.github.casinotables.poker.PokerManager;
import io.github.casinotables.poker.PokerArenaStyle;
import io.github.casinotables.blackjack.BlackjackManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CasinoTablesPlugin extends JavaPlugin {
    private EconomyHook economy;
    private WagerService wagers;
    private InviteManager invites;
    private LobbyManager lobbies;
    private MenuManager menus;
    private LuckService luck;
    private FlightManager flights;
    private PokerManager poker;
    private BlackjackManager blackjack;
    private Messages messages;
    private final Set<UUID> handPeekers = new HashSet<>();
    private final Set<UUID> boardPeekers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mergeConfigDefaults();
        messages = new Messages(this);
        economy = new EconomyHook(this);
        if (!economy.ready()) {
            getLogger().severe("No Vault economy provider found; CasinoTables has been disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        wagers = new WagerService(this, economy);
        luck = new LuckService(this);
        flights = new FlightManager(this);
        poker = new PokerManager(this);
        blackjack = new BlackjackManager(this);
        lobbies = new LobbyManager(this);
        invites = new InviteManager(this);
        menus = new MenuManager(this);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        PluginCommand command = getCommand("casino");
        if (command == null) {
            getLogger().severe("The /casino command is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        GameCommand handler = new GameCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        getLogger().info("CasinoTables enabled: Ludo, Texas Hold'em and Blackjack.");
    }

    @Override
    public void onDisable() {
        if (invites != null) invites.clear();
        if (lobbies != null) lobbies.clear();
        if (flights != null) flights.shutdown();
        if (poker != null) poker.shutdown();
        if (blackjack != null) blackjack.shutdown();
    }

    /**
     * saveDefaultConfig() 只在 config.yml 不存在时写入，升级插件后新增的配置节不会自动出现在旧文件里。
     * 这里把 jar 内 config.yml 中缺失的叶子键补进去，已有的值一律不动。
     */
    private void mergeConfigDefaults() {
        try (java.io.InputStream stream = getResource("config.yml")) {
            if (stream == null) return;
            org.bukkit.configuration.file.YamlConfiguration bundled =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            List<String> added = new ArrayList<>();
            for (String key : bundled.getKeys(true)) {
                if (bundled.isConfigurationSection(key) || getConfig().contains(key, true)) continue;
                getConfig().set(key, bundled.get(key));
                added.add(key);
            }
            if (added.isEmpty()) return;
            saveConfig();
            getLogger().info("Added " + added.size() + " new option(s) to config.yml: "
                    + String.join(", ", added));
        } catch (java.io.IOException exception) {
            getLogger().warning("Failed to add the new config.yml options: " + exception.getMessage());
        }
    }

    public boolean startGame(Player first, Player second, GameType type, double bet) {
        return startGroup(List.of(first, second), type, bet);
    }

    public boolean startGroup(List<Player> players, GameType type, double bet) {
        return startGroup(players, type, bet,
                getConfig().getInt("poker.small-blind", 10),
                getConfig().getInt("poker.big-blind", 20),
                getConfig().getInt("poker.default-carry-limit",
                        getConfig().getInt("poker.default-buy-in", 2000)));
    }

    public boolean startGroup(List<Player> players, GameType type, double bet, int smallBlind, int bigBlind) {
        return startGroup(players, type, bet, smallBlind, bigBlind,
                getConfig().getInt("poker.default-carry-limit",
                        getConfig().getInt("poker.default-buy-in", Math.max(bigBlind, 2000))));
    }

    public boolean startGroup(List<Player> players, GameType type, double bet,
                              int smallBlind, int bigBlind, int buyIn) {
        return startGroup(players, type, bet, smallBlind, bigBlind, buyIn,
                Math.max(2, Math.min(4, getConfig().getInt("flight-chess.default-pieces", 4))));
    }

    public boolean startGroup(List<Player> players, GameType type, double bet,
                              int smallBlind, int bigBlind, int buyIn, int flightPieces) {
        return startGroup(players, type, bet, smallBlind, bigBlind, buyIn, flightPieces,
                PokerArenaStyle.byId(getConfig().getInt("poker.default-arena-style", 1)));
    }

    public boolean startGroup(List<Player> players, GameType type, double bet,
                              int smallBlind, int bigBlind, int buyIn, int flightPieces,
                              PokerArenaStyle pokerArenaStyle) {
        int minimum = type.usesRealCurrency() ? 1 : 2;
        int maximum = switch (type) {
            case FLIGHT -> 4;
            case POKER -> 6;
            case BLACKJACK -> 6;
        };
        if (players.size() < minimum || players.size() > maximum) {
            if (!players.isEmpty()) Text.send(players.getFirst(), Messages.msg("game.start.player-count",
                    "game", type.display(), "minimum", minimum, "maximum", maximum));
            return false;
        }
        if (players.stream().anyMatch(player -> isActiveGame(player.getUniqueId()))) {
            Player first = players.getFirst();
            Text.send(first, Messages.msg("game.start.busy"));
            return false;
        }
        boolean fixedWager = !type.usesRealCurrency();
        if (fixedWager && !wagers.escrow(players, bet)) {
            return false;
        }
        try {
            switch (type) {
                case FLIGHT -> flights.start(players, bet, flightPieces);
                case POKER -> {
                    return poker.start(players, smallBlind, bigBlind, buyIn, pokerArenaStyle);
                }
                case BLACKJACK -> {
                    return blackjack.start(players, buyIn, pokerArenaStyle);
                }
            }
            return true;
        } catch (Throwable throwable) {
            getLogger().severe("Failed to start " + type + ": " + throwable);
            if (fixedWager) wagers.refund(players, bet);
            for (Player player : players) Text.send(player, Messages.msg(fixedWager
                    ? "game.start.failed.wager"
                    : "game.start.failed.real"));
            return false;
        }
    }

    public boolean isBusy(Player player) {
        return isActiveGame(player.getUniqueId()) || lobbies.has(player.getUniqueId());
    }

    public boolean isActiveGame(UUID player) {
        return flights.has(player) || poker.has(player) || blackjack.has(player);
    }

    public boolean joinActiveRoom(Player player, String hostKey) {
        if (poker.joinActive(player, hostKey)) return true;
        if (blackjack.joinActive(player, hostKey)) return true;
        if (flights.joinSpectator(player, hostKey)) return true;
        return false;
    }

    public List<ActiveRoom> activeRooms() {
        List<ActiveRoom> result = new ArrayList<>();
        result.addAll(poker.activeRooms());
        result.addAll(blackjack.activeRooms());
        result.addAll(flights.activeRooms());
        return List.copyOf(result);
    }

    public boolean activeRoomExists(String hostKey) {
        UUID id = null;
        try { id = UUID.fromString(hostKey); } catch (IllegalArgumentException ignored) { }
        for (ActiveRoom room : activeRooms()) {
            if (id != null && room.host().equals(id)) return true;
            if (room.hostName().equalsIgnoreCase(hostKey)) return true;
        }
        return false;
    }

    public boolean protectedPlayer(UUID player) {
        return flights.protectedPlayer(player) || poker.protectedPlayer(player)
                || blackjack.protectedPlayer(player);
    }

    public boolean handleTableMove(Player player, Location to) {
        return flights.handleMove(player, to) || poker.handleMove(player, to)
                || blackjack.handleMove(player, to);
    }

    public boolean handleTableTeleport(Player player, Location to) {
        return flights.handleTeleport(player, to) || poker.handleTeleport(player, to)
                || blackjack.handleTeleport(player, to);
    }

    public void openFor(Player player) {
        if (flights.openIfActive(player)) return;
        if (poker.openIfActive(player)) return;
        menus.openMain(player);
    }

    public void forfeit(Player player) {
        if (flights.forfeit(player)) return;
        if (poker.forfeit(player)) return;
        if (blackjack.forfeit(player)) return;
        Text.send(player, Messages.msg("game.forfeit.not-in-game"));
    }

    public void requestDraw(Player player) {
        if (flights.requestDraw(player)) return;
        if (poker.requestDraw(player)) return;
        Text.send(player, Messages.msg("game.draw.not-in-game"));
    }

    public void leave(Player player) {
        if (flights.quit(player)) return;
        if (poker.quit(player)) return;
        if (blackjack.quit(player)) return;
        lobbies.leave(player);
    }

    public void handleQuit(Player player) {
        handPeekers.remove(player.getUniqueId());
        boardPeekers.remove(player.getUniqueId());
        invites.removeAll(player.getUniqueId());
        lobbies.handleQuit(player);
        menus.forget(player);
        if (flights.disconnect(player)) return;
        if (poker.disconnect(player)) return;
        if (blackjack.disconnect(player)) return;
    }

    /** 全息、荷官名牌和广播前缀里显示的服务器/赌场名，由 config.yml 的 brand 决定。 */
    public String brand() {
        String value = getConfig().getString("brand", "CasinoTables");
        return value == null || value.isBlank() ? "CasinoTables" : value;
    }

    public Messages messages() { return messages; }

    public EconomyHook economy() { return economy; }
    public WagerService wagers() { return wagers; }
    public InviteManager invites() { return invites; }
    public LobbyManager lobbies() { return lobbies; }
    public MenuManager menus() { return menus; }
    public LuckService luck() { return luck; }
    public FlightManager flights() { return flights; }
    public PokerManager poker() { return poker; }
    public BlackjackManager blackjack() { return blackjack; }

    /**
     * 权限只在写入侧把关（只有管理员能执行 /casino peek），这里不再要求 casinotables.admin，
     * 否则管理员把看牌开给普通玩家时会立刻被这道判断否掉。集合成员本身就是授权凭据，
     * 玩家退出时 handleQuit 会清除。
     */
    public boolean handPeekEnabled(Player player) {
        return handPeekers.contains(player.getUniqueId());
    }

    public void setHandPeek(Player player, boolean enabled) {
        if (enabled) handPeekers.add(player.getUniqueId());
        else handPeekers.remove(player.getUniqueId());
        poker.refreshPrivateVisibility(player);
        blackjack.refreshPrivateVisibility(player);
    }

    /**
     * 德州提前看公牌；与 handPeek 相互独立，只作用于公牌位。
     * 与 handPeekEnabled 一样，权限在写入侧把关，这里只看集合成员。
     */
    public boolean boardPeekEnabled(Player player) {
        return boardPeekers.contains(player.getUniqueId());
    }

    public void setBoardPeek(Player player, boolean enabled) {
        if (enabled) boardPeekers.add(player.getUniqueId());
        else boardPeekers.remove(player.getUniqueId());
        poker.refreshPrivateVisibility(player);
    }
}
