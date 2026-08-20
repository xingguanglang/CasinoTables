package io.github.casinotables.flight;

import io.github.casinotables.CasinoTablesPlugin;
import io.github.casinotables.Messages;
import io.github.casinotables.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class FlightGame {
    private static final int TRACK = FlightRules.OUTER;
    private static final int FINISHED = FlightRules.FINISHED;
    /** 四个颜色的显示名连同颜色标记都写在语言文件里，这里只保留 key。 */
    private static final String[] COLOR_KEYS = {"red", "yellow", "blue", "green"};
    private static final String[] COLOR_TAGS = {"<red>", "<yellow>", "<blue>", "<green>"};

    private final FlightManager manager;
    private final CasinoTablesPlugin plugin;
    private final UUID[] players;
    private final String[] names;
    private final double bet;
    private final FlightArena arena;
    private final int[] colorsBySide;
    private final int[][] pieces;
    private final int pieceCount;
    private final boolean[] active;
    private final boolean[] actedThisRound;
    private final Set<UUID> drawVotes = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final List<UUID> finishOrder = new ArrayList<>();
    private final List<UUID> eliminationOrder = new ArrayList<>();
    private final int turnTimeoutSeconds;
    private final long startedAt = System.currentTimeMillis();
    private int turn;
    private int rolled;
    private int diceFace;
    private int sixStreak;
    private int roundNumber = 1;
    private long turnDeadline;
    private boolean ended;
    private boolean rolling;
    private boolean moving;

    FlightGame(FlightManager manager, CasinoTablesPlugin plugin, List<Player> participants, double bet,
               FlightArena arena, int[] colorsBySide, int pieceCount) {
        this.manager = manager;
        this.plugin = plugin;
        this.players = participants.stream().map(Player::getUniqueId).toArray(UUID[]::new);
        this.names = participants.stream().map(Player::getName).toArray(String[]::new);
        this.bet = bet;
        this.arena = arena;
        this.colorsBySide = colorsBySide.clone();
        this.turnTimeoutSeconds = Math.max(5, plugin.getConfig().getInt("flight-chess.turn-timeout-seconds",
                plugin.getConfig().getInt("game.turn-timeout-seconds", 60)));
        this.pieceCount = Math.max(2, Math.min(4, pieceCount));
        this.pieces = new int[players.length][this.pieceCount];
        this.active = new boolean[players.length];
        this.actedThisRound = new boolean[players.length];
        for (int side = 0; side < players.length; side++) {
            Arrays.fill(pieces[side], -1);
            active[side] = true;
        }
        turn = ThreadLocalRandom.current().nextInt(players.length);
        resetDeadline();
    }

    void start() {
        arena.sync(pieces, active, colorsBySide);
        String amount = plugin.wagers().format(bet);
        String roster = String.join(Messages.msg("flight.roster-separator"), names);
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            for (String line : Messages.msgList("flight.game.start", "players", roster, "wager", amount,
                    "slots", pieceCount + 1, "pieces", pieceCount, "seconds", turnTimeoutSeconds)) {
                Text.send(player, line);
            }
        }
        announceTurn(true);
    }

    void showStatus(Player player) {
        int side = side(player.getUniqueId());
        if (ended || !active(side) || !arena.protects(player.getUniqueId())) return;
        Text.send(player, Messages.msg("flight.status.current-turn",
                "side", colorName(colorsBySide[turn]), "player", names[turn]));
        showStatusBar();
    }

    boolean interactButton(Player player, Block block) {
        int buttonSide = arena.buttonSide(block);
        if (buttonSide < 0) return false;
        int playerSide = side(player.getUniqueId());
        if (ended || !active(playerSide) || !arena.protects(player.getUniqueId())) return true;
        if (buttonSide != playerSide) {
            Text.send(player, Messages.msg("flight.error.wrong-button"));
            return true;
        }
        if (playerSide != turn) {
            Text.send(player, Messages.msg("flight.error.not-your-turn", "player", names[turn]));
            return true;
        }
        roll(player);
        return true;
    }

    boolean interactPiece(Player player, Entity clicked) {
        int[] piece = arena.piece(clicked);
        if (piece == null) return false;
        int playerSide = side(player.getUniqueId());
        if (ended || !active(playerSide) || !arena.protects(player.getUniqueId())) return true;
        if (piece[0] != playerSide) {
            Text.send(player, Messages.msg("flight.error.not-your-piece"));
            return true;
        }
        if (playerSide != turn) {
            Text.send(player, Messages.msg("flight.error.not-your-turn", "player", names[turn]));
            return true;
        }
        move(player, piece[1]);
        return true;
    }

    boolean interactControl(Player player, org.bukkit.Material material) {
        int action = FlightControls.action(material);
        if (action == FlightControls.NONE) return false;
        int playerSide = side(player.getUniqueId());
        if (ended || !active(playerSide) || !arena.protects(player.getUniqueId())) return true;
        if (playerSide != turn) {
            Text.send(player, Messages.msg("flight.error.not-your-turn", "player", names[turn]));
            return true;
        }
        if (action == FlightControls.ROLL) roll(player);
        else move(player, action);
        return true;
    }

    void requestDraw(Player player) {
        int side = side(player.getUniqueId());
        if (ended || !active(side) || !arena.protects(player.getUniqueId())) return;
        if (!drawVotes.add(player.getUniqueId())) {
            Text.send(player, Messages.msg("flight.draw.already-requested"));
            return;
        }
        int required = 0;
        for (int index = 0; index < players.length; index++) {
            if (active[index] && arena.protects(players[index])) required++;
        }
        broadcastRoom(Messages.msg("flight.draw.requested", "player", names[side],
                "votes", drawVotes.size(), "required", required));
        if (required > 0 && drawVotes.size() >= required) {
            ended = true;
            manager.mutualDraw(this, Messages.msg("flight.reason.mutual-draw"));
        }
    }

    private void roll(Player player) {
        if (moving) {
            Text.send(player, Messages.msg("flight.error.pieces-moving"));
            return;
        }
        if (rolling) {
            Text.send(player, Messages.msg("flight.error.dice-rolling"));
            return;
        }
        if (rolled != 0) {
            Text.send(player, Messages.msg("flight.error.already-rolled", "value", rolled));
            return;
        }
        rolling = true;
        animateRoll(player.getUniqueId(), 0);
    }

    private void animateRoll(UUID roller, int step) {
        if (ended || !players[turn].equals(roller) || !active[turn]) {
            rolling = false;
            return;
        }
        diceFace = ThreadLocalRandom.current().nextInt(1, 7);
        Player player = Bukkit.getPlayer(roller);
        showRollToRoom(diceFace, true);
        refresh();
        if (step < 7) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> animateRoll(roller, step + 1), 2L);
            return;
        }
        rolling = false;
        rolled = diceFace;
        showRollToRoom(rolled, false);
        if (player == null) {
            eliminate(roller, Messages.msg("flight.reason.offline-roll"));
            return;
        }
        finishRoll(player);
    }

    private void finishRoll(Player player) {
        if (rolled == 6) {
            sixStreak++;
            if (sixStreak >= 3) {
                Text.send(player, Messages.msg("flight.roll.three-sixes"));
                rolled = 0;
                sixStreak = 0;
                changeTurn();
                return;
            }
        } else sixStreak = 0;
        broadcastRoom(Messages.msg("flight.roll.result", "side", colorName(colorsBySide[turn]),
                "player", names[turn], "value", rolled));
        if (!hasLegalMove(turn, rolled)) {
            Text.send(player, Messages.msg("flight.roll.no-moves"));
            boolean extra = rolled == 6;
            rolled = 0;
            if (extra) {
                resetDeadline();
                refresh();
            } else changeTurn();
        } else {
            resetDeadline();
            refresh();
        }
    }

    private void move(Player player, int piece) {
        if (piece < 0 || piece >= pieceCount) {
            Text.send(player, Messages.msg("flight.error.piece-out-of-range", "pieces", pieceCount));
            return;
        }
        if (moving) {
            Text.send(player, Messages.msg("flight.error.pieces-moving"));
            return;
        }
        if (rolling) {
            Text.send(player, Messages.msg("flight.error.wait-dice"));
            return;
        }
        if (rolled == 0) {
            Text.send(player, Messages.msg("flight.error.roll-first"));
            return;
        }
        int current = pieces[turn][piece];
        if (!legal(current, rolled)) {
            Text.send(player, Messages.msg(current < 0 ? "flight.error.need-six"
                    : "flight.error.piece-finished"));
            return;
        }
        boolean launched = current < 0;
        boolean bounced = current >= FlightRules.OUTER && current + rolled > FINISHED;
        int normalResult = FlightRules.normalDestination(current, rolled);
        int result = normalResult;
        if (launched) {
            broadcastRoom(Messages.msg("flight.move.launch", "side", colorName(colorsBySide[turn]),
                    "player", names[turn]));
        }
        List<Integer> path = FlightRules.movementPath(current, rolled, colorsBySide[turn]);
        if (path.isEmpty()) {
            Text.send(player, Messages.msg("flight.error.no-path"));
            return;
        }
        // movementPath 与 normalDestination 使用同一套终点折返规则。以实际动画路径末端为准，
        // 避免已有棋子停在终点时被无关的一致性校验误判为“无法生成路径”。
        normalResult = path.getLast();
        if (!launched) result = applyShortcut(turn, normalResult);
        if (bounced) {
            int excess = current + rolled - FINISHED;
            broadcastRoom(Messages.msg("flight.move.bounce", "side", colorName(colorsBySide[turn]),
                    "player", names[turn], "excess", excess,
                    "cell", normalResult - FlightRules.OUTER + 1));
        }
        int movingSide = turn;
        int destination = result;
        moving = true;
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> animateMove(player.getUniqueId(), movingSide, piece, path, 0, destination), 1L);
    }

    private void animateMove(UUID playerId, int movingSide, int piece, List<Integer> path,
                             int index, int result) {
        if (ended || !moving || turn != movingSide || !active(movingSide)) {
            moving = false;
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            moving = false;
            eliminate(playerId, Messages.msg("flight.reason.offline-move"));
            return;
        }
        pieces[movingSide][piece] = path.get(index);
        arena.sync(pieces, active, colorsBySide);
        arena.showMoveStep(movingSide, piece);
        showStatusBar();
        if (index + 1 < path.size()) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> animateMove(playerId, movingSide, piece, path, index + 1, result), 4L);
            return;
        }
        int shortcutSource = path.getLast();
        if (shortcutSource != result) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> animateShortcut(playerId, movingSide, piece, shortcutSource, result, 1), 3L);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> finishAnimatedMove(playerId, movingSide, piece, path.getLast()), 4L);
    }

    private void animateShortcut(UUID playerId, int movingSide, int piece, int source, int destination, int step) {
        if (ended || !moving || turn != movingSide || !active(movingSide)) {
            moving = false;
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            moving = false;
            eliminate(playerId, Messages.msg("flight.reason.offline-shortcut"));
            return;
        }
        int totalSteps = 12;
        arena.showShortcutStep(movingSide, piece, source, destination, step, totalSteps);
        showStatusBar();
        if (step < totalSteps) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> animateShortcut(playerId, movingSide, piece, source, destination, step + 1), 2L);
            return;
        }
        pieces[movingSide][piece] = destination;
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> finishAnimatedMove(playerId, movingSide, piece, destination), 3L);
    }

    private void finishAnimatedMove(UUID playerId, int movingSide, int piece, int result) {
        if (ended || !moving || turn != movingSide || !active(movingSide)) {
            moving = false;
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            moving = false;
            eliminate(playerId, Messages.msg("flight.reason.offline-move"));
            return;
        }
        if (result < TRACK) capture(movingSide, result, player);
        arena.sync(pieces, active, colorsBySide);
        boolean extra = rolled == 6;
        rolled = 0;
        moving = false;
        if (Arrays.stream(pieces[movingSide]).allMatch(value -> value == FINISHED)) {
            finishPlayer(movingSide);
            return;
        }
        if (extra) {
            Text.send(player, Messages.msg("flight.roll.extra-turn"));
            resetDeadline();
            refresh();
        } else changeTurn();
    }

    private void capture(int movingSide, int progress, Player player) {
        int global = globalCell(movingSide, progress);
        if (isSafe(global)) return;
        int total = 0;
        for (int opponent = 0; opponent < players.length; opponent++) {
            if (opponent == movingSide || !active[opponent]) continue;
            int count = 0;
            for (int piece = 0; piece < pieces[opponent].length; piece++) {
                int otherProgress = pieces[opponent][piece];
                if (otherProgress >= 0 && otherProgress < TRACK
                        && globalCell(opponent, otherProgress) == global) {
                    pieces[opponent][piece] = -1;
                    count++;
                    total++;
                }
            }
            if (count > 0) {
                Player target = Bukkit.getPlayer(players[opponent]);
                if (target != null) Text.send(target, Messages.msg("flight.capture.victim",
                        "count", count, "player", names[movingSide]));
            }
        }
        if (total > 0) Text.send(player, Messages.msg("flight.capture.actor", "count", total));
    }

    private int applyShortcut(int movingSide, int progress) {
        if (progress < 0 || progress >= TRACK) return progress;
        if (progress == FlightRules.FLIGHT_LANE_START) {
            int destination = FlightRules.shortcutDestination(colorsBySide[movingSide], progress);
            broadcastRoom(Messages.msg("flight.shortcut.lane",
                    "side", colorName(colorsBySide[movingSide]), "player", names[movingSide]));
            return destination;
        }
        int destination = FlightRules.shortcutDestination(colorsBySide[movingSide], progress);
        if (destination != progress) {
            broadcastRoom(Messages.msg("flight.shortcut.same-colour",
                    "side", colorName(colorsBySide[movingSide]), "player", names[movingSide]));
            return destination;
        }
        return progress;
    }

    private void changeTurn() {
        if (active(turn)) actedThisRound[turn] = true;
        int next = nextActive(turn);
        if (next < 0) return;
        boolean newRound = roundComplete();
        if (newRound) {
            roundNumber++;
            Arrays.fill(actedThisRound, false);
        }
        moving = false;
        turn = next;
        rolled = 0;
        diceFace = 0;
        sixStreak = 0;
        resetDeadline();
        refresh();
        announceTurn(newRound);
    }

    private boolean roundComplete() {
        for (int side = 0; side < players.length; side++) {
            if (active[side] && !actedThisRound[side]) return false;
        }
        return true;
    }

    private void announceTurn(boolean newRound) {
        if (newRound) {
            for (UUID id : viewerIds()) {
                Player viewer = Bukkit.getPlayer(id);
                if (viewer == null || !arena.protects(id)) continue;
                viewer.showTitle(net.kyori.adventure.title.Title.title(
                        Text.parse(Messages.msg("flight.round.title", "round", roundNumber)),
                        Text.parse(Messages.msg("flight.round.subtitle",
                                "side", colorName(colorsBySide[turn]), "player", names[turn]))));
                viewer.playSound(viewer.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.15f);
            }
        }
        for (int side = 0; side < players.length; side++) {
            Player player = Bukkit.getPlayer(players[side]);
            if (player == null || !arena.protects(players[side])) continue;
            if (!active[side]) continue;
            if (side == turn) {
                Text.send(player, Messages.msg("flight.turn.yours"));
                player.getWorld().strikeLightningEffect(player.getLocation());
                if (!newRound) {
                    player.showTitle(net.kyori.adventure.title.Title.title(
                            Text.parse(Messages.msg("flight.turn.title",
                                    "color", COLOR_TAGS[colorsBySide[side]])),
                            Text.parse(Messages.msg("flight.turn.subtitle",
                                    "seconds", turnTimeoutSeconds))));
                }
            } else {
                Text.send(player, Messages.msg("flight.turn.waiting", "player", names[turn]));
            }
        }
        for (UUID id : spectators) {
            Player spectator = Bukkit.getPlayer(id);
            if (spectator != null && arena.protects(id)) {
                Text.send(spectator, Messages.msg("flight.spectate.turn",
                        "side", colorName(colorsBySide[turn]), "player", names[turn]));
            }
        }
        showStatusBar();
    }

    private boolean hasLegalMove(int side, int value) {
        for (int progress : pieces[side]) if (legal(progress, value)) return true;
        return false;
    }

    private boolean legal(int progress, int value) {
        if (progress == FINISHED) return false;
        if (progress < 0) return value == 6;
        return value >= 1 && value <= 6;
    }

    private int globalCell(int side, int progress) {
        return FlightRules.trackIndex(colorsBySide[side], progress);
    }

    private int startOffset(int side) {
        return FlightRules.startIndex(colorsBySide[side]);
    }

    private boolean isSafe(int global) {
        for (int side = 0; side < players.length; side++) if (startOffset(side) == global) return true;
        return false;
    }

    private void refresh() {
        showStatusBar();
    }

    private void showStatusBar() {
        if (ended || turn < 0) return;
        int seconds = Math.max(0, (int) Math.ceil((turnDeadline - System.currentTimeMillis()) / 1000.0));
        arena.showStatus(turn, rolling ? diceFace : rolled, rolling, seconds, pieces, active);
        int color = colorsBySide[turn];
        String points = rolling
                ? Messages.msg("flight.actionbar.points.rolling",
                        "symbol", diceSymbol(diceFace), "value", diceFace)
                : rolled == 0 ? Messages.msg("flight.actionbar.points.waiting")
                : Messages.msg(moving ? "flight.actionbar.points.moving" : "flight.actionbar.points.value",
                        "symbol", diceSymbol(rolled), "value", rolled);
        String line = Messages.msg("flight.actionbar.line", "side", colorName(color),
                "player", names[turn], "points", points, "seconds", seconds);
        for (UUID id : viewerIds()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && arena.protects(id)) {
                player.sendActionBar(Text.parse(line));
            }
        }
    }

    private void showRollToRoom(int face, boolean animating) {
        int color = colorsBySide[turn];
        String subtitle = Messages.msg(animating ? "flight.roll.subtitle-rolling"
                        : "flight.roll.subtitle-result",
                "side", colorName(color), "player", names[turn], "value", face);
        for (UUID id : viewerIds()) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer == null || !arena.protects(id)) continue;
            viewer.showTitle(net.kyori.adventure.title.Title.title(
                    Text.parse(Messages.msg("flight.roll.title", "color", COLOR_TAGS[color],
                            "symbol", diceSymbol(face), "value", face)),
                    Text.parse(subtitle)));
            viewer.playSound(viewer.getLocation(), animating ? Sound.BLOCK_NOTE_BLOCK_HAT
                    : Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, animating ? 0.7f + face * 0.08f : 1.35f);
        }
    }

    private void resetDeadline() {
        turnDeadline = System.currentTimeMillis()
                + turnTimeoutSeconds * 1000L;
    }

    void tick() {
        if (ended) return;
        showStatusBar();
        long now = System.currentTimeMillis();
        if (!moving && now >= turnDeadline) {
            eliminate(players[turn], Messages.msg("flight.reason.turn-timeout", "player", names[turn]));
            return;
        }
        long max = plugin.getConfig().getLong("flight-chess.max-duration-seconds", 2700L) * 1000L;
        if (now - startedAt >= max) {
            ended = true;
            manager.draw(this, Messages.msg("flight.reason.time-limit"));
        }
    }

    void eliminate(UUID player, String reason) {
        if (ended) return;
        int side = side(player);
        if (!active(side)) {
            // 已经完成比赛的玩家是留场观战者；掉线或主动离开时只退出观战，不改名次。
            if (side >= 0 && finishOrder.contains(player)) manager.eliminated(this, player);
            return;
        }
        if (turn == side) moving = false;
        active[side] = false;
        eliminationOrder.add(player);
        drawVotes.remove(player);
        Arrays.fill(pieces[side], -1);
        arena.sync(pieces, active, colorsBySide);
        Player eliminated = Bukkit.getPlayer(player);
        if (eliminated != null) Text.send(eliminated, Messages.msg("flight.eliminate.self", "reason", reason));
        manager.eliminated(this, player);
        broadcastRoom(Messages.msg("flight.eliminate.broadcast", "player", names[side], "reason", reason));
        if (activeCount() == 1) {
            settleRankings(reason);
            return;
        }
        if (turn == side) changeTurn();
        else refresh();
    }

    private void finishPlayer(int side) {
        UUID playerId = players[side];
        active[side] = false;
        finishOrder.add(playerId);
        drawVotes.remove(playerId);
        int rank = finishOrder.size();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            Text.send(player, Messages.msg("flight.finish.self", "pieces", pieceCount, "rank", rank));
            arena.spectate(player);
        }
        arena.sync(pieces, active, colorsBySide);
        broadcastRoom(Messages.msg("flight.finish.broadcast", "player", names[side],
                "pieces", pieceCount, "rank", rank));
        manager.placed(this, playerId);
        if (activeCount() <= 1) {
            settleRankings(Messages.msg("flight.reason.all-ranks-decided"));
            return;
        }
        if (turn == side) changeTurn();
        else refresh();
    }

    private void settleRankings(String reason) {
        if (ended) return;
        ended = true;
        List<UUID> standings = new ArrayList<>(finishOrder);
        for (int side = 0; side < players.length; side++) {
            if (active[side] && !standings.contains(players[side])) standings.add(players[side]);
        }
        for (int index = eliminationOrder.size() - 1; index >= 0; index--) {
            UUID id = eliminationOrder.get(index);
            if (!standings.contains(id)) standings.add(id);
        }
        for (UUID id : players) if (!standings.contains(id)) standings.add(id);
        manager.rank(this, standings, reason);
    }

    private void broadcastRoom(String message) {
        for (UUID id : viewerIds()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && arena.protects(id)) Text.send(player, message);
        }
    }

    private List<UUID> viewerIds() {
        List<UUID> viewers = new ArrayList<>(Arrays.asList(players));
        viewers.addAll(spectators);
        return viewers;
    }

    private int activeCount() {
        int count = 0;
        for (boolean value : active) if (value) count++;
        return count;
    }

    private int nextActive(int after) {
        for (int step = 1; step <= players.length; step++) {
            int side = (after + step) % players.length;
            if (active[side]) return side;
        }
        return -1;
    }

    private boolean active(int side) { return side >= 0 && active[side]; }

    /** 颜色名（含颜色标记）从语言文件读取。 */
    private static String colorName(int color) {
        return Messages.msg("flight.color." + COLOR_KEYS[Math.floorMod(color, COLOR_KEYS.length)]);
    }

    private String diceSymbol(int face) {
        return switch (face) {
            case 1 -> "⚀";
            case 2 -> "⚁";
            case 3 -> "⚂";
            case 4 -> "⚃";
            case 5 -> "⚄";
            case 6 -> "⚅";
            default -> "□";
        };
    }

    private int side(UUID player) {
        for (int side = 0; side < players.length; side++) if (players[side].equals(player)) return side;
        return -1;
    }

    List<UUID> players() { return List.of(players); }
    boolean protects(UUID player) { return arena.protects(player); }
    boolean contains(org.bukkit.Location location) { return arena.contains(location); }
    void cancel() { ended = true; rolling = false; moving = false; }
    void release(Player player) { arena.release(player); }
    void addSpectator(Player player) {
        arena.addSpectator(player);
        spectators.add(player.getUniqueId());
        Text.send(player, Messages.msg("flight.spectate.joined-game"));
        showStatusBar();
    }
    void removeSpectator(UUID player) { spectators.remove(player); }
    void closeArena() { arena.close(); }
    static int[] colorsFor(int count) {
        return count == 2 ? new int[]{0, 2} : count == 3 ? new int[]{0, 1, 2} : new int[]{0, 1, 2, 3};
    }
    int playerCount() { return players.length; }
    double bet() { return bet; }
    UUID hostId() { return players[0]; }
    String hostName() { return names[0]; }
}
