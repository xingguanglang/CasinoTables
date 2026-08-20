package io.github.casinotables.menu;

import io.github.casinotables.CasinoTablesPlugin;
import io.github.casinotables.ActiveRoom;
import io.github.casinotables.GameType;
import io.github.casinotables.Items;
import io.github.casinotables.Messages;
import io.github.casinotables.Text;
import io.github.casinotables.lobby.GameLobby;
import io.github.casinotables.poker.PokerArenaStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** 开局/房间菜单可在背包 GUI 和聊天点击版之间切换；进行中的 HUD 不使用背包 GUI。 */
public final class MenuManager {
    private enum InputMode { BET, BLINDS, BUY_IN }
    private enum MenuMode { GUI, CHAT }

    private final CasinoTablesPlugin plugin;
    private final Map<UUID, Double> selectedBets = new ConcurrentHashMap<>();
    private final Map<UUID, InputMode> pendingInput = new ConcurrentHashMap<>();
    private final Map<UUID, MenuMode> menuModes = new ConcurrentHashMap<>();

    public MenuManager(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
    }

    public void setGuiMode(Player player, boolean gui) {
        menuModes.put(player.getUniqueId(), gui ? MenuMode.GUI : MenuMode.CHAT);
        Text.send(player, gui ? Messages.msg("menu.mode.gui") : Messages.msg("menu.mode.chat"));
    }

    public void forget(Player player) {
        UUID id = player.getUniqueId();
        menuModes.remove(id);
        pendingInput.remove(id);
        selectedBets.remove(id);
    }

    public void openMain(Player player) {
        if (mode(player) == MenuMode.GUI) openGuiMain(player);
        else openChatMain(player);
    }

    public void openInvitePanel(Player player) {
        GameLobby lobby = plugin.lobbies().get(player.getUniqueId());
        if (lobby == null || !lobby.host().equals(player.getUniqueId())) {
            Text.send(player, Messages.msg("menu.invite.no-host-room"));
            return;
        }
        if (mode(player) == MenuMode.GUI) openGuiLobbyInvites(player, lobby);
        else openChatInvitePanel(player, lobby);
    }

    public void openRooms(Player player) {
        if (mode(player) == MenuMode.GUI) openGuiRooms(player);
        else openChatRooms(player);
    }

