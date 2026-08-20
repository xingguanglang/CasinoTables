package io.github.xingguanglang.casinotables.poker;

import io.github.xingguanglang.casinotables.CasinoTablesPlugin;
import io.github.xingguanglang.casinotables.Items;
import io.github.xingguanglang.casinotables.Messages;
import io.github.xingguanglang.casinotables.Text;
import io.github.xingguanglang.casinotables.arena.ArenaShape;
import io.github.xingguanglang.casinotables.arena.ArenaWorld;
import io.github.xingguanglang.casinotables.arena.PlayerSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class PokerArena {
    record ControlButton(int side, Material action) { }

    static final String DISPLAY_TAG = io.github.xingguanglang.casinotables.arena.ArenaTags.POKER_DISPLAY;
    /**
     * 空座位的内部哨兵：PokerGame 清空座位时写入同一个值，这里只参与 equals 比较，从不显示给玩家——
     * 座位牌上的文字走 poker.arena.seat.empty。连字符不是合法用户名字符，所以永远撞不上真实玩家名。
     */
    static final String EMPTY_SEAT_NAME = "-empty-seat-";
    private static final int ROOM_X = 20;
    private static final int ROOM_Z = 16;
    private static final int[][] BET_ZONE = {
            {5, 9}, {11, -5}, {11, 5}, {-5, 9}, {-11, 5}, {-11, -5}
    };
    private static final int[][] CHIP_PILES = {
            {-7, -3}, {-7, 0}, {-7, 3}, {6, -3}, {6, 0}, {6, 3}
    };
    private static final int CHIP_PILE_WIDTH = 3;
    private static final int CHIP_PILE_DEPTH = 2;
    private static final int CHIP_PILE_HEIGHT = 5;
    private static final int[][] EXIT_BUTTON = {
            {9, 12}, {15, -3}, {15, 3}, {-9, 12}, {-14, 3}, {-14, -3}
    };
    private static final int[][][] CONTROL_BUTTONS = {
            {{3, 14}, {4, 14}, {5, 14}, {6, 14}, {7, 14}},
            {{17, -10}, {17, -9}, {17, -8}, {17, -7}, {17, -6}},
            {{17, 6}, {17, 7}, {17, 8}, {17, 9}, {17, 10}},
            {{-7, 14}, {-6, 14}, {-5, 14}, {-4, 14}, {-3, 14}},
            {{-17, 10}, {-17, 9}, {-17, 8}, {-17, 7}, {-17, 6}},
            {{-17, -6}, {-17, -7}, {-17, -8}, {-17, -9}, {-17, -10}}
    };
    private static final Material[] CONTROL_ACTIONS = {
            Material.YELLOW_DYE, Material.LIME_DYE, Material.CHEST,
            Material.NETHER_STAR, Material.RED_DYE
    };
    private static final Material[] CONTROL_BASES = {
            Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.MAGENTA_CONCRETE, Material.RED_CONCRETE
    };
    private static final String[] CONTROL_LABEL_KEYS = {
            "poker.arena.control.call",
            "poker.arena.control.confirm",
            "poker.arena.control.withdraw",
            "poker.arena.control.all-in",
            "poker.arena.control.fold"
    };
    private static final Material[] SEAT_COLORS = {
            Material.RED_CARPET, Material.BLUE_CARPET, Material.YELLOW_CARPET,
            Material.LIME_CARPET, Material.PURPLE_CARPET, Material.ORANGE_CARPET
    };
    private static final Material[] SEAT_BLOCKS = {
            Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE, Material.PURPLE_CONCRETE, Material.ORANGE_CONCRETE
    };
    private static final Material[] SEAT_GLASS = {
            Material.RED_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS,
            Material.LIME_STAINED_GLASS, Material.PURPLE_STAINED_GLASS, Material.ORANGE_STAINED_GLASS
    };

    private final CasinoTablesPlugin plugin;
    private final ArenaWorld arenaWorld;
    private final World world;
    private final int slot;
    private final int centerX;
    private final int centerZ = 0;
    private final int y;
    private final PokerArenaStyle style;
    private final ArenaShape shape;
    private final int roomX;
    private final int roomZ;
    private final UUID[] players;
    private final String[] names;
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final List<Block> lamps = new ArrayList<>();
    private final TextDisplay[] publicCards = new TextDisplay[5];
    /** 与 publicCards 同位置的管理员专用公牌，提前显示尚未发出的牌；仅对开启 floppeek 的管理员可见。 */
    private final TextDisplay[] peekCards = new TextDisplay[5];
    private final TextDisplay[] playerStatus;
    private final TextDisplay[] betZoneLabels;
    private final TextDisplay[] exitButtonLabels;
    private final TextDisplay[][] controlButtonLabels;
    private final TextDisplay[] chipPileLabels = new TextDisplay[PokerChips.denominations().size()];
    private final TextDisplay[][] holeCards;
    private final TextDisplay[] handStrength;
    private final Villager[] botPlayers;
    private final List<Map<PokerChips.Denomination, Integer>> chipLayouts;
    private final boolean[] compactChips;
    private final boolean[] chipLayoutDirty;
    private TextDisplay tableStatus;
    private TextDisplay boardStatus;
    private TextDisplay peekBoardStatus;
    private TextDisplay atmStatus;
    private int lastAtmState;
    private long nextAtmRefresh;
    private boolean closed;
    private boolean privateCardsPublic;

    PokerArena(CasinoTablesPlugin plugin, ArenaWorld arenaWorld, int slot, UUID[] seatPlayers,
               String[] seatNames, List<Player> participants, PokerCard[][] hole, PokerArenaStyle style, ArenaShape shape) {
        this.plugin = plugin;
        this.arenaWorld = arenaWorld;
        this.world = arenaWorld.world();
        this.slot = slot;
        this.centerX = arenaWorld.centerX(slot);
        this.y = arenaWorld.baseY();
        this.style = style == null ? PokerArenaStyle.CLASSIC : style;
        this.shape = shape == null ? ArenaShape.RECTANGLE : shape;
        this.roomX = this.shape.roomX(ROOM_X);
        this.roomZ = this.shape.roomZ(ROOM_Z);
        this.players = seatPlayers.clone();
        this.names = seatNames.clone();
        this.playerStatus = new TextDisplay[players.length];
        this.betZoneLabels = new TextDisplay[players.length];
        this.exitButtonLabels = new TextDisplay[players.length];
        this.controlButtonLabels = new TextDisplay[players.length][CONTROL_ACTIONS.length];
        this.holeCards = new TextDisplay[players.length][2];
        this.handStrength = new TextDisplay[players.length];
        this.botPlayers = new Villager[players.length];
        this.chipLayouts = new ArrayList<>(players.length);
        this.compactChips = new boolean[players.length];
        this.chipLayoutDirty = new boolean[players.length];
        java.util.Arrays.fill(chipLayoutDirty, true);
        for (int side = 0; side < players.length; side++) chipLayouts.add(new LinkedHashMap<>());
        try {
            buildRoom();
            preparePlayers(participants);
            spawnDisplays(hole);
            spawnInitialBots();
        } catch (RuntimeException | Error throwable) {
            rollback();
            throw throwable;
        }
    }

    private void buildRoom() {
        arenaWorld.clearBox(centerX, centerZ, roomX + 1, roomZ + 1, y, y + 8);
        PokerArenaStyle.Palette palette = style.palette();
        // 露天材质和开放式轮廓都不砌墙、不封顶。
        boolean roofed = !palette.outdoor() && !shape.open();
        for (int x = -roomX; x <= roomX; x++) {
            for (int z = -roomZ; z <= roomZ; z++) {
                if (!shape.inside(x, z, roomX, roomZ)) continue;
                set(x, 0, z, shape.trim(x, z, roomX, roomZ) ? palette.floorTrim()
                        : (Math.floorMod(x + z, 8) == 0 ? palette.wallAccent() : palette.floor()));
                boolean edge = shape.boundary(x, z, roomX, roomZ);
                if (roofed && edge) {
                    for (int dy = 1; dy <= 6; dy++) {
                        boolean column = Math.floorMod(x + z, 8) == 0;
                        Material wallMaterial = column ? palette.wallAccent()
                                : dy == 3 || dy == 4 ? palette.wallBand()
                                : dy <= 2 ? palette.wall() : palette.ceiling();
                        set(x, dy, z, wallMaterial);
                    }
                } else if (!roofed && edge && Math.floorMod(x + z, 6) == 0) {
                    // 开放式：不砌墙，只在边缘每隔几格立一根矮柱标出台子范围。
                    for (int dy = 1; dy <= 3; dy++) set(x, dy, z, palette.wallAccent());
                    set(x, 4, z, palette.ceilingAccent());
                }
                if (roofed) {
                    set(x, 7, z, (x % 8 == 0 && z % 8 == 0)
                            ? palette.ceilingAccent() : palette.ceiling());
                }
            }
        }

        // 露天与开放式没有天花，吊灯和内墙柱会悬在空中，直接跳过。
        int[][] chandeliers = roofed ? new int[][]{{-12, -9}, {12, -9}, {-12, 9}, {12, 9}} : new int[0][];
        for (int[] point : chandeliers) {
            set(point[0], 6, point[1], Material.END_ROD);
            set(point[0], 5, point[1], Material.SEA_LANTERN);
            set(point[0] - 1, 5, point[1], Material.GOLD_BLOCK);
            set(point[0] + 1, 5, point[1], Material.GOLD_BLOCK);
            set(point[0], 5, point[1] - 1, Material.GOLD_BLOCK);
            set(point[0], 5, point[1] + 1, Material.GOLD_BLOCK);
        }

        // 覆盖整座赌场的隐形满级灯网：没有碰撞，也不会挡住全息与牌桌视线。
        for (int x = -16; x <= 16; x += 4) {
            for (int z = -12; z <= 12; z += 4) set(x, 6, z, Material.LIGHT);
        }

        // 金色、紫水晶与宝石内墙柱，位于座位外侧，不侵占牌桌区域。
        for (int z = -12; roofed && z <= 12; z += 4) {
            decorateCasinoColumn(-19, z, Math.floorMod(z / 4, 2) == 0);
            decorateCasinoColumn(19, z, Math.floorMod(z / 4, 2) != 0);
        }
        for (int x = -16; roofed && x <= 16; x += 4) {
            decorateCasinoColumn(x, -15, Math.floorMod(x / 4, 2) == 0);
            decorateCasinoColumn(x, 15, Math.floorMod(x / 4, 2) != 0);
        }

        buildAtm();

        PokerArenaStyle.Palette tablePalette = style.palette();
        for (int x = -8; x <= 8; x++) {
            for (int z = -5; z <= 5; z++) {
                boolean border = Math.abs(x) == 8 || Math.abs(z) == 5;
                set(x, 1, z, border ? tablePalette.tableRim() : tablePalette.tableTop());
            }
        }

        // 公牌左右各三座内嵌筹码槽；空槽保持绿色桌面，避免结算后留下挖空的牌桌。
        for (int[] anchor : CHIP_PILES) {
            for (int dx = 0; dx < CHIP_PILE_WIDTH; dx++) {
                for (int dz = 0; dz < CHIP_PILE_DEPTH; dz++) {
                    set(anchor[0] + dx, 0, anchor[1] + dz, Material.GILDED_BLACKSTONE);
                    set(anchor[0] + dx, 1, anchor[1] + dz, Material.GREEN_CONCRETE);
                }
            }
        }

        for (int side = 0; side < BET_ZONE.length; side++) {
            int baseX = BET_ZONE[side][0];
            int baseZ = BET_ZONE[side][1];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) set(baseX + dx, 1, baseZ + dz, Material.BLACK_CONCRETE);
            }
            set(baseX, 1, baseZ, SEAT_BLOCKS[side]);
            Location seat = seat(side);
            set((int) Math.floor(seat.getX() - centerX), 1,
                    (int) Math.floor(seat.getZ() - centerZ), SEAT_COLORS[side]);
        }

        for (int side = 0; side < players.length; side++) {
            int buttonX = EXIT_BUTTON[side][0];
            int buttonZ = EXIT_BUTTON[side][1];
            set(buttonX, 1, buttonZ, Material.GOLD_BLOCK);
            set(buttonX, 2, buttonZ, Material.POLISHED_BLACKSTONE_BUTTON);
            Block button = world.getBlockAt(centerX + buttonX, y + 2, centerZ + buttonZ);
            if (button.getBlockData() instanceof Switch data) {
                data.setFace(Switch.Face.FLOOR);
                button.setBlockData(data, false);
            }
            for (int action = 0; action < CONTROL_ACTIONS.length; action++) {
                int controlX = CONTROL_BUTTONS[side][action][0];
                int controlZ = CONTROL_BUTTONS[side][action][1];
                set(controlX, 1, controlZ, CONTROL_BASES[action]);
                set(controlX, 2, controlZ, Material.POLISHED_BLACKSTONE_BUTTON);
                Block control = world.getBlockAt(centerX + controlX, y + 2, centerZ + controlZ);
                if (control.getBlockData() instanceof Switch data) {
                    data.setFace(Switch.Face.FLOOR);
                    control.setBlockData(data, false);
                }
            }
        }

        int[][] lampLocations = {{-6, -1}, {-6, 2}, {6, -1}, {6, 2}, {-3, -4}, {3, -4}, {-3, 4}, {3, 4}};
        for (int[] point : lampLocations) {
            set(point[0], 2, point[1], Material.REDSTONE_LAMP);
            lamps.add(world.getBlockAt(centerX + point[0], y + 2, centerZ + point[1]));
        }
        applySelectedStyle();
    }

    /** 房间外壳已经完全由调色板驱动，这里只需要按同一套材质画牌桌与下注区。 */
    private void applySelectedStyle() {
        paintTableAndBettingZones();
    }

    /** 模板一的桌面与地面齐平；模板二保留一层玻璃面；自然模板使用苔藓木质牌桌。 */
    private void paintTableAndBettingZones() {
        PokerArenaStyle.Palette palette = style.palette();
        for (int x = -8; x <= 8; x++) {
            for (int z = -5; z <= 5; z++) {
                boolean border = Math.abs(x) == 8 || Math.abs(z) == 5;
                if (palette.hasOverlay()) {
                    // 覆盖层模板：桌面下层铺发光棋盘格，上层盖玻璃，外沿单独一种玻璃。
                    set(x, 0, z, Math.floorMod(x + z, 2) == 0 ? palette.tableGlowA() : palette.tableGlowB());
                    set(x, 1, z, border ? palette.overlayRim() : palette.tableOverlay());
                } else {
                    set(x, 0, z, border ? palette.tableRim() : palette.tableTop());
                    set(x, 1, z, Material.AIR);
                }
            }
        }
        for (int side = 0; side < players.length; side++) renderBetZone(side, false);
    }

    private void clearNatureColumn(int relativeX, int relativeZ) {
        for (int dy = 1; dy <= 6; dy++) set(relativeX, dy, relativeZ, Material.AIR);
    }

    private void buildGardenTree(int relativeX, int relativeZ) {
        for (int dy = 1; dy <= 4; dy++) set(relativeX, dy, relativeZ, Material.OAK_LOG);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 3) continue;
                set(relativeX + dx, 4, relativeZ + dz,
                        Math.floorMod(dx + dz, 3) == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.OAK_LEAVES);
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    set(relativeX + dx, 5, relativeZ + dz, Material.OAK_LEAVES);
                }
            }
        }
        set(relativeX, 3, relativeZ + 1, Material.LANTERN);
    }

    private void preparePlayers(List<Player> participants) {
        for (int side = 0; side < participants.size(); side++) {
            preparePlayer(side, participants.get(side));
        }
    }

    private void preparePlayer(int side, Player player) {
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        snapshots.put(player.getUniqueId(), snapshot);
        snapshot.prepare(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(seat(side));
        Text.send(player, Messages.msg("poker.arena.welcome"));
    }

    void addPlayer(int side, Player player) {
        if (closed || side < 0 || side >= players.length) {
            throw new IllegalStateException("Invalid Texas Hold'em seat index");
        }
        removeBot(side);
        players[side] = player.getUniqueId();
        names[side] = player.getName();
        try {
            preparePlayer(side, player);
            updateSeatIdentity(side, false);
            hidePrivateFrom(player);
            for (Player online : plugin.getServer().getOnlinePlayers()) hidePrivateFrom(online);
        } catch (RuntimeException | Error throwable) {
            release(player);
            players[side] = new UUID(0L, side + 1L);
            names[side] = EMPTY_SEAT_NAME;
            updateSeatIdentity(side, true);
            throw throwable;
        }
    }

    void vacateSeat(int side) {
        if (side < 0 || side >= players.length) return;
        removeBot(side);
        players[side] = new UUID(0L, side + 1L);
        names[side] = EMPTY_SEAT_NAME;
        updateSeatIdentity(side, true);
    }

    void setBotSeat(int side, UUID botId, String botName) {
        if (side < 0 || side >= players.length) return;
        removeBot(side);
        players[side] = botId;
        names[side] = botName;
        updateSeatIdentity(side, false);
        spawnBot(side);
    }

    private void spawnInitialBots() {
        for (int side = 0; side < players.length; side++) {
            if (CasinoBot.namedBot(names[side])) spawnBot(side);
        }
    }

    private void spawnBot(int side) {
        Villager bot = world.spawn(seat(side), Villager.class, spawned -> {
            spawned.addScoreboardTag(DISPLAY_TAG);
            spawned.setAI(false);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setCollidable(false);
            spawned.setPersistent(false);
            spawned.setProfession(Villager.Profession.NITWIT);
            spawned.customName(Text.parse("<aqua><bold>" + names[side] + "</bold></aqua>"));
            spawned.setCustomNameVisible(true);
        });
        botPlayers[side] = bot;
        entities.add(bot);
    }

    private void removeBot(int side) {
        Villager bot = botPlayers[side];
        if (bot != null && bot.isValid()) bot.remove();
        botPlayers[side] = null;
    }

    private void updateSeatIdentity(int side, boolean empty) {
        TextDisplay status = playerStatus[side];
        if (status != null && status.isValid()) status.text(Text.parse(empty
                ? Messages.msg("poker.arena.seat.empty")
                : Messages.msg("poker.arena.seat.waiting", "player", names[side])));
        TextDisplay bet = betZoneLabels[side];
        if (bet != null && bet.isValid()) bet.text(Text.parse(empty
                ? Messages.msg("poker.arena.bet-zone.empty")
                : Messages.msg("poker.arena.bet-zone.label", "player", names[side])));
        for (TextDisplay display : holeCards[side]) {
            if (display != null && display.isValid()) display.text(Text.parse(
                    Messages.msg(empty ? "poker.arena.hole.empty" : "poker.arena.hole.next-hand")));
        }
        TextDisplay strength = handStrength[side];
        if (strength != null && strength.isValid()) strength.text(Text.parse(empty
                ? Messages.msg("poker.arena.strength.waiting-player")
                : Messages.msg("poker.arena.strength.sitting-out")));
    }

    private void spawnDisplays(PokerCard[][] hole) {
        Villager dealer = world.spawn(dealerLocation(), Villager.class, spawned -> {
            spawned.addScoreboardTag(DISPLAY_TAG);
            spawned.setProfession(Villager.Profession.CLERIC);
            spawned.setVillagerLevel(5);
            spawned.setAdult();
            spawned.setAgeLock(true);
            spawned.setAI(false);
            spawned.setAware(false);
            spawned.setSilent(true);
            spawned.setInvulnerable(true);
            spawned.setCollidable(false);
            spawned.setPersistent(false);
            spawned.customName(Text.parse(Messages.msg("poker.arena.dealer.name", "brand", plugin.brand())));
            spawned.setCustomNameVisible(true);
        });
        entities.add(dealer);
        tableStatus = spawnRoomText(new Location(world, centerX + 0.5, y + 5.75, centerZ + 0.5),
                Messages.msg("poker.arena.table.loading", "brand", plugin.brand()), 300);
        boardStatus = spawnRoomText(new Location(world, centerX + 0.5, y + 3.55, centerZ + 0.5),
                Messages.msg("poker.arena.board.empty"), 420);
        scaleText(boardStatus, 1.25F);
        peekBoardStatus = spawnPrivateText(new Location(world, centerX + 0.5, y + 3.55, centerZ + 0.5),
                Messages.msg("poker.arena.board.empty"), 420);
        scaleText(peekBoardStatus, 1.25F);
        atmStatus = spawnRoomText(new Location(world, centerX + 16.2, y + 5.15,
                centerZ + 0.5), Messages.msg("poker.arena.atm.loading", "brand", plugin.brand()), 260);
        scaleText(atmStatus, 1.15F);
        for (int card = 0; card < 5; card++) {
            Location boardSlot = new Location(world, centerX - 5.0 + card * 2.5, y + 2.75, centerZ + 0.5);
            publicCards[card] = spawnText(boardSlot, "<gray><bold>[ ? ]</bold>", 110);
            scaleText(publicCards[card], 1.45F);
            peekCards[card] = spawnPrivateText(boardSlot.clone(), "<gray><bold>[ ? ]</bold>", 110);
            scaleText(peekCards[card], 1.45F);
        }
        for (int side = 0; side < players.length; side++) {
            boolean emptySeat = EMPTY_SEAT_NAME.equals(names[side]);
            playerStatus[side] = spawnText(statusLocation(side), emptySeat
                    ? Messages.msg("poker.arena.seat.empty")
                    : Messages.msg("poker.arena.seat.name", "player", names[side]), 230);
            betZoneLabels[side] = spawnText(betZoneLabelLocation(side), emptySeat
                    ? Messages.msg("poker.arena.bet-zone.empty")
                    : Messages.msg("poker.arena.bet-zone.label", "player", names[side]), 170);
            exitButtonLabels[side] = spawnText(exitButtonLabelLocation(side),
                    Messages.msg("poker.arena.exit.idle"), 170);
            for (int action = 0; action < CONTROL_ACTIONS.length; action++) {
                controlButtonLabels[side][action] = spawnText(controlButtonLabelLocation(side, action),
                        Messages.msg(CONTROL_LABEL_KEYS[action]), 105);
            }
            for (int card = 0; card < 2; card++) {
                PokerCard value = hole[side][card];
                TextDisplay display = spawnPrivateText(holeLocation(side, card),
                        value == null ? Messages.msg("poker.arena.hole.empty") : cardText(value), 80);
                scaleText(display, 1.25F);
                holeCards[side][card] = display;
                Player owner = plugin.getServer().getPlayer(players[side]);
                if (owner != null) owner.showEntity(plugin, display);
            }
            handStrength[side] = spawnPrivateText(handStrengthLocation(side),
                    hole[side][0] == null || hole[side][1] == null
                            ? Messages.msg("poker.arena.strength.waiting-player")
                            : Messages.msg("poker.arena.strength.current", "hand",
                                    PokerHandEvaluator.describeCurrent(
                                            List.of(hole[side][0], hole[side][1]))), 180);
            Player owner = plugin.getServer().getPlayer(players[side]);
            if (owner != null) owner.showEntity(plugin, handStrength[side]);
        }
        for (int index = 0; index < CHIP_PILES.length; index++) {
            int[] anchor = CHIP_PILES[index];
            chipPileLabels[index] = spawnText(new Location(world, centerX + anchor[0] + 1.5,
                    y + 6.15, centerZ + anchor[1] + 1.0), "", 130);
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) hidePrivateFrom(online);
        // 玩家刚被传送到竞技世界时，客户端可能尚未开始追踪该区块；延迟重发可见性状态。
        for (long delay : new long[]{1L, 10L, 40L}) {
            plugin.getServer().getScheduler().runTaskLater(plugin, this::refreshVisibility, delay);
        }
    }

    /**
     * @param board 已经公开的公牌
     * @param peek  含尚未发出部分的完整五张公牌，只写进 floppeek 管理员专用的全息
     */
    void syncBoard(List<PokerCard> board, List<PokerCard> peek) {
        StringBuilder combined = new StringBuilder();
        StringBuilder peekCombined = new StringBuilder();
        boolean hasUnrevealed = false;
        for (int card = 0; card < 5; card++) {
            String text = card < board.size() ? cardText(board.get(card)) : "<gray><bold>[ ? ]</bold>";
            combined.append(text).append(' ');
            TextDisplay display = publicCards[card];
            if (display != null && display.isValid()) display.text(Text.parse(text));

            String peekText = text;
            if (card >= board.size() && card < peek.size()) {
                peekText = peekCardText(peek.get(card));
                hasUnrevealed = true;
            }
            peekCombined.append(peekText).append(' ');
            TextDisplay peekDisplay = peekCards[card];
            if (peekDisplay != null && peekDisplay.isValid()) peekDisplay.text(Text.parse(peekText));
        }
        if (boardStatus != null && boardStatus.isValid()) {
            boardStatus.text(Text.parse(Messages.msg("poker.arena.board.cards",
                    "cards", combined.toString().trim())));
        }
        if (peekBoardStatus != null && peekBoardStatus.isValid()) {
            peekBoardStatus.text(Text.parse(Messages.msg("poker.arena.board.cards",
                    "cards", peekCombined.toString().trim())
                    + (hasUnrevealed ? Messages.msg("poker.arena.board.peek-note") : "")));
        }
        refreshVisibility();
    }

    void syncHandStrengths(List<PokerCard> board, PokerCard[][] hole, boolean[] folded, boolean[] seated) {
        for (int side = 0; side < players.length; side++) {
            TextDisplay display = handStrength[side];
            if (display == null || !display.isValid()) continue;
            if (!seated[side]) {
                display.text(Text.parse(Messages.msg("poker.arena.strength.left")));
                continue;
            }
            if (hole[side][0] == null || hole[side][1] == null) {
                display.text(Text.parse(Messages.msg("poker.arena.strength.sitting-out")));
                continue;
            }
            if (folded[side]) {
                display.text(Text.parse(Messages.msg("poker.arena.strength.folded")));
                continue;
            }
            List<PokerCard> known = new ArrayList<>(board);
            known.add(hole[side][0]);
            known.add(hole[side][1]);
            display.text(Text.parse(Messages.msg("poker.arena.strength.current",
                    "hand", PokerHandEvaluator.describeCurrent(known))));
        }
        refreshVisibility();
    }

    void prepareNextHand(PokerCard[][] hole) {
        privateCardsPublic = false;
        prepareBettingRound();
        restoreTableForNextHand();
        syncBoard(List.of(), List.of());
        setLamps(false);
        for (Player online : plugin.getServer().getOnlinePlayers()) hidePrivateFrom(online);
        for (int side = 0; side < players.length; side++) {
            for (int card = 0; card < 2; card++) {
                TextDisplay display = holeCards[side][card];
                if (display == null || !display.isValid()) continue;
                PokerCard value = hole[side][card];
                display.text(Text.parse(value == null
                        ? Messages.msg("poker.arena.hole.left") : cardText(value)));
            }
            Player owner = plugin.getServer().getPlayer(players[side]);
            if (owner != null && snapshots.containsKey(players[side])) {
                for (TextDisplay display : holeCards[side]) owner.showEntity(plugin, display);
                owner.showEntity(plugin, handStrength[side]);
            }
        }
    }

    /** 每个下注轮重新按当前余额计算一套可下注筹码，不沿用上一轮的手动拆分布局。 */
    void prepareBettingRound() {
        for (int side = 0; side < players.length; side++) {
            compactChips[side] = false;
            chipLayouts.get(side).clear();
            chipLayoutDirty[side] = true;
        }
    }

    void revealAllHoleCards() {
        privateCardsPublic = true;
        for (UUID viewerId : players) {
            Player viewer = plugin.getServer().getPlayer(viewerId);
            if (viewer == null || !snapshots.containsKey(viewerId)) continue;
            for (TextDisplay[] pair : holeCards) {
                for (TextDisplay display : pair) {
                    if (display != null && display.isValid()) viewer.showEntity(plugin, display);
                }
            }
        }
    }

    void celebrateWinners(List<UUID> winners) {
        setLamps(true);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> setLamps(true), 25L);
        for (UUID id : winners) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && snapshots.containsKey(id)) {
                player.setGlowing(true);
                world.strikeLightningEffect(player.getLocation());
                continue;
            }
            int side = side(id);
            if (side >= 0 && botPlayers[side] != null && botPlayers[side].isValid()) {
                botPlayers[side].setGlowing(true);
                world.strikeLightningEffect(botPlayers[side].getLocation());
            }
        }
    }

    void clearCelebration() {
        setLamps(false);
        for (UUID id : players) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) player.setGlowing(false);
        }
        for (Villager bot : botPlayers) if (bot != null && bot.isValid()) bot.setGlowing(false);
    }

    void syncPlayers(int[] stack, int[] roundBet, int[] pendingBet, boolean[] folded, boolean[] seated, int dealer,
                     int smallBlind, int bigBlind, int actor, int[] wins, double[] winRates) {
        for (int side = 0; side < players.length; side++) {
            Player player = plugin.getServer().getPlayer(players[side]);
            if (player != null && snapshots.containsKey(players[side])) player.setGlowing(!folded[side]);
            if (botPlayers[side] != null && botPlayers[side].isValid()) botPlayers[side].setGlowing(!folded[side]);
            TextDisplay display = playerStatus[side];
            if (display == null || !display.isValid()) continue;
            if (EMPTY_SEAT_NAME.equals(names[side])) {
                display.text(Text.parse(Messages.msg("poker.arena.seat.empty")));
                renderBetZone(side, false);
                continue;
            }
            String role = side == dealer ? Messages.msg("poker.arena.role.dealer")
                    : side == smallBlind ? Messages.msg("poker.arena.role.small-blind")
                    : side == bigBlind ? Messages.msg("poker.arena.role.big-blind") : "";
            String state = seated[side] && folded[side] && stack[side] == 0
                    && roundBet[side] == 0 && pendingBet[side] == 0
                    ? Messages.msg("poker.arena.state.joining-next")
                    : folded[side] ? Messages.msg("poker.arena.state.folded")
                    : stack[side] == 0 ? Messages.msg("poker.arena.state.all-in")
                    : side == actor ? Messages.msg("poker.arena.state.acting")
                    : Messages.msg("poker.arena.state.waiting");
            int available = Math.max(0, stack[side] - pendingBet[side]);
            display.text(Text.parse(Messages.msg("poker.arena.seat.status",
                    "role", role, "state", state, "chips", available, "round", roundBet[side],
                    "pending", pendingBet[side] > 0
                            ? Messages.msg("poker.arena.seat.status-pending", "pending", pendingBet[side]) : "",
                    "wins", wins[side],
                    "winrate", String.format(Locale.ROOT, "%.1f%%", winRates[side]),
                    "player", names[side])));
            renderBetZone(side, side == actor);
            TextDisplay label = betZoneLabels[side];
            if (label != null && label.isValid()) {
                String chips = Messages.msg("poker.arena.bet-zone.chips", "chips", available)
                        + (pendingBet[side] > 0
                                ? Messages.msg("poker.arena.bet-zone.pending", "pending", pendingBet[side]) : "");
                label.text(Text.parse((side == actor
                        ? Messages.msg("poker.arena.bet-zone.active", "player", names[side])
                        : Messages.msg("poker.arena.bet-zone.label", "player", names[side])) + chips));
            }
            renderPendingBet(side, pendingBet[side]);
        }
    }

    void syncCenter(int actor, int seconds, int[] stack, int[] roundBet, int[] pendingBet,
                    int[] contribution, int[] queuedRebuy, boolean[] seated, int pot,
                    int carryLimit, boolean handActive) {
        if (tableStatus == null || !tableStatus.isValid()) return;
        String timeColor = seconds <= 5 ? "<red>" : seconds <= 10 ? "<gold>" : "<green>";
        StringBuilder text = new StringBuilder(
                Messages.msg("poker.arena.table.title", "brand", plugin.brand())).append('\n');
        if (actor >= 0 && actor < players.length) {
            text.append(Messages.msg("poker.arena.table.acting",
                    "color", timeColor, "seconds", seconds, "player", names[actor]));
        } else {
            text.append(Messages.msg("poker.arena.table.settling"));
        }
        int pendingTotal = 0;
        for (int side = 0; side < players.length; side++) {
            if (!seated[side] && contribution[side] == 0 && queuedRebuy[side] == 0 && stack[side] == 0) continue;
            pendingTotal += pendingBet[side];
            text.append(Messages.msg("poker.arena.table.seat-line",
                    "round", roundBet[side],
                    "pending", pendingBet[side] > 0
                            ? Messages.msg("poker.arena.table.seat-pending", "pending", pendingBet[side]) : "",
                    "total", contribution[side], "player", names[side]));
        }
        text.append(Messages.msg("poker.arena.table.pot", "pot", pot + pendingTotal))
                .append(pendingTotal > 0 ? Messages.msg("poker.arena.table.pot-breakdown",
                        "pot", pot, "pending", pendingTotal) : "");
        tableStatus.text(Text.parse(text.toString()));
        // 待确认筹码在各自下注区显示；绿色确认后才汇入中央筹码山。
        renderChipMountain(pot);
        syncAtm(stack, pendingBet, contribution, queuedRebuy, seated, carryLimit, handActive);
    }

    void syncCenterMessage(String headline, String detail, int pot) {
        if (tableStatus == null || !tableStatus.isValid()) return;
        tableStatus.text(Text.parse(Messages.msg("poker.arena.table.message",
                "brand", plugin.brand(), "pot", pot, "headline", headline, "detail", detail)));
        renderChipMountain(pot);
    }

    void syncInventories(int[] stack, int[] pendingBet, PokerCard[][] hole, boolean[] folded) {
        for (int side = 0; side < players.length; side++) {
            Player player = plugin.getServer().getPlayer(players[side]);
            if (player == null || !snapshots.containsKey(players[side])) continue;
            ensureItem(player, 0, handCard(hole[side]));
            ensureItem(player, 1, Items.item(Material.YELLOW_DYE,
                    Messages.msg("poker.arena.item.call.name"),
                    Messages.msg("poker.arena.item.call.hint"),
                    Messages.msg("poker.arena.item.call.note")));
            ensureItem(player, 2, Items.item(Material.LIME_DYE,
                    Messages.msg("poker.arena.item.confirm.name"),
                    Messages.msg("poker.arena.item.confirm.hint")));
            int available = Math.max(0, stack[side] - pendingBet[side]);
            Map<PokerChips.Denomination, Integer> layout = chipLayouts.get(side);
            if (chipLayoutDirty[side] || chipValue(layout) != available) {
                layout = new LinkedHashMap<>(compactChips[side] || available < PokerChips.MIN_OPENING_COUNT
                        ? PokerChips.breakdown(available) : PokerChips.playableBreakdown(available));
                chipLayouts.set(side, layout);
                clearChipItems(player);
                putChips(player, layout);
                chipLayoutDirty[side] = false;
            }
        }
    }

    private void ensureItem(Player player, int slot, ItemStack desired) {
        ItemStack current = player.getInventory().getItem(slot);
        if (current == null || current.getAmount() != desired.getAmount() || !current.isSimilar(desired)) {
            player.getInventory().setItem(slot, desired);
        }
    }

    /** 下注放块事件会被取消；只更新内部布局，并在下一 tick 从原手持堆扣除一枚。 */
    boolean consumePlacedChip(Player player, Material material) {
        int side = side(player.getUniqueId());
        PokerChips.Denomination denomination = denomination(material);
        if (side < 0 || denomination == null) return false;
        Map<PokerChips.Denomination, Integer> layout = chipLayouts.get(side);
        int count = layout.getOrDefault(denomination, 0);
        if (count <= 0) {
            chipLayoutDirty[side] = true;
            return false;
        }
        if (count == 1) layout.remove(denomination); else layout.put(denomination, count - 1);
        int heldSlot = player.getInventory().getHeldItemSlot();
        plugin.getServer().getScheduler().runTask(plugin,
                () -> decrementOneChip(player, heldSlot, material));
        return true;
    }

    private void decrementOneChip(Player player, int preferredSlot, Material material) {
        if (!snapshots.containsKey(player.getUniqueId())) return;
        ItemStack item = player.getInventory().getItem(preferredSlot);
        int slot = preferredSlot;
        if (item == null || item.getType() != material || PokerChips.value(item.getType()) <= 0) {
            slot = -1;
            for (int candidate = 3; candidate < 36; candidate++) {
                ItemStack candidateItem = player.getInventory().getItem(candidate);
                if (candidateItem != null && candidateItem.getType() == material
                        && PokerChips.value(candidateItem.getType()) > 0) {
                    item = candidateItem;
                    slot = candidate;
                    break;
                }
            }
        }
        if (slot < 0 || item == null) return;
        if (item.getAmount() <= 1) player.getInventory().setItem(slot, null);
        else {
            item.setAmount(item.getAmount() - 1);
            player.getInventory().setItem(slot, item);
        }
    }

    private void putChips(Player player, Map<PokerChips.Denomination, Integer> breakdown) {
        int[] slots = new int[33];
        for (int index = 0; index < slots.length; index++) slots[index] = index + 3;
        int slotIndex = 0;
        for (Map.Entry<PokerChips.Denomination, Integer> entry : breakdown.entrySet()) {
            int remaining = entry.getValue();
            while (remaining > 0 && slotIndex < slots.length) {
                int count = Math.min(64, remaining);
                ItemStack item = chipItem(entry.getKey(), count);
                player.getInventory().setItem(slots[slotIndex++], item);
                remaining -= count;
            }
        }
    }

    boolean splitChip(Player player, Material material) {
        int side = side(player.getUniqueId());
        if (!snapshots.containsKey(player.getUniqueId()) || side < 0) return false;
        PokerChips.Split split = PokerChips.split(material);
        if (split == null) {
            Text.send(player, Messages.msg(PokerChips.value(material) > 0
                    ? "poker.arena.chip.split-smallest"
                    : "poker.arena.chip.split-need-chip"));
            return PokerChips.value(material) > 0;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != material || held.getAmount() <= 0) return false;
        ItemStack replacement = chipItem(split.target(), split.count());
        int heldSlot = player.getInventory().getHeldItemSlot();
        int capacity = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack existing = contents[slot];
            if (slot == heldSlot && held.getAmount() == 1) existing = null;
            if (existing == null || existing.getType().isAir()) capacity += replacement.getMaxStackSize();
            else if (existing.isSimilar(replacement)) capacity += replacement.getMaxStackSize() - existing.getAmount();
        }
        if (capacity < split.count()) {
            Text.send(player, Messages.msg("poker.arena.chip.split-no-space"));
            return true;
        }
        if (held.getAmount() == 1) player.getInventory().setItemInMainHand(null);
        else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItemInMainHand(held);
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(replacement);
        if (!leftovers.isEmpty()) return false;
        Map<PokerChips.Denomination, Integer> layout = chipLayouts.get(side);
        PokerChips.Denomination source = denomination(material);
        if (source != null && layout.getOrDefault(source, 0) > 0) {
            int sourceCount = layout.get(source) - 1;
            if (sourceCount == 0) layout.remove(source); else layout.put(source, sourceCount);
            layout.merge(split.target(), split.count(), Integer::sum);
        }
        compactChips[side] = false;
        chipLayoutDirty[side] = false;
        Text.send(player, Messages.msg("poker.arena.chip.split-done",
                "count", split.count(), "from", materialName(material), "to", split.target().display()));
        return true;
    }

    void mergeChips(Player player, int amount) {
        int side = side(player.getUniqueId());
        if (side < 0 || !snapshots.containsKey(player.getUniqueId())) return;
        Map<PokerChips.Denomination, Integer> compact = new LinkedHashMap<>(PokerChips.breakdown(amount));
        if (requiredSlots(compact) > 33) {
            Text.send(player, Messages.msg("poker.arena.chip.merge-overflow"));
            return;
        }
        compactChips[side] = true;
        chipLayoutDirty[side] = false;
        chipLayouts.set(side, compact);
        clearChipItems(player);
        putChips(player, compact);
        int pieces = compact.values().stream().mapToInt(Integer::intValue).sum();
        Text.send(player, Messages.msg("poker.arena.chip.merge-done",
                "pieces", pieces, "summary", PokerChips.summary(amount)));
    }

    private void clearChipItems(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && PokerChips.value(item.getType()) > 0) player.getInventory().setItem(slot, null);
        }
    }

    private int chipValue(Map<PokerChips.Denomination, Integer> layout) {
        int value = 0;
        for (Map.Entry<PokerChips.Denomination, Integer> entry : layout.entrySet()) {
            value += entry.getKey().value() * entry.getValue();
        }
        return value;
    }

    private int requiredSlots(Map<PokerChips.Denomination, Integer> layout) {
        int slots = 0;
        for (int count : layout.values()) slots += (count + 63) / 64;
        return slots;
    }

    private PokerChips.Denomination denomination(Material material) {
        for (PokerChips.Denomination denomination : PokerChips.denominations()) {
            if (denomination.material() == material) return denomination;
        }
        return null;
    }

    private ItemStack chipItem(PokerChips.Denomination denomination, int count) {
        ItemStack item = Items.item(denomination.material(),
                Messages.msg("poker.arena.item.chip.name",
                        "value", denomination.value(), "chip", denomination.display()),
                Messages.msg("poker.arena.item.chip.place"),
                Messages.msg(denomination.value() > 1
                        ? "poker.arena.item.chip.split" : "poker.arena.item.chip.smallest"),
                Messages.msg("poker.arena.item.chip.merge"));
        item.setAmount(count);
        return item;
    }

    private ItemStack handCard(PokerCard[] cards) {
        if (cards == null || cards.length < 2 || cards[0] == null || cards[1] == null) {
            return Items.item(Material.PAPER, Messages.msg("poker.arena.item.hand.none.name"),
                    Messages.msg("poker.arena.item.hand.none.hint"));
        }
        return Items.item(Material.PAPER, Messages.msg("poker.arena.item.hand.dealt.name",
                        "first", cardText(cards[0]), "second", cardText(cards[1])),
                Messages.msg("poker.arena.item.hand.dealt.hint"),
                Messages.msg("poker.arena.item.hand.dealt.note"));
    }


    private String materialName(Material material) {
        for (PokerChips.Denomination denomination : PokerChips.denominations()) {
            if (denomination.material() == material) return denomination.display();
        }
        return material.name();
    }

    private void renderChipMountain(int amount) {
        // 场上筹码始终实时合成为尽可能高的面额。
        Map<PokerChips.Denomination, Integer> breakdown = PokerChips.breakdown(amount);
        List<PokerChips.Denomination> denominations = PokerChips.denominations();
        int chipBase = chipBaseDy();
        int pileHeight = visibleChipPileHeight();
        for (int index = 0; index < CHIP_PILES.length; index++) {
            int[] anchor = CHIP_PILES[index];
            for (int dx = 0; dx < CHIP_PILE_WIDTH; dx++) {
                for (int dz = 0; dz < CHIP_PILE_DEPTH; dz++) {
                    for (int dy = chipBase; dy < chipBase + pileHeight; dy++) {
                        set(anchor[0] + dx, dy, anchor[1] + dz, Material.AIR);
                    }
                }
            }
            PokerChips.Denomination denomination = denominations.get(index);
            int count = breakdown.getOrDefault(denomination, 0);
            // 每种面额最多显示 3×2×5 的实体筹码山；更大的数量仍由全息精确显示且不限制下注。
            int layerSize = CHIP_PILE_WIDTH * CHIP_PILE_DEPTH;
            int shown = Math.min(layerSize * pileHeight, count);
            for (int piece = 0; piece < shown; piece++) {
                int layer = piece / layerSize;
                int within = piece % layerSize;
                set(anchor[0] + within % CHIP_PILE_WIDTH, chipBase + layer,
                        anchor[1] + within / CHIP_PILE_WIDTH,
                        denomination.material());
            }
            TextDisplay label = chipPileLabels[index];
            if (label != null && label.isValid()) {
                label.text(Text.parse(count == 0 ? "" : Messages.msg("poker.arena.chip-pile.label",
                        "count", count, "chip", denomination.display())));
            }
        }
    }

    /** 下一手前修复桌面和下注区，避免上一手的实体筹码槽留下空洞。 */
    private void restoreTableForNextHand() {
        paintTableAndBettingZones();
        int chipBase = chipBaseDy();
        int pileHeight = visibleChipPileHeight();
        for (int[] anchor : CHIP_PILES) {
            for (int dx = 0; dx < CHIP_PILE_WIDTH; dx++) {
                for (int dz = 0; dz < CHIP_PILE_DEPTH; dz++) {
                    for (int dy = chipBase; dy < chipBase + pileHeight; dy++) {
                        set(anchor[0] + dx, dy, anchor[1] + dz, Material.AIR);
                    }
                }
            }
        }
        for (int side = 0; side < players.length; side++) {
            renderPendingBet(side, 0);
            renderBetZone(side, false);
        }
    }

    private void renderPendingBet(int side, int amount) {
        int baseX = BET_ZONE[side][0];
        int baseZ = BET_ZONE[side][1];
        int chipBase = pendingChipBaseDy();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = chipBase; dy < chipBase + 3; dy++) set(baseX + dx, dy, baseZ + dz, Material.AIR);
            }
        }
        if (amount <= 0) return;
        int position = 0;
        for (Map.Entry<PokerChips.Denomination, Integer> entry : PokerChips.breakdown(amount).entrySet()) {
            for (int count = 0; count < entry.getValue() && position < 27; count++, position++) {
                int layer = position / 9;
                int within = position % 9;
                set(baseX - 1 + within % 3, chipBase + layer, baseZ - 1 + within / 3,
                        entry.getKey().material());
            }
        }
    }

    private void renderBetZone(int side, boolean active) {
        int baseX = BET_ZONE[side][0];
        int baseZ = BET_ZONE[side][1];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean center = dx == 0 && dz == 0;
                PokerArenaStyle.Palette palette = style.palette();
                if (palette.hasOverlay()) {
                    set(baseX + dx, 0, baseZ + dz, Math.floorMod(dx + dz, 2) == 0
                            ? palette.tableGlowA() : palette.tableGlowB());
                    set(baseX + dx, 1, baseZ + dz, center ? SEAT_GLASS[side]
                            : active ? Material.LIME_STAINED_GLASS : palette.tableOverlay());
                } else {
                    set(baseX + dx, 0, baseZ + dz, center ? SEAT_BLOCKS[side]
                            : active ? palette.zoneActive() : palette.zoneIdle());
                    set(baseX + dx, 1, baseZ + dz, Material.AIR);
                }
            }
        }
    }

    private int chipBaseDy() { return style.chipBaseDy(); }

    private int pendingChipBaseDy() { return style.chipBaseDy(); }

    private int visibleChipPileHeight() {
        return style.palette().hasOverlay() ? CHIP_PILE_HEIGHT - 1 : CHIP_PILE_HEIGHT;
    }

    boolean inBetZone(int side, Location location) {
        if (side < 0 || side >= BET_ZONE.length || location == null || !world.equals(location.getWorld())) return false;
        int relativeX = location.getBlockX() - centerX;
        int relativeZ = location.getBlockZ() - centerZ;
        return location.getBlockY() >= y + pendingChipBaseDy()
                && location.getBlockY() <= y + pendingChipBaseDy() + 2
                && Math.abs(relativeX - BET_ZONE[side][0]) <= 1
                && Math.abs(relativeZ - BET_ZONE[side][1]) <= 1;
    }

    private void decorateCasinoColumn(int relativeX, int relativeZ, boolean diamond) {
        set(relativeX, 1, relativeZ, Material.CHISELED_QUARTZ_BLOCK);
        set(relativeX, 2, relativeZ, Material.QUARTZ_PILLAR);
        set(relativeX, 3, relativeZ, diamond ? Material.DIAMOND_BLOCK : Material.EMERALD_BLOCK);
        set(relativeX, 4, relativeZ, Material.AMETHYST_BLOCK);
        set(relativeX, 5, relativeZ, Material.GOLD_BLOCK);
        set(relativeX, 6, relativeZ, Material.SEA_LANTERN);
    }

    private void buildAtm() {
        for (int z = -2; z <= 2; z++) {
            set(18, 1, z, Material.GILDED_BLACKSTONE);
            set(18, 2, z, Material.GOLD_BLOCK);
            set(18, 3, z, Math.abs(z) == 2 ? Material.AMETHYST_BLOCK : Material.BLACK_CONCRETE);
            set(18, 4, z, Material.CHISELED_QUARTZ_BLOCK);
            set(18, 5, z, z == 0 ? Material.SEA_LANTERN : Material.GOLD_BLOCK);
        }
        set(17, 1, -1, Material.POLISHED_BLACKSTONE);
        set(17, 1, 0, Material.POLISHED_BLACKSTONE);
        set(17, 1, 1, Material.POLISHED_BLACKSTONE);
        set(17, 2, 0, Material.EMERALD_BLOCK);
        placeAtmButton();
    }

    private void syncAtm(int[] stack, int[] pendingBet, int[] contribution, int[] queuedRebuy,
                         boolean[] seated, int carryLimit, boolean handActive) {
        if (atmStatus == null || !atmStatus.isValid()) return;
        int state = java.util.Arrays.hashCode(stack);
        state = 31 * state + java.util.Arrays.hashCode(pendingBet);
        state = 31 * state + java.util.Arrays.hashCode(contribution);
        state = 31 * state + java.util.Arrays.hashCode(queuedRebuy);
        state = 31 * state + java.util.Arrays.hashCode(seated);
        long now = System.currentTimeMillis();
        if (state == lastAtmState && now < nextAtmRefresh) return;
        lastAtmState = state;
        nextAtmRefresh = now + 5000L;
        StringBuilder text = new StringBuilder(Messages.msg("poker.arena.atm.header",
                "brand", plugin.brand(), "limit", carryLimit));
        for (int side = 0; side < players.length; side++) {
            if (!seated[side]) continue;
            boolean bot = CasinoBot.namedBot(names[side]);
            double wallet = bot ? 0.0 : plugin.economy().balance(Bukkit.getOfflinePlayer(players[side]));
            int available = Math.max(0, stack[side] - pendingBet[side]);
            int handBet = Math.max(0, contribution[side] + pendingBet[side]);
            int room = PokerMoney.topUpRoom(carryLimit, stack[side], contribution[side],
                    queuedRebuy[side], handActive);
            text.append(Messages.msg("poker.arena.atm.line",
                    "wallet", bot ? Messages.msg("poker.arena.atm.bot-wallet")
                            : Messages.msg("poker.arena.atm.wallet",
                                    "balance", plugin.economy().format(wallet)),
                    "chips", available, "hand", handBet,
                    "queued", queuedRebuy[side] > 0
                            ? Messages.msg("poker.arena.atm.queued", "amount", queuedRebuy[side]) : "",
                    "room", room, "player", names[side]));
        }
        atmStatus.text(Text.parse(text.toString()));
    }

    void flashLamps() {
        for (int step = 0; step < 6; step++) {
            boolean lit = step % 2 == 0;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> setLamps(lit), step * 4L);
        }
    }

    void lightningTable() {
        if (closed) return;
        world.strikeLightningEffect(new Location(world, centerX + 0.5, y + 2.0, centerZ + 0.5));
    }

    void punishFold(int side) {
        if (side < 0 || side >= players.length || closed) return;
        Player player = plugin.getServer().getPlayer(players[side]);
        if (player != null && snapshots.containsKey(players[side])) {
            player.setGlowing(false);
            world.strikeLightningEffect(player.getLocation());
        } else if (botPlayers[side] != null && botPlayers[side].isValid()) {
            botPlayers[side].setGlowing(false);
            world.strikeLightningEffect(botPlayers[side].getLocation());
        }
    }

    ControlButton controlButton(Block block) {
        if (block == null || !world.equals(block.getWorld()) || block.getY() != y + 2) return null;
        int relativeX = block.getX() - centerX;
        int relativeZ = block.getZ() - centerZ;
        for (int side = 0; side < players.length; side++) {
            for (int action = 0; action < CONTROL_ACTIONS.length; action++) {
                if (CONTROL_BUTTONS[side][action][0] == relativeX
                        && CONTROL_BUTTONS[side][action][1] == relativeZ) {
                    return new ControlButton(side, CONTROL_ACTIONS[action]);
                }
            }
        }
        return null;
    }

    boolean isExitButton(int side, Block block) {
        if (side < 0 || side >= players.length || block == null || !world.equals(block.getWorld())) return false;
        return block.getX() == centerX + EXIT_BUTTON[side][0]
                && block.getY() == y + 2
                && block.getZ() == centerZ + EXIT_BUTTON[side][1];
    }

    boolean isAtm(Block block) {
        return block != null && world.equals(block.getWorld())
                && block.getX() == centerX + 17
                && (block.getY() == y + 2 || block.getY() == y + 3)
                && block.getZ() == centerZ;
    }

    /** ATM 绿宝石上方也放一个石按钮，和座位按钮保持一致的操作方式。 */
    private void placeAtmButton() {
        set(17, 3, 0, Material.POLISHED_BLACKSTONE_BUTTON);
        Block button = world.getBlockAt(centerX + 17, y + 3, centerZ);
        if (button.getBlockData() instanceof Switch data) {
            data.setFace(Switch.Face.FLOOR);
            button.setBlockData(data, false);
        }
    }

    void syncExitButton(int side, boolean enabled) {
        if (side < 0 || side >= players.length) return;
        TextDisplay label = exitButtonLabels[side];
        if (label != null && label.isValid()) {
            label.text(Text.parse(Messages.msg(enabled
                    ? "poker.arena.exit.armed" : "poker.arena.exit.idle")));
        }
        set(EXIT_BUTTON[side][0], 1, EXIT_BUTTON[side][1],
                enabled ? Material.RED_CONCRETE : Material.GOLD_BLOCK);
    }

    private void setLamps(boolean lit) {
        if (closed) return;
        for (Block lamp : lamps) {
            if (lamp.getType() != Material.REDSTONE_LAMP || !(lamp.getBlockData() instanceof Lightable data)) continue;
            data.setLit(lit);
            lamp.setBlockData(data, false);
        }
    }

    void hidePrivateFrom(Player viewer) {
        int viewerSide = side(viewer.getUniqueId());
        boolean seeEveryHand = privateCardsPublic || plugin.handPeekEnabled(viewer);
        // floppeek 管理员看专用公牌全息，同时隐藏公开版，避免同一位置两段文字重叠。
        boolean seeBoardAhead = plugin.boardPeekEnabled(viewer);
        for (int card = 0; card < publicCards.length; card++) {
            swapVisibility(viewer, publicCards[card], peekCards[card], seeBoardAhead);
        }
        swapVisibility(viewer, boardStatus, peekBoardStatus, seeBoardAhead);
        for (int side = 0; side < holeCards.length; side++) {
            for (TextDisplay display : holeCards[side]) {
                if (display == null || !display.isValid()) continue;
                if (side == viewerSide || seeEveryHand) viewer.showEntity(plugin, display);
                else viewer.hideEntity(plugin, display);
            }
            TextDisplay strength = handStrength[side];
            if (strength != null && strength.isValid()) {
                if (side == viewerSide) viewer.showEntity(plugin, strength);
                else viewer.hideEntity(plugin, strength);
            }
        }
    }

    void release(Player player) {
        PlayerSnapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot == null) return;
        player.setGlowing(false);
        player.teleport(snapshot.location());
        snapshot.restore(player);
    }

    boolean protects(UUID player) { return snapshots.containsKey(player); }

    boolean contains(Location location) {
        return location != null && world.equals(location.getWorld())
                && location.getY() >= y && location.getY() <= y + 7
                // 必须按真实轮廓判定：非矩形轮廓下外接矩形里有大片没有地板的格子，
                // 若只用矩形，开放式/露天场地的玩家能走出边缘掉进虚空，
                // 而移动回弹加上免伤会把人永久卡在半空。
                && shape.inside(location.getBlockX() - centerX, location.getBlockZ() - centerZ,
                        roomX, roomZ);
    }

    void close() {
        if (closed) return;
        closed = true;
        clearCelebration();
        for (UUID id : List.copyOf(snapshots.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) release(player);
            else snapshots.remove(id);
        }
        for (Entity entity : entities) if (entity != null && entity.isValid()) entity.remove();
        entities.clear();
        arenaWorld.clearBox(centerX, centerZ, roomX + 1, roomZ + 1, y, y + 8);
        arenaWorld.release(slot);
    }

    private void rollback() {
        for (UUID id : List.copyOf(snapshots.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) release(player);
        }
        for (Entity entity : entities) if (entity != null && entity.isValid()) entity.remove();
        entities.clear();
        arenaWorld.clearBox(centerX, centerZ, roomX + 1, roomZ + 1, y, y + 8);
    }

    private TextDisplay spawnText(Location location, String text, int width) {
        // 房间公共全息使用原生可见性。旧版设为 false 后只调用一次 showEntity，
        // 玩家刚跨世界传送时 Paper 26.2 可能不会把显示实体发送给客户端。
        return spawnText(location, text, width, true);
    }

    private TextDisplay spawnPrivateText(Location location, String text, int width) {
        // 先正常生成，spawnDisplays 会在同一 tick 内对非主人隐藏，不会依赖默认隐藏实体的追踪时机。
        return spawnText(location, text, width, true);
    }

    private TextDisplay spawnRoomText(Location location, String text, int width) {
        return spawnText(location, text, width);
    }

    private TextDisplay spawnText(Location location, String text, int width, boolean visibleByDefault) {
        TextDisplay display = world.spawn(location, TextDisplay.class, spawned -> {
            spawned.addScoreboardTag(DISPLAY_TAG);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setSeeThrough(true);
            spawned.setShadowed(true);
            spawned.setLineWidth(width);
            spawned.setViewRange(2.0F);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setPersistent(false);
            spawned.setVisibleByDefault(visibleByDefault);
            spawned.text(Text.parse(text));
        });
        entities.add(display);
        return display;
    }

    private void scaleText(TextDisplay display, float scale) {
        org.bukkit.util.Transformation transformation = display.getTransformation();
        transformation.getScale().mul(scale);
        display.setTransformation(transformation);
    }

    /** 保证公共全息可见，并重新应用私人底牌的逐玩家权限。 */
    private void refreshVisibility() {
        if (closed) return;
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            hidePrivateFrom(online);
        }
    }

    private String cardText(PokerCard card) {
        return (card.suit().red() ? "<red>" : "<white>") + "<bold>[ " + card.display() + " ]</bold>";
    }

    /** 同一位置的公开版与管理员版全息二选一，保证任何时刻只有一段文字对该玩家可见。 */
    private void swapVisibility(Player viewer, TextDisplay open, TextDisplay peek, boolean usePeek) {
        if (open != null && open.isValid()) {
            if (usePeek) viewer.hideEntity(plugin, open);
            else viewer.showEntity(plugin, open);
        }
        if (peek != null && peek.isValid()) {
            if (usePeek) viewer.showEntity(plugin, peek);
            else viewer.hideEntity(plugin, peek);
        }
    }

    /** 尚未发出的公牌用圆括号和紫色标注，避免管理员把它当成已经公开的牌。 */
    private String peekCardText(PokerCard card) {
        return "<light_purple><bold>( " + card.display() + " )</bold></light_purple>";
    }

    private Location seat(int side) {
        Location location = switch (side) {
            case 0 -> new Location(world, centerX + 6.5, y + 2.0, centerZ + 12.5);
            case 1 -> new Location(world, centerX + 14.5, y + 2.0, centerZ - 6.5);
            case 2 -> new Location(world, centerX + 14.5, y + 2.0, centerZ + 6.5);
            case 3 -> new Location(world, centerX - 5.5, y + 2.0, centerZ + 12.5);
            case 4 -> new Location(world, centerX - 13.5, y + 2.0, centerZ + 6.5);
            default -> new Location(world, centerX - 13.5, y + 2.0, centerZ - 6.5);
        };
        Location dealer = dealerLocation();
        double deltaX = dealer.getX() - location.getX();
        double deltaZ = dealer.getZ() - location.getZ();
        location.setYaw((float) Math.toDegrees(Math.atan2(-deltaX, deltaZ)));
        location.setPitch(5.0F);
        return location;
    }

    private Location dealerLocation() {
        // 北侧墙内，无玩家座位位于荷官背后；yaw=0 令荷官朝南面对整张牌桌。
        return new Location(world, centerX + 0.5, y + 2.0, centerZ - 15.0, 0.0F, 0.0F);
    }

    private Location statusLocation(int side) {
        Location seat = seat(side);
        double x = seat.getX() + (centerX + 0.5 - seat.getX()) * 0.18;
        double z = seat.getZ() + (centerZ + 0.5 - seat.getZ()) * 0.18;
        return new Location(world, x, y + 4.4, z);
    }

    private Location holeLocation(int side, int card) {
        Location seat = seat(side);
        double towardX = (centerX + 0.5 - seat.getX()) * 0.22;
        double towardZ = (centerZ + 0.5 - seat.getZ()) * 0.22;
        boolean northSouth = side == 0 || side == 3;
        double offsetX = northSouth ? (card == 0 ? -0.8 : 0.8) : 0;
        double offsetZ = northSouth ? 0 : (card == 0 ? -0.8 : 0.8);
        return new Location(world, seat.getX() + towardX + offsetX, y + 3.0,
                seat.getZ() + towardZ + offsetZ);
    }

    private Location handStrengthLocation(int side) {
        Location seat = seat(side);
        double towardX = (centerX + 0.5 - seat.getX()) * 0.22;
        double towardZ = (centerZ + 0.5 - seat.getZ()) * 0.22;
        return new Location(world, seat.getX() + towardX, y + 3.7, seat.getZ() + towardZ);
    }

    private Location betZoneLabelLocation(int side) {
        return new Location(world, centerX + BET_ZONE[side][0] + 0.5, y + 5.35,
                centerZ + BET_ZONE[side][1] + 0.5);
    }

    private Location exitButtonLabelLocation(int side) {
        return new Location(world, centerX + EXIT_BUTTON[side][0] + 0.5, y + 3.35,
                centerZ + EXIT_BUTTON[side][1] + 0.5);
    }

    private Location controlButtonLabelLocation(int side, int action) {
        return new Location(world, centerX + CONTROL_BUTTONS[side][action][0] + 0.5, y + 3.25,
                centerZ + CONTROL_BUTTONS[side][action][1] + 0.5);
    }

    private int side(UUID player) {
        for (int side = 0; side < players.length; side++) if (players[side].equals(player)) return side;
        return -1;
    }

    private void set(int relativeX, int dy, int relativeZ, Material material) {
        world.getBlockAt(centerX + relativeX, y + dy, centerZ + relativeZ).setType(material, false);
    }
}
