package io.github.xingguanglang.casinotables.flight;

import io.github.xingguanglang.casinotables.CasinoTablesPlugin;
import io.github.xingguanglang.casinotables.Items;
import io.github.xingguanglang.casinotables.Messages;
import io.github.xingguanglang.casinotables.Text;
import io.github.xingguanglang.casinotables.arena.ArenaWorld;
import io.github.xingguanglang.casinotables.arena.PlayerSnapshot;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class FlightArena {
    static final String PIECE_TAG = io.github.xingguanglang.casinotables.arena.ArenaTags.FLIGHT_PIECE;
    private static final DyeColor[] DYES = {DyeColor.RED, DyeColor.YELLOW, DyeColor.BLUE, DyeColor.GREEN};
    private static final Material[] COLORS = {
            Material.RED_CONCRETE, Material.YELLOW_CONCRETE,
            Material.BLUE_CONCRETE, Material.GREEN_CONCRETE};
    private static final Material[] START_MARKERS = {
            Material.REDSTONE_BLOCK, Material.GOLD_BLOCK,
            Material.LAPIS_BLOCK, Material.EMERALD_BLOCK};
    private static final Material[] SHORTCUT_PATHS = {
            Material.RED_CARPET, Material.YELLOW_CARPET,
            Material.BLUE_CARPET, Material.GREEN_CARPET};
    private static final int[][] TRACK = createTrack();
    private static final String[] DICE = {"·", "⚀", "⚁", "⚂", "⚃", "⚄", "⚅"};
    /** 四个颜色的显示名连同颜色标记都写在语言文件里，这里只保留 key。 */
    private static final String[] COLOR_KEYS = {"red", "yellow", "blue", "green"};
    private static final String[] COLOR_TAGS = {"<red>", "<yellow>", "<blue>", "<green>"};
    private static final int[][] BUTTONS = {{-12, -15}, {15, -12}, {12, 15}, {-15, 12}};

    private final CasinoTablesPlugin plugin;
    private final ArenaWorld arenaWorld;
    private final World world;
    private final int slot;
    private final int centerX;
    private final int centerZ = 0;
    private final int y;
    private final UUID[] players;
    private final String[] names;
    private final int[] colorsBySide;
    private final int pieceCount;
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final Sheep[][] sheep;
    private final List<Entity> entities = new ArrayList<>();
    private TextDisplay dice;
    private boolean closed;

    FlightArena(CasinoTablesPlugin plugin, ArenaWorld arenaWorld, int slot, List<Player> participants,
                int[] colorsBySide, int pieceCount) {
        this.plugin = plugin;
        this.arenaWorld = arenaWorld;
        this.world = arenaWorld.world();
        this.slot = slot;
        this.centerX = arenaWorld.centerX(slot);
        this.y = arenaWorld.baseY();
        this.players = participants.stream().map(Player::getUniqueId).toArray(UUID[]::new);
        this.names = participants.stream().map(Player::getName).toArray(String[]::new);
        this.colorsBySide = colorsBySide.clone();
        this.pieceCount = Math.max(2, Math.min(4, pieceCount));
        this.sheep = new Sheep[players.length][this.pieceCount];
        try {
            buildBoard();
            spawnPieces(colorsBySide);
            preparePlayers(participants, colorsBySide);
        } catch (RuntimeException | Error throwable) {
            rollback();
            throw throwable;
        }
    }

    private void buildBoard() {
        arenaWorld.clearBox(centerX, centerZ, 17, 17, y, y + 8);
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                set(x, 0, z, Material.SMOOTH_STONE);
                // 棋盘整体铺成同一高度的白色底板，彩色路线之间不再露出低一格的空洞。
                set(x, 1, z, Material.WHITE_CONCRETE);
            }
        }
        int[][] baseCenters = {{-12, -12}, {12, -12}, {12, 12}, {-12, 12}};
        for (int color = 0; color < 4; color++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    set(baseCenters[color][0] + dx, 1, baseCenters[color][1] + dz, COLORS[color]);
                }
            }
        }
        for (int cell = 0; cell < TRACK.length; cell++) {
            set(TRACK[cell][0], 1, TRACK[cell][1], COLORS[FlightRules.trackColorIndex(cell)]);
        }
        // 出生点是外圈唯一允许替换混凝土的特殊格：红西北、黄东北、蓝东南、绿西南。
        for (int color = 0; color < 4; color++) {
            int start = FlightRules.startIndex(color);
            int startX = TRACK[start][0];
            int startZ = TRACK[start][1];
            set(startX, 1, startZ, START_MARKERS[color]);
            spawnStartLabel(color, startX, startZ);
        }
        for (int color = 0; color < 4; color++) {
            for (int step = 0; step < FlightRules.HOME; step++) {
                int[] cell = homeCell(color, step);
                set(cell[0], 1, cell[1], COLORS[color]);
            }
        }
        set(0, 1, 0, Material.BEACON);
        markShortcutRunways();
        dice = world.spawn(new Location(world, centerX + 0.5, y + 5.0, centerZ + 0.5), TextDisplay.class, display -> {
            display.addScoreboardTag(PIECE_TAG);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.setLineWidth(300);
            display.setViewRange(2.0F);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setVisibleByDefault(true);
            display.text(Text.parse(Messages.msg("flight.board.initial")));
        });
        entities.add(dice);
        showToParticipants(dice);
        for (int side = 0; side < players.length; side++) {
            int currentSide = side;
            int color = colorsBySide[side];
            int[] button = BUTTONS[color];
            setFloorButton(button[0], button[1]);
            TextDisplay label = world.spawn(new Location(world, centerX + button[0] + 0.5, y + 3.2,
                    centerZ + button[1] + 0.5), TextDisplay.class, display -> {
                display.addScoreboardTag(PIECE_TAG);
                display.setBillboard(Display.Billboard.CENTER);
                display.setSeeThrough(true);
                display.setShadowed(true);
                display.setLineWidth(170);
                display.setViewRange(2.0F);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setVisibleByDefault(true);
                display.text(Text.parse(Messages.msg("flight.board.seat-label",
                        "side", colorName(color), "player", names[currentSide])));
            });
            entities.add(label);
            showToParticipants(label);
        }
    }

    private void spawnPieces(int[] colorsBySide) {
        for (int side = 0; side < players.length; side++) {
            int color = colorsBySide[side];
            int currentSide = side;
            for (int piece = 0; piece < pieceCount; piece++) {
                int currentPiece = piece;
                Sheep entity = world.spawn(baseLocation(color, piece), Sheep.class, spawned -> {
                    spawned.addScoreboardTag(PIECE_TAG);
                    spawned.setColor(DYES[color]);
                    spawned.setBaby();
                    spawned.setAgeLock(true);
                    spawned.setAI(false);
                    spawned.setAware(false);
                    spawned.setSilent(true);
                    spawned.setInvulnerable(true);
                    spawned.setCollidable(false);
                    spawned.setPersistent(false);
                    spawned.customName(Text.parse(Messages.msg("flight.board.piece-name",
                            "side", colorName(color), "player", names[currentSide],
                            "index", currentPiece + 1)));
                    spawned.setCustomNameVisible(true);
                });
                sheep[side][piece] = entity;
                entities.add(entity);
            }
        }
    }

    private void preparePlayers(List<Player> participants, int[] colorsBySide) {
        for (int side = 0; side < participants.size(); side++) {
            Player player = participants.get(side);
            PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
            snapshots.put(player.getUniqueId(), snapshot);
            snapshot.prepare(player);
            player.teleport(seat(colorsBySide[side]));
            player.setAllowFlight(true);
            player.setFlying(true);
            giveControls(player, colorsBySide[side]);
            Text.send(player, Messages.msg("flight.arena.welcome", "pieces", pieceCount));
        }
    }

    private void giveControls(Player player, int color) {
        player.getInventory().setItem(0, Items.item(FlightControls.ROLL_MATERIAL,
                Messages.msg("flight.item.roll.name", "color", COLOR_TAGS[color]),
                Messages.msg("flight.item.roll.lore-1"), Messages.msg("flight.item.roll.lore-2")));
        for (int piece = 0; piece < pieceCount; piece++) {
            player.getInventory().setItem(piece + 1, Items.item(FlightControls.pieceMaterial(piece),
                    Messages.msg("flight.item.piece.name", "side", colorName(color), "index", piece + 1),
                    Messages.msg("flight.item.piece.lore-1"), Messages.msg("flight.item.piece.lore-2")));
        }
        player.getInventory().setHeldItemSlot(0);
    }

    void sync(int[][] pieces, boolean[] active, int[] colorsBySide) {
        for (int side = 0; side < players.length; side++) {
            int color = colorsBySide[side];
            for (int piece = 0; piece < sheep[side].length; piece++) {
                Sheep entity = sheep[side][piece];
                if (entity == null || !entity.isValid()) continue;
                if (!active[side]) {
                    entity.remove();
                    continue;
                }
                entity.teleport(pieceLocation(color, piece, pieces[side][piece]));
            }
        }
    }

    void showMoveStep(int side, int piece) {
        if (side < 0 || side >= sheep.length || piece < 0 || piece >= sheep[side].length) return;
        Sheep moving = sheep[side][piece];
        if (moving == null || !moving.isValid()) return;
        Location location = moving.getLocation().add(0.0, 0.35, 0.0);
        world.spawnParticle(Particle.CLOUD, location, 4, 0.16, 0.08, 0.16, 0.01);
        for (UUID id : players) {
            Player viewer = plugin.getServer().getPlayer(id);
            if (viewer != null && snapshots.containsKey(id)) {
                viewer.playSound(location, Sound.BLOCK_WOOL_STEP, 0.45f, 0.9f + piece * 0.08f);
            }
        }
    }

    void showShortcutStep(int side, int piece, int sourceProgress, int destinationProgress,
                          int step, int totalSteps) {
        if (side < 0 || side >= sheep.length || piece < 0 || piece >= sheep[side].length) return;
        Sheep moving = sheep[side][piece];
        if (moving == null || !moving.isValid()) return;
        Location source = pieceLocation(colorsBySide[side], piece, sourceProgress);
        Location destination = pieceLocation(colorsBySide[side], piece, destinationProgress);
        double ratio = Math.max(0.0, Math.min(1.0, step / (double) totalSteps));
        Location position = source.clone().add(
                (destination.getX() - source.getX()) * ratio,
                Math.sin(Math.PI * ratio) * 2.0,
                (destination.getZ() - source.getZ()) * ratio);
        moving.teleport(position);
        world.spawnParticle(Particle.END_ROD, position.clone().add(0.0, 0.3, 0.0),
                5, 0.12, 0.12, 0.12, 0.01);
        for (UUID id : players) {
            Player viewer = plugin.getServer().getPlayer(id);
            if (viewer != null && snapshots.containsKey(id)) {
                viewer.playSound(position, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f,
                        0.9f + (float) ratio * 0.6f);
            }
        }
    }

    void showStatus(int side, int face, boolean rolling, int seconds, int[][] pieces, boolean[] active) {
        if (dice == null || !dice.isValid()) return;
        int safeFace = Math.max(0, Math.min(6, face));
        int safeSide = Math.max(0, Math.min(players.length - 1, side));
        int color = colorsBySide[safeSide];
        String points = safeFace == 0 ? Messages.msg("flight.board.dice.waiting")
                : Messages.msg("flight.board.dice.points", "symbol", DICE[safeFace], "value", safeFace);
        String timeKey = seconds <= 10 ? "flight.board.seconds.urgent"
                : seconds <= 30 ? "flight.board.seconds.warning" : "flight.board.seconds.normal";
        StringBuilder text = new StringBuilder(Messages.msg("flight.board.title", "brand", plugin.brand()))
                .append("\n")
                .append(Messages.msg("flight.board.actor", "side", colorName(color),
                        "player", names[safeSide], "time", Messages.msg(timeKey, "seconds", seconds)))
                .append("\n")
                .append(Messages.msg(rolling ? "flight.board.dice.rolling" : "flight.board.dice.settled",
                        "points", points));
        for (int playerSide = 0; playerSide < players.length; playerSide++) {
            int finished = 0;
            for (int progress : pieces[playerSide]) if (progress == FlightRules.FINISHED) finished++;
            int playerColor = colorsBySide[playerSide];
            text.append("\n").append(active[playerSide]
                    ? Messages.msg("flight.board.player.active", "side", colorName(playerColor),
                            "player", names[playerSide], "finished", finished, "total", pieceCount)
                    : Messages.msg("flight.board.player.eliminated", "side", colorName(playerColor),
                            "player", names[playerSide]));
        }
        dice.text(Text.parse(text.toString()));
        world.spawnParticle(Particle.END_ROD, dice.getLocation(), rolling ? 8 : 16, 0.6, 0.5, 0.6, 0.02);
    }

    /** 完成全部棋子后留在场内观战，最终结算时仍由快照完整恢复。 */
    void spectate(Player player) {
        if (!snapshots.containsKey(player.getUniqueId())) return;
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    void addSpectator(Player player) {
        if (closed || snapshots.containsKey(player.getUniqueId())) return;
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        snapshots.put(player.getUniqueId(), snapshot);
        snapshot.prepare(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.teleport(new Location(world, centerX + 0.5, y + 6.0, centerZ + 0.5));
        for (Entity entity : entities) if (entity != null && entity.isValid()) player.showEntity(plugin, entity);
        Text.send(player, Messages.msg("flight.spectate.joined-arena"));
    }

    int buttonSide(Block block) {
        if (block == null || !world.equals(block.getWorld()) || block.getY() != y + 2) return -1;
        int relativeX = block.getX() - centerX;
        int relativeZ = block.getZ() - centerZ;
        for (int side = 0; side < players.length; side++) {
            int[] position = BUTTONS[colorsBySide[side]];
            if (position[0] == relativeX && position[1] == relativeZ) return side;
        }
        return -1;
    }

    int[] piece(Entity clicked) {
        if (clicked == null) return null;
        for (int side = 0; side < sheep.length; side++) {
            for (int piece = 0; piece < sheep[side].length; piece++) {
                if (sheep[side][piece] != null && sheep[side][piece].getUniqueId().equals(clicked.getUniqueId())) {
                    return new int[]{side, piece};
                }
            }
        }
        return null;
    }

    void release(Player player) {
        PlayerSnapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot == null) return;
        player.teleport(snapshot.location());
        snapshot.restore(player);
    }

    boolean protects(UUID player) { return snapshots.containsKey(player); }
    boolean contains(Location location) {
        return location != null && world.equals(location.getWorld())
                && Math.abs(location.getX() - (centerX + 0.5)) <= 16.5
                && Math.abs(location.getZ() - (centerZ + 0.5)) <= 16.5
                && location.getY() >= y && location.getY() <= y + 7;
    }

    void close() {
        if (closed) return;
        closed = true;
        for (UUID id : List.copyOf(snapshots.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) release(player);
            else snapshots.remove(id);
        }
        for (Entity entity : entities) if (entity != null && entity.isValid()) entity.remove();
        entities.clear();
        arenaWorld.clearBox(centerX, centerZ, 17, 17, y, y + 8);
        arenaWorld.release(slot);
    }

    private void rollback() {
        for (UUID id : List.copyOf(snapshots.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) release(player);
        }
        for (Entity entity : entities) if (entity != null && entity.isValid()) entity.remove();
        entities.clear();
        arenaWorld.clearBox(centerX, centerZ, 17, 17, y, y + 8);
    }

    private Location pieceLocation(int color, int piece, int progress) {
        if (progress < 0) return baseLocation(color, piece);
        if (progress < FlightRules.OUTER) {
            int global = FlightRules.trackIndex(color, progress);
            return cellLocation(TRACK[global][0], TRACK[global][1], piece);
        }
        if (progress < FlightRules.FINISHED) {
            int[] cell = homeCell(color, progress - FlightRules.OUTER);
            return cellLocation(cell[0], cell[1], piece);
        }
        double[][] finish = {{-0.55, -0.55}, {0.55, -0.55}, {0.55, 0.55}, {-0.55, 0.55}};
        int[] offset = pieceOffset(piece);
        // 同色棋子在终点使用独立停放位，避免实体重叠后右键选中错误的棋子。
        return new Location(world, centerX + 0.5 + finish[color][0] + offset[0] * 0.22,
                y + 2.0, centerZ + 0.5 + finish[color][1] + offset[1] * 0.22);
    }

    private Location baseLocation(int color, int piece) {
        int[][] centers = {{-12, -12}, {12, -12}, {12, 12}, {-12, 12}};
        int[] offset = pieceOffset(piece);
        return new Location(world, centerX + centers[color][0] + 0.5 + offset[0],
                y + 2.0, centerZ + centers[color][1] + 0.5 + offset[1]);
    }

    private Location cellLocation(int logicalX, int logicalZ, int piece) {
        int[] offset = pieceOffset(piece);
        return new Location(world, centerX + logicalX + 0.5 + offset[0] * 0.12,
                y + 2.0, centerZ + logicalZ + 0.5 + offset[1] * 0.12);
    }

    private int[] pieceOffset(int piece) {
        if (pieceCount == 2) return piece == 0 ? new int[]{-1, 0} : new int[]{1, 0};
        if (pieceCount == 3) {
            return switch (piece) {
                case 0 -> new int[]{0, -1};
                case 1 -> new int[]{-1, 1};
                default -> new int[]{1, 1};
            };
        }
        return switch (piece) {
            case 0 -> new int[]{-1, -1};
            case 1 -> new int[]{1, -1};
            case 2 -> new int[]{-1, 1};
            default -> new int[]{1, 1};
        };
    }

    private Location seat(int color) {
        return switch (color) {
            case 0 -> new Location(world, centerX + 0.5, y + 1.0, centerZ - 15.5, 0f, 12f);
            case 1 -> new Location(world, centerX + 15.5, y + 1.0, centerZ + 0.5, 90f, 12f);
            case 2 -> new Location(world, centerX + 0.5, y + 1.0, centerZ + 15.5, 180f, 12f);
            default -> new Location(world, centerX - 15.5, y + 1.0, centerZ + 0.5, -90f, 12f);
        };
    }

    private void set(int relativeX, int dy, int relativeZ, Material material) {
        world.getBlockAt(centerX + relativeX, y + dy, centerZ + relativeZ).setType(material, false);
    }

    private void setFloorButton(int relativeX, int relativeZ) {
        Block block = world.getBlockAt(centerX + relativeX, y + 2, centerZ + relativeZ);
        block.setType(Material.STONE_BUTTON, false);
        if (block.getBlockData() instanceof Switch data) {
            data.setFace(Switch.Face.FLOOR);
            block.setBlockData(data, false);
        }
    }

    private void showToParticipants(Entity entity) {
        for (UUID id : players) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) player.showEntity(plugin, entity);
        }
    }

    private void markShortcutRunways() {
        for (int color = 0; color < 4; color++) {
            int sourceGlobal = FlightRules.trackIndex(color, FlightRules.FLIGHT_LANE_START);
            int destinationGlobal = FlightRules.trackIndex(color,
                    FlightRules.FLIGHT_LANE_START + FlightRules.FLIGHT_LANE_DISTANCE);
            int sourceX = TRACK[sourceGlobal][0];
            int sourceZ = TRACK[sourceGlobal][1];
            int destinationX = TRACK[destinationGlobal][0];
            int destinationZ = TRACK[destinationGlobal][1];
            // 飞跃端点仍保持原本的四色混凝土，不再用釉陶覆盖。连线改铺在地面上方的羊毛地毯；
            // 若插值点碰到任意一色的终点跑道则留空，保证其他颜色通往中心的路线完整。
            for (int step = 1; step < 12; step++) {
                int x = (int) Math.round(sourceX + (destinationX - sourceX) * (step / 12.0));
                int z = (int) Math.round(sourceZ + (destinationZ - sourceZ) * (step / 12.0));
                if (isHomeLaneCell(x, z) || isOuterTrackCell(x, z) || (x == 0 && z == 0)) continue;
                set(x, 2, z, SHORTCUT_PATHS[color]);
            }
        }
    }

    private boolean isHomeLaneCell(int x, int z) {
        for (int color = 0; color < 4; color++) {
            for (int step = 0; step < FlightRules.HOME; step++) {
                int[] cell = homeCell(color, step);
                if (cell[0] == x && cell[1] == z) return true;
            }
        }
        return false;
    }

    private boolean isOuterTrackCell(int x, int z) {
        for (int[] cell : TRACK) if (cell[0] == x && cell[1] == z) return true;
        return false;
    }

    private void spawnStartLabel(int color, int startX, int startZ) {
        TextDisplay label = world.spawn(new Location(world, centerX + startX + 0.5, y + 3.15,
                centerZ + startZ + 0.5), TextDisplay.class, display -> {
            display.addScoreboardTag(PIECE_TAG);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.setLineWidth(120);
            display.setViewRange(2.0F);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setVisibleByDefault(true);
            display.text(Text.parse(Messages.msg("flight.board.start-label", "side", colorName(color))));
        });
        entities.add(label);
        showToParticipants(label);
    }

    /** 颜色名（含颜色标记）从语言文件读取。 */
    private static String colorName(int color) {
        return Messages.msg("flight.color." + COLOR_KEYS[Math.floorMod(color, COLOR_KEYS.length)]);
    }

    private static int[] homeCell(int color, int step) {
        return switch (color) {
            case 0 -> new int[]{0, -6 + step};
            // 黄色出生点为了同时服从外圈四色序位，终点跑道第一格从东北方向斜接入。
            case 1 -> step == 0 ? new int[]{6, -1} : new int[]{6 - step, 0};
            case 2 -> new int[]{0, 6 - step};
            default -> new int[]{-6 + step, 0};
        };
    }

    private static int[][] createTrack() {
        List<int[]> cells = new ArrayList<>(FlightRules.TRACK_CELLS);
        // 每象限恰好 12 格（4 的倍数）。两个折角用短直角阶梯展开，保持单格连续且不重复。
        int[][] quadrant = {
                {0, -7}, {1, -7}, {2, -7}, {3, -7}, {4, -7}, {4, -6},
                {5, -5}, {6, -5}, {6, -4}, {7, -3}, {7, -2}, {7, -1}
        };
        for (int rotation = 0; rotation < 4; rotation++) {
            for (int[] point : quadrant) {
                int x = point[0];
                int z = point[1];
                for (int turn = 0; turn < rotation; turn++) {
                    int oldX = x;
                    x = -z;
                    z = oldX;
                }
                cells.add(new int[]{x, z});
            }
        }
        if (cells.size() != FlightRules.TRACK_CELLS) {
            throw new IllegalStateException("Ludo outer track cell count does not match the rules: " + cells.size());
        }
        return cells.toArray(int[][]::new);
    }
}