    public boolean handleInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GameMenuHolder holder)) return false;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer().equals(player.getUniqueId())) return true;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return true;
        String action = holder.action(slot);
        if (action == null) return true;
        Bukkit.getScheduler().runTask(plugin, () -> executeGuiAction(player, holder.page(), action));
        return true;
    }

    public boolean handleInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GameMenuHolder)) return false;
        event.setCancelled(true);
        return true;
    }

    private void executeGuiAction(Player player, GameMenuHolder.Page page, String action) {
        if (!player.isOnline()) return;
        switch (action) {
            case "@close" -> player.closeInventory();
            case "@chat" -> {
                setGuiMode(player, false);
                player.closeInventory();
                openChatMain(player);
            }
            case "@main" -> openGuiMain(player);
            case "@rooms" -> openGuiRooms(player);
            case "@bet" -> {
                player.closeInventory();
                requestBet(player);
            }
            case "@blinds" -> {
                player.closeInventory();
                requestBlinds(player);
            }
            case "@buyin" -> {
                player.closeInventory();
                requestBuyIn(player);
            }
            case "@history" -> {
                player.closeInventory();
                plugin.poker().showHistory(player);
            }
            case "@bjhistory" -> {
                player.closeInventory();
                plugin.blackjack().showHistory(player);
            }
            default -> {
                player.performCommand(action);
                if (page == GameMenuHolder.Page.OPEN_ROOMS && action.startsWith("casino join ")) {
                    if (plugin.isActiveGame(player.getUniqueId())) player.closeInventory();
                    else openGuiMain(player);
                }
            }
        }
    }

    private void openGuiMain(Player player) {
        double bet = selectedBet(player);
        int[] blinds = plugin.lobbies().blinds(player.getUniqueId());
        int carry = plugin.lobbies().buyIn(player.getUniqueId());
        int flightPieces = plugin.lobbies().flightPieces(player.getUniqueId());
        PokerArenaStyle pokerArenaStyle = plugin.lobbies().pokerArenaStyle(player.getUniqueId());
        GameMenuHolder holder = new GameMenuHolder(player.getUniqueId(), GameMenuHolder.Page.MAIN);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Text.parse(Messages.msg("menu.main.title", "brand", plugin.brand())));
        holder.inventory(inventory);

        put(inventory, holder, 10, Material.FIREWORK_ROCKET, Messages.msg("menu.main.flight.name"),
                "casino create flight " + plainAmount(bet),
                Messages.msg("menu.main.flight.lore-players"),
                Messages.msg("menu.main.flight.lore-wager", "bet", plugin.economy().format(bet)));
        put(inventory, holder, 13, Material.DIAMOND, Messages.msg("menu.main.poker.name"),
                "casino create poker", Messages.msg("menu.main.poker.lore-players"));
        put(inventory, holder, 17, Material.HEART_OF_THE_SEA, Messages.msg("menu.main.blackjack.name"),
                "casino create blackjack",
                Messages.msg("menu.main.blackjack.lore-players"),
                Messages.msg("menu.main.blackjack.lore-actions"),
                Messages.msg("menu.main.blackjack.lore-currency"));

        put(inventory, holder, 20, Material.GOLD_NUGGET, Messages.msg("menu.main.wager.name"),
                "@bet", Messages.msg("menu.main.wager.lore-current", "bet", plugin.economy().format(bet)),
                Messages.msg("menu.main.wager.lore-range"));
        put(inventory, holder, 22, Material.GOLD_INGOT, Messages.msg("menu.main.blinds.name"),
                "@blinds", Messages.msg("menu.main.blinds.lore-current",
                        "small", blinds[0], "big", blinds[1]));
        put(inventory, holder, 24, Material.EMERALD, Messages.msg("menu.main.buyin.name"),
                "@buyin", Messages.msg("menu.main.buyin.lore-current", "limit", carry));
        put(inventory, holder, 27, Material.OAK_BUTTON,
                Messages.msg(flightPieces == 2 ? "menu.main.pieces.selected" : "menu.main.pieces.unselected",
                        "count", 2), "casino pieces 2");
        put(inventory, holder, 28, Material.SPRUCE_BUTTON,
                Messages.msg(flightPieces == 3 ? "menu.main.pieces.selected" : "menu.main.pieces.unselected",
                        "count", 3), "casino pieces 3");
        put(inventory, holder, 29, Material.BIRCH_BUTTON,
                Messages.msg(flightPieces == 4 ? "menu.main.pieces.selected" : "menu.main.pieces.unselected",
                        "count", 4), "casino pieces 4");
        // 13 种装修放不进主菜单，这里给一个循环切换按钮，直接选可用 /casino casino <1～13>。
        PokerArenaStyle nextStyle = PokerArenaStyle.byId(
                pokerArenaStyle.id() % PokerArenaStyle.count() + 1);
        put(inventory, holder, 37, styleIcon(pokerArenaStyle),
                Messages.msg("menu.main.decor.name",
                        "id", pokerArenaStyle.id(), "name", pokerArenaStyle.display()),
                "casino casino " + nextStyle.id(),
                Messages.msg("menu.main.decor.lore-description",
                        "description", pokerArenaStyle.description()),
                Messages.msg("menu.main.decor.lore-next",
                        "id", nextStyle.id(), "name", nextStyle.display()),
                Messages.msg("menu.main.decor.lore-count", "count", PokerArenaStyle.count()));
        put(inventory, holder, 38, Material.ITEM_FRAME, Messages.msg("menu.main.decor-list.name"),
                "casino casino", Messages.msg("menu.main.decor-list.lore", "count", PokerArenaStyle.count()));

        int openRooms = plugin.lobbies().openLobbies().size() + plugin.activeRooms().size();
        put(inventory, holder, 40, Material.COMPASS, Messages.msg("menu.main.rooms.name"),
                "@rooms", Messages.msg("menu.main.rooms.lore-what"),
                Messages.msg("menu.main.rooms.lore-count", "count", openRooms));

        GameLobby lobby = plugin.lobbies().get(player.getUniqueId());
        if (lobby != null) {
            String funding = lobby.type().usesRealCurrency()
                    ? Messages.msg(lobby.type() == GameType.POKER
                            ? "menu.rules.main.blinds" : "menu.rules.main.ante",
                            "small", lobby.smallBlind(), "big", lobby.bigBlind(),
                            "limit", lobby.buyIn(), "decor", lobby.pokerArenaStyle().display())
                    : lobby.type() == GameType.FLIGHT
                            ? Messages.msg("menu.rules.main.wager-pieces",
                                    "bet", plugin.economy().format(lobby.bet()),
                                    "pieces", lobby.flightPieces())
                            : Messages.msg("menu.rules.main.wager",
                                    "bet", plugin.economy().format(lobby.bet()));
            put(inventory, holder, 30, Material.PAPER,
                    Messages.msg("menu.main.room.name", "game", lobby.type().display()),
                    null, Messages.msg("menu.main.room.lore-players",
                            "current", lobby.members().size(), "max", lobby.maximum()),
                    Messages.msg("menu.main.room.lore-rules", "rules", funding));
            if (lobby.host().equals(player.getUniqueId())) {
                put(inventory, holder, 32, Material.NETHER_STAR, Messages.msg("menu.main.start.name"),
                        "casino start", lobby.type().usesRealCurrency()
                                ? Messages.msg("menu.main.start.lore-bots")
                                : Messages.msg("menu.main.start.lore-minimum"));
                put(inventory, holder, 34, Material.PLAYER_HEAD, Messages.msg("menu.main.invite.name"),
                        "casino invitepanel", Messages.msg("menu.main.invite.lore"));
            }
            put(inventory, holder, 36, Material.BARRIER, Messages.msg("menu.main.leave.name"),
                    "casino leave", Messages.msg("menu.main.leave.lore"));
        }

        put(inventory, holder, 45, Material.WRITTEN_BOOK, Messages.msg("menu.main.history.poker"), "@history");
        put(inventory, holder, 46, Material.BOOK, Messages.msg("menu.main.history.other"), "@bjhistory");
        put(inventory, holder, 49, Material.WRITABLE_BOOK, Messages.msg("menu.common.to-chat.name"), "@chat",
                Messages.msg("menu.common.to-chat.lore"));
        put(inventory, holder, 53, Material.BARRIER, Messages.msg("menu.common.close"), "@close");
        player.openInventory(inventory);
    }

    private void openGuiRooms(Player player) {
        GameMenuHolder holder = new GameMenuHolder(player.getUniqueId(), GameMenuHolder.Page.OPEN_ROOMS);
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.parse(Messages.msg("menu.rooms.title")));
        holder.inventory(inventory);
        int slot = 0;
        for (GameLobby lobby : plugin.lobbies().openLobbies().stream()
                .sorted(Comparator.comparing(this::hostName, String.CASE_INSENSITIVE_ORDER)).toList()) {
            if (slot >= 45) break;
            String host = hostName(lobby);
            String funding = lobby.type().usesRealCurrency()
                    ? Messages.msg(lobby.type() == GameType.POKER
                            ? "menu.rules.rooms.blinds" : "menu.rules.rooms.ante",
                            "small", lobby.smallBlind(), "big", lobby.bigBlind(), "limit", lobby.buyIn())
                    : lobby.type() == GameType.FLIGHT
                            ? Messages.msg("menu.rules.rooms.wager-pieces",
                                    "bet", plugin.economy().format(lobby.bet()),
                                    "pieces", lobby.flightPieces())
                            : Messages.msg("menu.rules.rooms.wager",
                                    "bet", plugin.economy().format(lobby.bet()));
            Material icon = switch (lobby.type()) {
                case FLIGHT -> Material.FIREWORK_ROCKET;
                case POKER -> Material.DIAMOND;
                case BLACKJACK -> Material.HEART_OF_THE_SEA;
            };
            put(inventory, holder, slot++, icon, Messages.msg("menu.rooms.lobby.name", "host", host),
                    "casino join " + lobby.host(),
                    Messages.msg("menu.rooms.entry.game", "game", lobby.type().display()),
                    Messages.msg("menu.rooms.entry.players",
                            "current", lobby.members().size(), "max", lobby.maximum()),
                    Messages.msg("menu.rooms.entry.rules", "rules", funding));
        }
        for (ActiveRoom room : plugin.activeRooms().stream()
                .sorted(Comparator.comparing(ActiveRoom::hostName, String.CASE_INSENSITIVE_ORDER)).toList()) {
            if (slot >= 45) break;
            Material icon = switch (room.type()) {
                case FLIGHT -> Material.SPYGLASS;
                case POKER -> Material.EMERALD;
                case BLACKJACK -> Material.NAUTILUS_SHELL;
            };
            String title = Messages.msg(room.playableJoin()
                    ? "menu.rooms.active.name-join" : "menu.rooms.active.name-spectate",
                    "host", room.hostName());
            put(inventory, holder, slot++, icon, title,
                    "casino join " + room.host(),
                    Messages.msg("menu.rooms.entry.game", "game", room.type().display()),
                    Messages.msg("menu.rooms.entry.seated", "count", room.players()),
                    Messages.msg("menu.rooms.entry.status", "status", room.detail()));
        }
        if (slot == 0) put(inventory, holder, 22, Material.GRAY_DYE, Messages.msg("menu.rooms.empty"), null);
        put(inventory, holder, 45, Material.COMPASS, Messages.msg("menu.rooms.refresh"), "@rooms");
        put(inventory, holder, 50, Material.ARROW, Messages.msg("menu.common.back"), "@main");
        put(inventory, holder, 52, Material.WRITABLE_BOOK, Messages.msg("menu.common.to-chat.name"), "@chat");
        put(inventory, holder, 53, Material.BARRIER, Messages.msg("menu.common.close"), "@close");
        player.openInventory(inventory);
    }

    private void openGuiLobbyInvites(Player player, GameLobby lobby) {
        GameMenuHolder holder = new GameMenuHolder(player.getUniqueId(), GameMenuHolder.Page.LOBBY_INVITES);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Text.parse(Messages.msg("menu.invites.title", "game", lobby.type().display())));
        holder.inventory(inventory);
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList()) {
            if (slot >= 45) break;
            if (target.equals(player) || lobby.members().contains(target.getUniqueId()) || plugin.isBusy(target)) continue;
            if (plugin.invites().hasOutgoing(player.getUniqueId(), target.getUniqueId())) {
                put(inventory, holder, slot++, Material.CLOCK,
                        Messages.msg("menu.invites.pending.name", "player", target.getName()),
                        null, Messages.msg("menu.invites.pending.lore"));
            } else {
                put(inventory, holder, slot++, Material.PLAYER_HEAD,
                        Messages.msg("menu.invites.invite.name", "player", target.getName()),
                        "casino invite " + target.getName(), Messages.msg("menu.invites.invite.lore"));
            }
        }
        if (slot == 0) put(inventory, holder, 22, Material.GRAY_DYE, Messages.msg("menu.invites.empty"), null);
        put(inventory, holder, 45, Material.COMPASS, Messages.msg("menu.invites.refresh"), "casino invitepanel");
        if (lobby.members().size() >= 2) {
            put(inventory, holder, 48, Material.NETHER_STAR, Messages.msg("menu.invites.start"), "casino start");
        }
        put(inventory, holder, 50, Material.ARROW, Messages.msg("menu.common.back"), "@main");
        put(inventory, holder, 52, Material.WRITABLE_BOOK, Messages.msg("menu.common.to-chat.name"), "@chat");
        put(inventory, holder, 53, Material.BARRIER, Messages.msg("menu.common.close"), "@close");
        player.openInventory(inventory);
    }

    private void openChatMain(Player player) {
        double bet = selectedBet(player);
        String formattedBet = plugin.economy().format(bet);
        int[] blinds = plugin.lobbies().blinds(player.getUniqueId());
        int carry = plugin.lobbies().buyIn(player.getUniqueId());
        int flightPieces = plugin.lobbies().flightPieces(player.getUniqueId());
        PokerArenaStyle pokerArenaStyle = plugin.lobbies().pokerArenaStyle(player.getUniqueId());

        Text.send(player, Messages.msg("menu.chat.header", "brand", plugin.brand()));
        runButton(player, Messages.msg("menu.chat.to-gui.label"), "/casino gui",
                Messages.msg("menu.chat.to-gui.hover"));
        button(player, Messages.msg("menu.chat.flight.label"),
                "/casino create flight " + plainAmount(bet),
                Messages.msg("menu.chat.flight.hover", "bet", formattedBet));
        button(player, Messages.msg("menu.chat.poker.label"),
                "/casino create poker", Messages.msg("menu.chat.poker.hover"));
        button(player, Messages.msg("menu.chat.blackjack.label"),
                "/casino create blackjack", Messages.msg("menu.chat.blackjack.hover"));
        button(player, Messages.msg("menu.chat.decor.label", "name", pokerArenaStyle.display()),
                "/casino casino", Messages.msg("menu.chat.decor.hover",
                        "id", pokerArenaStyle.id(), "count", PokerArenaStyle.count()));

        runButton(player, Messages.msg("menu.chat.wager.label", "bet", formattedBet),
                "/casino wager", Messages.msg("menu.chat.wager.hover"));
        button(player, Messages.msg("menu.chat.blinds.label", "small", blinds[0], "big", blinds[1]),
                "/casino blinds " + blinds[0] + " " + blinds[1], Messages.msg("menu.chat.blinds.hover"));
        button(player, Messages.msg("menu.chat.buyin.label", "limit", carry),
                "/casino buyin " + carry, Messages.msg("menu.chat.buyin.hover"));
        runButton(player, Messages.msg(pokerArenaStyle == PokerArenaStyle.CLASSIC
                        ? "menu.chat.decor-1.selected" : "menu.chat.decor-1.unselected"),
                "/casino casino 1", Messages.msg("menu.chat.decor-1.hover"));
        runButton(player, Messages.msg(pokerArenaStyle == PokerArenaStyle.LUMINOUS
                        ? "menu.chat.decor-2.selected" : "menu.chat.decor-2.unselected"),
                "/casino casino 2", Messages.msg("menu.chat.decor-2.hover"));
        runButton(player, Messages.msg(pokerArenaStyle == PokerArenaStyle.NATURE
                        ? "menu.chat.decor-3.selected" : "menu.chat.decor-3.unselected"),
                "/casino casino 3", Messages.msg("menu.chat.decor-3.hover"));
        runButton(player, Messages.msg("menu.chat.pieces.label", "count", 2), "/casino pieces 2",
                flightPieces == 2 ? Messages.msg("menu.chat.pieces.hover-current")
                        : Messages.msg("menu.chat.pieces.hover-set", "count", 2));
        runButton(player, Messages.msg("menu.chat.pieces.label", "count", 3), "/casino pieces 3",
                flightPieces == 3 ? Messages.msg("menu.chat.pieces.hover-current")
                        : Messages.msg("menu.chat.pieces.hover-set", "count", 3));
        runButton(player, Messages.msg("menu.chat.pieces.label", "count", 4), "/casino pieces 4",
                flightPieces == 4 ? Messages.msg("menu.chat.pieces.hover-current")
                        : Messages.msg("menu.chat.pieces.hover-set", "count", 4));
        runButton(player, Messages.msg("menu.chat.rooms.label", "count",
                        plugin.lobbies().openLobbies().size() + plugin.activeRooms().size()),
                "/casino rooms", Messages.msg("menu.chat.rooms.hover"));

        GameLobby lobby = plugin.lobbies().get(player.getUniqueId());
        if (lobby != null) {
            String funding = lobby.type().usesRealCurrency()
                    ? Messages.msg(lobby.type() == GameType.POKER
                            ? "menu.rules.chat-main.blinds" : "menu.rules.chat-main.ante",
                            "small", lobby.smallBlind(), "big", lobby.bigBlind(),
                            "limit", lobby.buyIn(), "decor", lobby.pokerArenaStyle().display())
                    : lobby.type() == GameType.FLIGHT
                            ? Messages.msg("menu.rules.chat-main.wager-pieces",
                                    "bet", plugin.economy().format(lobby.bet()),
                                    "pieces", lobby.flightPieces())
                            : Messages.msg("menu.rules.chat-main.wager",
                                    "bet", plugin.economy().format(lobby.bet()));
            Text.send(player, Messages.msg("menu.chat.room", "game", lobby.type().display(),
                    "current", lobby.members().size(), "max", lobby.maximum(), "rules", funding));
            if (lobby.host().equals(player.getUniqueId())) {
                button(player, Messages.msg("menu.chat.start.label"), "/casino start",
                        lobby.type().usesRealCurrency() ? Messages.msg("menu.chat.start.hover-bots")
                                : Messages.msg("menu.chat.start.hover-minimum"));
                runButton(player, Messages.msg("menu.chat.invitepanel.label"), "/casino invitepanel",
                        Messages.msg("menu.chat.invitepanel.hover"));
            }
            button(player, Messages.msg("menu.chat.leave.label"), "/casino leave",
                    Messages.msg("menu.chat.leave.hover"));
        }
        Text.send(player, Messages.msg("menu.chat.footer"));
    }

    private void openChatRooms(Player player) {
        Text.send(player, Messages.msg("menu.chat-rooms.header"));
        int available = 0;
        for (GameLobby lobby : plugin.lobbies().openLobbies().stream()
                .sorted(Comparator.comparing(this::hostName, String.CASE_INSENSITIVE_ORDER)).toList()) {
            available++;
            String host = hostName(lobby);
            String rule = lobby.type().usesRealCurrency()
                    ? Messages.msg(lobby.type() == GameType.POKER
                            ? "menu.rules.chat-rooms.blinds" : "menu.rules.chat-rooms.ante",
                            "small", lobby.smallBlind(), "big", lobby.bigBlind(),
                            "limit", lobby.buyIn(), "decor", lobby.pokerArenaStyle().display())
                    : Messages.msg("menu.rules.chat-rooms.wager",
                            "bet", plugin.economy().format(lobby.bet()));
            runButton(player, Messages.msg("menu.chat-rooms.lobby.label", "host", host,
                            "game", lobby.type().display(), "current", lobby.members().size(),
                            "max", lobby.maximum(), "rules", rule),
                    "/casino join " + lobby.host(), Messages.msg("menu.chat-rooms.lobby.hover"));
        }
        for (ActiveRoom room : plugin.activeRooms().stream()
                .sorted(Comparator.comparing(ActiveRoom::hostName, String.CASE_INSENSITIVE_ORDER)).toList()) {
            available++;
            runButton(player, Messages.msg(room.playableJoin()
                            ? "menu.chat-rooms.active-join.label" : "menu.chat-rooms.active-spectate.label",
                            "host", room.hostName(), "game", room.type().display(), "status", room.detail()),
                    "/casino join " + room.host(), Messages.msg(room.playableJoin()
                            ? "menu.chat-rooms.active-join.hover" : "menu.chat-rooms.active-spectate.hover"));
        }
        if (available == 0) Text.send(player, Messages.msg("menu.chat-rooms.empty"));
        runButton(player, Messages.msg("menu.chat-rooms.back.label"), "/casino open",
                Messages.msg("menu.chat-rooms.back.hover"));
    }

    private String hostName(GameLobby lobby) {
        String name = Bukkit.getOfflinePlayer(lobby.host()).getName();
        return name == null ? lobby.host().toString().substring(0, 8) : name;
    }

    private void openChatInvitePanel(Player player, GameLobby lobby) {
        String members = lobby.members().stream().map(id -> {
            String name = Bukkit.getOfflinePlayer(id).getName();
            return name == null ? id.toString() : name;
        }).collect(Collectors.joining(Messages.msg("menu.chat-invites.separator")));
        Text.send(player, Messages.msg("menu.chat-invites.header", "game", lobby.type().display()));
        Text.send(player, Messages.msg("menu.chat-invites.room", "members", members,
                "current", lobby.members().size(), "max", lobby.maximum()));

        int available = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player) || lobby.members().contains(target.getUniqueId()) || plugin.isBusy(target)) continue;
            if (plugin.invites().hasOutgoing(player.getUniqueId(), target.getUniqueId())) {
                Text.send(player, Messages.msg("menu.chat-invites.pending", "player", target.getName()));
                continue;
            }
            available++;
            runButton(player, Messages.msg("menu.chat-invites.invite.label", "player", target.getName()),
                    "/casino invite " + target.getName(), Messages.msg("menu.chat-invites.invite.hover"));
        }
        if (available == 0) Text.send(player, Messages.msg("menu.chat-invites.empty"));
        if (lobby.members().size() >= 2) {
            runButton(player, Messages.msg("menu.chat-invites.start.label"), "/casino start",
                    Messages.msg("menu.chat-invites.start.hover"));
        }
        runButton(player, Messages.msg("menu.chat-invites.back.label"), "/casino open",
                Messages.msg("menu.chat-invites.back.hover"));
        Text.send(player, Messages.msg("menu.chat-invites.footer"));
    }

    public void requestBet(Player player) { requestInput(player, InputMode.BET); }
    public void requestBlinds(Player player) { requestInput(player, InputMode.BLINDS); }
    public void requestBuyIn(Player player) { requestInput(player, InputMode.BUY_IN); }

    public boolean handleChat(Player player, String input) {
        InputMode mode = pendingInput.remove(player.getUniqueId());
        if (mode == null) return false;
        Bukkit.getScheduler().runTask(plugin, () -> acceptInput(player, mode, input));
        return true;
    }

    public double selectedBet(Player player) {
        return selectedBets.getOrDefault(player.getUniqueId(),
                plugin.getConfig().getDouble("economy.default-bet", 15.0));
    }

    private void acceptInput(Player player, InputMode mode, String input) {
        // 放弃用的关键词要跟提示语一起走语言文件，否则翻译者改了提示、玩家照着打就卡在输入循环里。
        // "cancel" 永远保底接受，免得某份语言文件把这个词删了就没法退出。
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase(Messages.msg("menu.input.cancel-word"))) {
            Text.send(player, Messages.msg("menu.input.cancelled"));
            openMain(player);
            return;
        }
        if (mode == InputMode.BET) {
            try {
                double value = Double.parseDouble(input.trim());
                if (!plugin.lobbies().setBet(player, value)) {
                    requestInput(player, InputMode.BET);
                    return;
                }
                selectedBets.put(player.getUniqueId(), value);
                Text.send(player, Messages.msg("menu.input.bet-set",
                        "bet", plugin.economy().format(value)));
                openMain(player);
            } catch (NumberFormatException exception) {
                Text.send(player, Messages.msg("menu.input.bet-invalid"));
                requestInput(player, InputMode.BET);
            }
            return;
        }
        if (mode == InputMode.BUY_IN) {
            try {
                if (plugin.lobbies().setBuyIn(player, Integer.parseInt(input.trim()))) openMain(player);
                else requestInput(player, InputMode.BUY_IN);
            } catch (NumberFormatException exception) {
                Text.send(player, Messages.msg("menu.input.buyin-invalid"));
                requestInput(player, InputMode.BUY_IN);
            }
            return;
        }
        String[] parts = input.trim().split("[\\s,，]+");
        try {
            if (parts.length < 1 || parts.length > 2) throw new NumberFormatException();
            int small = Integer.parseInt(parts[0]);
            int big = parts.length == 2 ? Integer.parseInt(parts[1]) : Math.multiplyExact(small, 2);
            if (plugin.lobbies().setBlinds(player, small, big)) openMain(player);
            else requestInput(player, InputMode.BLINDS);
        } catch (NumberFormatException | ArithmeticException exception) {
            Text.send(player, Messages.msg("menu.input.blinds-invalid"));
            requestInput(player, InputMode.BLINDS);
        }
    }

    private void requestInput(Player player, InputMode mode) {
        pendingInput.put(player.getUniqueId(), mode);
        if (mode == InputMode.BET) {
            Text.send(player, Messages.msg("menu.input.bet-prompt"));
        } else if (mode == InputMode.BLINDS) {
            Text.send(player, Messages.msg("menu.input.blinds-prompt"));
        } else {
            Text.send(player, Messages.msg("menu.input.buyin-prompt"));
        }
    }

    private void put(Inventory inventory, GameMenuHolder holder, int slot, Material material,
                     String name, String action, String... lore) {
        ItemStack item = Items.item(material, name, lore);
        inventory.setItem(slot, item);
        if (action != null) holder.action(slot, action);
    }

    private MenuMode mode(Player player) {
        return menuModes.getOrDefault(player.getUniqueId(), MenuMode.GUI);
    }

    private void button(Player player, String label, String command, String hover) {
        Component component = Text.prefixed(label)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Text.parse("<gray>" + hover + "</gray>")));
        player.sendMessage(component);
    }

    private void runButton(Player player, String label, String command, String hover) {
        Component component = Text.prefixed(label)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Text.parse("<gray>" + hover + "</gray>")));
        player.sendMessage(component);
    }


    /** 主菜单里代表当前装修的图标，尽量贴近该风格的主材。 */
    private Material styleIcon(PokerArenaStyle style) {
        return switch (style) {
            case CLASSIC -> Material.GREEN_CARPET;
            case LUMINOUS -> Material.SEA_LANTERN;
            case NATURE -> Material.OAK_SAPLING;
            case MIDNIGHT -> Material.POLISHED_BLACKSTONE;
            case CRIMSON -> Material.RED_WOOL;
            case AZURE -> Material.LAPIS_BLOCK;
            case AMETHYST -> Material.AMETHYST_BLOCK;
            case SAKURA -> Material.CHERRY_SAPLING;
            case DESERT -> Material.SMOOTH_SANDSTONE;
            case NETHER -> Material.NETHER_BRICKS;
            case GLACIER -> Material.BLUE_ICE;
            case COPPER -> Material.COPPER_BLOCK;
            case ENDER -> Material.END_STONE_BRICKS;
        };
    }

    private String plainAmount(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
