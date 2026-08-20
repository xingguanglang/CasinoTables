package io.github.casinotables.command;

import io.github.casinotables.CasinoTablesPlugin;
import io.github.casinotables.GameType;
import io.github.casinotables.Messages;
import io.github.casinotables.Text;
import io.github.casinotables.lobby.GameLobby;
import io.github.casinotables.arena.ArenaShape;
import io.github.casinotables.poker.PokerArenaStyle;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GameCommand implements CommandExecutor, TabCompleter {
    private final CasinoTablesPlugin plugin;

    public GameCommand(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("config")) {
            config(sender, args);
            return true;
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("luck") || args[0].equalsIgnoreCase("badluck"))) {
            luck(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadConfig();
                plugin.messages().reload();
                Text.send(sender, Messages.msg("config.reloaded"));
            } else Text.send(sender, Messages.msg("error.players-only"));
            return true;
        }
        if (!player.hasPermission("casinotables.use")) {
            Text.send(player, Messages.msg("error.no-permission"));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("open")) {
            plugin.openFor(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui" -> {
                plugin.menus().setGuiMode(player, true);
                plugin.openFor(player);
            }
            case "chat" -> {
                plugin.menus().setGuiMode(player, false);
                plugin.openFor(player);
            }
            case "invite", "挑战", "邀请" -> invite(player, args);
            case "create", "房间", "创建" -> create(player, args);
            case "invitepanel", "players", "邀请面板" -> plugin.menus().openInvitePanel(player);
            case "rooms", "房间列表" -> plugin.menus().openRooms(player);
            case "join", "加入" -> join(player, args);
            case "start", "开始" -> plugin.lobbies().start(player);
            case "leave", "离开", "解散" -> plugin.leave(player);
            case "wager", "bet", "赌注" -> plugin.menus().requestBet(player);
            case "pieces", "piece", "棋子" -> pieces(player, args);
            case "blinds", "blind", "盲注" -> blinds(player, args);
            case "buyin", "cap", "携带", "上限" -> buyIn(player, args);
            case "casino", "style", "赌场", "场地" -> casino(player, args);
            case "shape", "轮廓", "形状" -> shape(player, args);
            case "history", "历史", "记录" -> plugin.poker().showHistory(player);
            case "bjhistory", "21历史", "21点历史" -> plugin.blackjack().showHistory(player);
            case "split", "chips", "分解", "筹码" -> plugin.poker().requestSplit(player);
            case "merge", "合成", "合并" -> plugin.poker().requestMerge(player);
            case "accept", "接受" -> plugin.invites().accept(player, args.length >= 2 ? args[1] : null);
            case "deny", "拒绝" -> plugin.invites().deny(player, args.length >= 2 ? args[1] : null);
            case "cancel", "取消" -> plugin.invites().cancel(player);
            case "forfeit", "认输" -> plugin.forfeit(player);
            case "draw", "平局" -> plugin.requestDraw(player);
            case "peek", "seehand", "seecards", "透视牌" -> handPeek(player, args);
            case "floppeek", "seeboard", "seeflop", "透视公牌" -> boardPeek(player, args);
            case "reload" -> {
                if (!player.hasPermission("casinotables.admin")) Text.send(player, Messages.msg("error.no-permission"));
                else {
                    plugin.reloadConfig();
                plugin.messages().reload();
                    Text.send(player, Messages.msg("config.reloaded-ingame"));
                }
            }
            case "help", "帮助" -> help(player);
            default -> {
                GameType type = GameType.parse(args[0]);
                if (type != null && args.length >= 2) inviteShort(player, type, args);
                else help(player);
            }
        }
        return true;
    }

    private void config(CommandSender sender, String[] args) {
        if (!sender.hasPermission("casinotables.admin")) {
            Text.send(sender, Messages.msg("error.no-permission"));
            return;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("get") && isFlightTimeKey(args[2])) {
            int seconds = plugin.getConfig().getInt("flight-chess.turn-timeout-seconds", 60);
            Text.send(sender, Messages.msg("config.flight-time.current", "seconds", seconds));
            return;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("get") && isPokerTimeKey(args[2])) {
            int seconds = plugin.getConfig().getInt("poker.action-timeout-seconds", 60);
            Text.send(sender, Messages.msg("config.poker-time.current", "seconds", seconds));
            return;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("get") && isPokerRakeKey(args[2])) {
            double percent = plugin.getConfig().getDouble("poker.rake-rate", 0.005) * 100.0;
            Text.send(sender, Messages.msg("config.poker-rake.current", "percent", formatDecimal(percent)));
            return;
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("set") && isFlightTimeKey(args[2])) {
            setPlayerTime(sender, args[3], "flight-chess.turn-timeout-seconds",
                    Messages.msg("config.flight-time.label"));
            return;
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("set") && isPokerTimeKey(args[2])) {
            setPlayerTime(sender, args[3], "poker.action-timeout-seconds",
                    Messages.msg("config.poker-time.label"));
            return;
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("set") && isPokerRakeKey(args[2])) {
            setPokerRake(sender, args[3]);
            return;
        }
        for (String line : Messages.msgList("config.usage")) Text.send(sender, line);
    }

    private void luck(CommandSender sender, String[] args) {
        boolean badLuck = args[0].equalsIgnoreCase("badluck");
        String commandName = badLuck ? "badluck" : "luck";
        String effectName = Messages.msg(badLuck ? "luck.effect.bad" : "luck.effect.good");
        if (!sender.hasPermission("casinotables.admin")) {
            Text.send(sender, Messages.msg("error.no-permission"));
            return;
        }
        if (args.length < 3 || args.length > 4) {
            Text.send(sender, Messages.msg("luck.usage", "command", commandName));
            return;
        }
        GameType type = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "poker", "texas", "德州" -> GameType.POKER;
            default -> null;
        };
        if (type == null) {
            Text.send(sender, Messages.msg("luck.unknown-game"));
            return;
        }
        Player online = Bukkit.getPlayerExact(args[2]);
        org.bukkit.OfflinePlayer target = online == null ? Bukkit.getOfflinePlayer(args[2]) : online;
        if (online == null && !target.hasPlayedBefore()) {
            Text.send(sender, Messages.msg("error.unknown-player", "player", args[2]));
            return;
        }
        if (args.length == 3) {
            int current = plugin.luck().boost(type, target.getUniqueId());
            int shown = badLuck ? Math.max(0, -current) : Math.max(0, current);
            String opposite = current == 0 || (badLuck ? current < 0 : current > 0) ? ""
                    : Messages.msg("luck.opposite-active", "percent", Math.abs(current));
            Text.send(sender, Messages.msg("luck.query",
                    "player", target.getName(), "game", type.display(),
                    "effect", effectName, "percent", shown) + opposite);
            return;
        }
        int percent;
        if (args[3].equalsIgnoreCase("off") || args[3].equals("0")) percent = 0;
        else {
            try {
                percent = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                Text.send(sender, Messages.msg("luck.not-a-number", "effect", effectName));
                return;
            }
            if (percent < 1 || percent > 100) {
                Text.send(sender, Messages.msg("luck.out-of-range", "effect", effectName));
                return;
            }
        }
        String targetName = target.getName() == null ? args[2] : target.getName();
        int signedPercent = badLuck ? -percent : percent;
        plugin.luck().set(type, target.getUniqueId(), targetName, signedPercent);
        Text.send(sender, percent == 0
                ? Messages.msg("luck.cleared", "player", targetName, "game", type.display())
                : Messages.msg("luck.applied", "player", targetName, "game", type.display(),
                        "effect", effectName, "percent", percent,
                        "pick", Messages.msg(badLuck ? "luck.pick.worst" : "luck.pick.best")));
        plugin.getLogger().info(sender.getName() + " set " + type.name() + " luck weight of "
                + targetName + " to " + signedPercent + "%");
    }

    private void handPeek(Player player, String[] args) {
        peekCommand(player, args, false);
    }

    private void boardPeek(Player player, String[] args) {
        peekCommand(player, args, true);
    }

    /**
     * /casino peek|floppeek [玩家] [on|off]
     *
     * <p>不带玩家名就是给自己开关；带玩家名则把权限开给别人（只有管理员能执行）。
     * 目标必须在线：开关状态只存在内存里，玩家退出时会被清掉。
     */
    private void peekCommand(Player sender, String[] args, boolean board) {
        if (!sender.hasPermission("casinotables.admin")) {
            Text.send(sender, Messages.msg("error.no-permission"));
            return;
        }
        String label = board ? "floppeek" : "peek";
        String what = Messages.msg(board ? "peek.name.board" : "peek.name.hand");

        Player target = sender;
        String[] toggleArgs = args;
        if (args.length >= 2 && parseToggle(new String[]{args[0], args[1]}, false) == null) {
            // 第二个参数不是 on/off，那就当成玩家名。
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                Text.send(sender, Messages.msg("peek.offline-target",
                        "player", args[1], "what", what));
                return;
            }
            // 把 [cmd, 玩家, on/off] 压成 [cmd, on/off] 交给统一的解析。
            toggleArgs = args.length >= 3 ? new String[]{args[0], args[2]} : new String[]{args[0]};
        }

        boolean current = board ? plugin.boardPeekEnabled(target) : plugin.handPeekEnabled(target);
        Boolean enabled = parseToggle(toggleArgs, !current);
        if (enabled == null) {
            Text.send(sender, Messages.msg("peek.usage", "command", label));
            return;
        }
        if (board) plugin.setBoardPeek(target, enabled);
        else plugin.setHandPeek(target, enabled);

        String detail = Messages.msg(board ? "peek.detail.board" : "peek.detail.hand");
        if (target == sender) {
            Text.send(sender, enabled
                    ? Messages.msg("peek.self.enabled", "what", what, "detail", detail)
                    : Messages.msg("peek.self.disabled", "what", what));
            return;
        }
        Text.send(sender, enabled
                ? Messages.msg("peek.granted", "player", target.getName(), "what", what)
                : Messages.msg("peek.revoked", "player", target.getName(), "what", what));
        Text.send(target, enabled
                ? Messages.msg("peek.target.enabled", "what", what, "detail", detail)
                : Messages.msg("peek.target.disabled", "what", what));
        plugin.getLogger().info(sender.getName() + (enabled ? " granted " : " revoked ")
                + label + " for " + target.getName());
    }

    /** 解析 [on|off] 开关参数；无参数时取反 current，参数非法返回 null。 */
    private Boolean parseToggle(String[] args, boolean current) {
        if (args.length == 1) return current;
        if (args.length != 2) return null;
        String value = args[1].toLowerCase(Locale.ROOT);
        if (List.of("on", "true", "开", "开启").contains(value)) return true;
        if (List.of("off", "false", "关", "关闭").contains(value)) return false;
        return null;
    }

    private void setPlayerTime(CommandSender sender, String rawValue, String path, String label) {
        try {
            int seconds = Integer.parseInt(rawValue);
            if (seconds < 5 || seconds > 600) {
                Text.send(sender, Messages.msg("config.time.out-of-range"));
                return;
            }
            plugin.getConfig().set(path, seconds);
            plugin.saveConfig();
            Text.send(sender, Messages.msg("config.time.applied", "label", label, "seconds", seconds));
        } catch (NumberFormatException exception) {
            Text.send(sender, Messages.msg("config.time.not-a-number"));
        }
    }

    private boolean isLuckCommand(String value) {
        return value.equalsIgnoreCase("luck") || value.equalsIgnoreCase("badluck");
    }

    private boolean isFlightTimeKey(String value) {
        return value.equalsIgnoreCase("flight-time") || value.equalsIgnoreCase("flight-timeout")
                || value.equalsIgnoreCase("flight-chess.turn-timeout-seconds")
                || value.equalsIgnoreCase("飞行棋时间");
    }

    private boolean isPokerTimeKey(String value) {
        return value.equalsIgnoreCase("poker-time") || value.equalsIgnoreCase("poker-timeout")
                || value.equalsIgnoreCase("poker.action-timeout-seconds")
                || value.equalsIgnoreCase("德州时间");
    }

    private boolean isPokerRakeKey(String value) {
        return value.equalsIgnoreCase("poker-rake") || value.equalsIgnoreCase("poker.rake-rate")
                || value.equalsIgnoreCase("赌场抽水") || value.equalsIgnoreCase("德州抽水");
    }

    private void setPokerRake(CommandSender sender, String rawValue) {
        try {
            double percent = Double.parseDouble(rawValue);
            if (!Double.isFinite(percent) || percent < 0.0 || percent > 20.0) {
                Text.send(sender, Messages.msg("config.rake.out-of-range"));
                return;
            }
            plugin.getConfig().set("poker.rake-rate", percent / 100.0);
            plugin.saveConfig();
            Text.send(sender, Messages.msg("config.rake.applied", "percent", formatDecimal(percent)));
        } catch (NumberFormatException exception) {
            Text.send(sender, Messages.msg("config.rake.not-a-number"));
        }
    }

    private String formatDecimal(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private void create(Player player, String[] args) {
        if (args.length < 2) {
            Text.send(player, Messages.msg("create.usage"));
            return;
        }
        GameType type = GameType.parse(args[1]);
        if (type == null) {
            Text.send(player, Messages.msg("error.unknown-game"));
            return;
        }
        double bet = type.usesRealCurrency() ? 0.0 : parseBet(player, args.length >= 3 ? args[2] : null);
        if (!Double.isNaN(bet) && plugin.lobbies().create(player, type, bet) != null) {
            plugin.menus().openInvitePanel(player);
        }
    }

    private void invite(Player sender, String[] args) {
        if (args.length < 2) {
            Text.send(sender, Messages.msg("invite.usage"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.send(sender, Messages.msg("error.player-offline"));
            return;
        }
        if (args.length == 2) {
            GameLobby lobby = plugin.lobbies().get(sender.getUniqueId());
            if (lobby == null || !lobby.host().equals(sender.getUniqueId())) {
                Text.send(sender, Messages.msg("invite.no-room"));
                return;
            }
            if (plugin.invites().send(sender, target, lobby.type(), lobby.bet())) {
                plugin.menus().openInvitePanel(sender);
            }
            return;
        }
        GameType type = GameType.parse(args[2]);
        if (type == null) {
            Text.send(sender, Messages.msg("error.unknown-game"));
            return;
        }
        double bet = type.usesRealCurrency() ? 0.0 : parseBet(sender, args.length >= 4 ? args[3] : null);
        if (!Double.isNaN(bet) && plugin.invites().send(sender, target, type, bet)) {
            plugin.menus().openInvitePanel(sender);
        }
    }

    private void join(Player player, String[] args) {
        if (args.length != 2) {
            Text.send(player, Messages.msg("join.usage"));
            return;
        }
        if (plugin.lobbies().joinOpen(player, args[1]) && !plugin.isActiveGame(player.getUniqueId())) {
            plugin.menus().openMain(player);
        }
    }

    private void inviteShort(Player sender, GameType type, String[] args) {
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.send(sender, Messages.msg("error.player-offline"));
            return;
        }
        double bet = type.usesRealCurrency() ? 0.0 : parseBet(sender, args.length >= 3 ? args[2] : null);
        if (!Double.isNaN(bet) && plugin.invites().send(sender, target, type, bet)) {
            plugin.menus().openInvitePanel(sender);
        }
    }

    private void pieces(Player player, String[] args) {
        if (args.length != 2) {
            Text.send(player, Messages.msg("pieces.usage"));
            return;
        }
        try {
            if (plugin.lobbies().setFlightPieces(player, Integer.parseInt(args[1]))) {
                plugin.menus().openMain(player);
            }
        } catch (NumberFormatException exception) {
            Text.send(player, Messages.msg("pieces.invalid"));
        }
    }

    private void blinds(Player player, String[] args) {
        if (args.length < 2 || args.length > 3) {
            Text.send(player, Messages.msg("blinds.usage"));
            return;
        }
        try {
            int small = Integer.parseInt(args[1]);
            int big = args.length == 3 ? Integer.parseInt(args[2]) : Math.multiplyExact(small, 2);
            plugin.lobbies().setBlinds(player, small, big);
        } catch (NumberFormatException | ArithmeticException exception) {
            Text.send(player, Messages.msg("blinds.not-a-number"));
        }
    }

    private void buyIn(Player player, String[] args) {
        if (args.length != 2) {
            Text.send(player, Messages.msg("buyin.usage"));
            return;
        }
        try {
            plugin.lobbies().setBuyIn(player, Integer.parseInt(args[1]));
        } catch (NumberFormatException exception) {
            Text.send(player, Messages.msg("buyin.not-a-number"));
        }
    }

    private void casino(Player player, String[] args) {
        PokerArenaStyle current = plugin.lobbies().pokerArenaStyle(player.getUniqueId());
        if (args.length != 2) {
            Text.send(player, Messages.msg("decor.list.header", "count", PokerArenaStyle.count()));
            for (PokerArenaStyle option : PokerArenaStyle.values()) {
                Text.send(player, Messages.msg(option == current ? "decor.list.selected" : "decor.list.entry",
                        "id", option.id(), "name", option.display(), "description", option.description()));
            }
            return;
        }
        PokerArenaStyle style = PokerArenaStyle.parse(args[1]);
        if (style == null) {
            Text.send(player, Messages.msg("decor.unknown", "count", PokerArenaStyle.count()));
            return;
        }
        if (plugin.lobbies().setPokerArenaStyle(player, style)) plugin.menus().openMain(player);
    }

    private void shape(Player player, String[] args) {
        ArenaShape current = plugin.lobbies().arenaShape(player.getUniqueId());
        if (args.length != 2) {
            Text.send(player, Messages.msg("shape.list.header", "count", ArenaShape.count()));
            for (ArenaShape option : ArenaShape.values()) {
                Text.send(player, Messages.msg(option == current ? "shape.list.selected" : "shape.list.entry",
                        "id", option.id(), "name", option.display(), "description", option.description()));
            }
            Text.send(player, Messages.msg("shape.list.footer"));
            return;
        }
        ArenaShape shape = ArenaShape.parse(args[1]);
        if (shape == null) {
            Text.send(player, Messages.msg("shape.unknown", "count", ArenaShape.count()));
            return;
        }
        plugin.lobbies().setArenaShape(player, shape);
    }

    private double parseBet(Player player, String value) {
        if (value == null) return plugin.menus().selectedBet(player);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            Text.send(player, Messages.msg("error.bad-amount", "value", value));
            return Double.NaN;
        }
    }

    private void help(Player player) {
        Text.send(player, Messages.msg("help.header", "brand", plugin.brand()));
        for (String line : Messages.msgList("help.lines", "styles", PokerArenaStyle.count())) {
            Text.send(player, line);
        }
        if (!player.hasPermission("casinotables.admin")) return;
        for (String line : Messages.msgList("help.admin-lines",
                "styles", PokerArenaStyle.count(), "shapes", ArenaShape.count())) {
            Text.send(player, line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> values = new ArrayList<>();
        if (args.length == 1) {
            values.addAll(List.of("gui", "chat", "create", "invite", "invitepanel", "rooms", "join", "start", "leave", "wager", "pieces", "blinds", "buyin", "casino", "shape", "history", "bjhistory", "split", "merge",
                    "accept", "deny", "cancel", "forfeit", "draw", "open", "help"));
            if (sender.hasPermission("casinotables.admin")) values.addAll(List.of("config", "luck", "badluck", "peek", "floppeek", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            for (Player player : Bukkit.getOnlinePlayers()) values.add(player.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            for (GameLobby lobby : plugin.lobbies().openLobbies()) {
                String name = Bukkit.getOfflinePlayer(lobby.host()).getName();
                if (name != null) values.add(name);
            }
            plugin.activeRooms().forEach(room -> values.add(room.hostName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            values.addAll(List.of("flight", "poker", "blackjack"));
        } else if (false) {
        } else if (args.length == 2 && args[0].equalsIgnoreCase("pieces")) {
            values.addAll(List.of("2", "3", "4"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("shape")) {
            for (ArenaShape option : ArenaShape.values()) values.add(Integer.toString(option.id()));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("casino")
                || args[0].equalsIgnoreCase("style"))) {
            for (PokerArenaStyle option : PokerArenaStyle.values()) {
                values.add(Integer.toString(option.id()));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("invite")) {
            values.addAll(List.of("flight", "poker", "blackjack"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny"))
                && sender instanceof Player player) {
            plugin.invites().incoming(player.getUniqueId()).forEach(invite -> values.add(invite.senderName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("config") && sender.hasPermission("casinotables.admin")) {
            values.addAll(List.of("get", "set"));
        } else if (args.length == 2 && isLuckCommand(args[0]) && sender.hasPermission("casinotables.admin")) {
            values.addAll(List.of("poker", "zha"));
        } else if (args.length == 3 && isLuckCommand(args[0]) && sender.hasPermission("casinotables.admin")) {
            for (Player player : Bukkit.getOnlinePlayers()) values.add(player.getName());
        } else if (args.length == 4 && isLuckCommand(args[0]) && sender.hasPermission("casinotables.admin")) {
            values.addAll(List.of("10", "20", "30", "50", "100", "off"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("peek") || args[0].equalsIgnoreCase("floppeek"))
                && sender.hasPermission("casinotables.admin")) {
            values.addAll(List.of("on", "off"));
            for (Player online : Bukkit.getOnlinePlayers()) values.add(online.getName());
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("peek") || args[0].equalsIgnoreCase("floppeek"))
                && sender.hasPermission("casinotables.admin")) {
            values.addAll(List.of("on", "off"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("config") && sender.hasPermission("casinotables.admin")) {
            values.addAll(List.of("flight-time", "poker-time", "poker-rake"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("config")
                && args[1].equalsIgnoreCase("set") && sender.hasPermission("casinotables.admin")) {
            if (isFlightTimeKey(args[2])) values.add("60");
            else if (isPokerTimeKey(args[2])) values.add("60");
            else if (isPokerRakeKey(args[2])) values.add("0.5");
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        values.removeIf(value -> !value.toLowerCase(Locale.ROOT).startsWith(prefix));
        return values;
    }
}
