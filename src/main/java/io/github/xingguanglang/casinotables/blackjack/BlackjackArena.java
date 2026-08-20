package io.github.xingguanglang.casinotables.blackjack;

import io.github.xingguanglang.casinotables.arena.ArenaBlocks;
import io.github.xingguanglang.casinotables.CasinoTablesPlugin;
import io.github.xingguanglang.casinotables.Items;
import io.github.xingguanglang.casinotables.Messages;
import io.github.xingguanglang.casinotables.Text;
import io.github.xingguanglang.casinotables.arena.ArenaShape;
import io.github.xingguanglang.casinotables.arena.ArenaWorld;
import io.github.xingguanglang.casinotables.arena.PlayerSnapshot;
import io.github.xingguanglang.casinotables.poker.PokerArenaStyle;
import io.github.xingguanglang.casinotables.poker.PokerCard;
import io.github.xingguanglang.casinotables.poker.PokerChips;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class BlackjackArena {
    static final String ENTITY_TAG = io.github.xingguanglang.casinotables.arena.ArenaTags.BLACKJACK_ENTITY;
    /** 快捷栏前五格是按钮，实体筹码从第六格开始，保证手里能直接拿到筹码。 */
    private static final int RESERVED_SLOTS = 5;
    private static final int ROOM_X = 20;
    private static final int ROOM_Z = 18;
    private static final int[][] SEAT = {
            {6, 12}, {15, -6}, {15, 6}, {-6, 12}, {-15, 6}, {-15, -6}
    };
    /** 每个座位十个连续按钮，顺序与 ACTIONS 一致。银行占用 x=±18 的 z=-2..2，这里刻意避开。 */
    private static final int[][][] CONTROLS = {
            {{1, 15}, {2, 15}, {3, 15}, {4, 15}, {5, 15}, {6, 15}, {7, 15}, {8, 15}, {9, 15}, {10, 15}},
            {{18, -12}, {18, -11}, {18, -10}, {18, -9}, {18, -8}, {18, -7}, {18, -6}, {18, -5}, {18, -4}, {18, -3}},
            {{18, 3}, {18, 4}, {18, 5}, {18, 6}, {18, 7}, {18, 8}, {18, 9}, {18, 10}, {18, 11}, {18, 12}},
            {{-10, 15}, {-9, 15}, {-8, 15}, {-7, 15}, {-6, 15}, {-5, 15}, {-4, 15}, {-3, 15}, {-2, 15}, {-1, 15}},
            {{-18, 3}, {-18, 4}, {-18, 5}, {-18, 6}, {-18, 7}, {-18, 8}, {-18, 9}, {-18, 10}, {-18, 11}, {-18, 12}},
            {{-18, -12}, {-18, -11}, {-18, -10}, {-18, -9}, {-18, -8}, {-18, -7}, {-18, -6}, {-18, -5}, {-18, -4}, {-18, -3}}
    };
    /** 每个座位面前牌桌上的 3×3 下注区，玩家把实体筹码放进去决定注额。 */
    private static final int[][] BET_ZONE = {
            {4, 7}, {9, -4}, {9, 4}, {-4, 7}, {-9, 4}, {-9, -4}
    };
    private static final BlackjackAction[] ACTIONS = {
            BlackjackAction.BET_MIN, BlackjackAction.BET_RECLAIM, BlackjackAction.BET_CONFIRM,
            BlackjackAction.HIT, BlackjackAction.STAND, BlackjackAction.DOUBLE, BlackjackAction.SPLIT,
            BlackjackAction.INSURANCE, BlackjackAction.TOP_UP, BlackjackAction.LEAVE_AFTER_HAND
    };
    private static final Material[] ACTION_BLOCKS = {
            Material.RED_CONCRETE, Material.LIME_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE, Material.ORANGE_CONCRETE, Material.GOLD_BLOCK,
            Material.PURPLE_CONCRETE, Material.CYAN_CONCRETE, Material.EMERALD_BLOCK, Material.BLACK_CONCRETE
    };
    private final CasinoTablesPlugin plugin;
    private final ArenaWorld arenaWorld;
    private final World world;
    private final int slot;
    private final int centerX;
    private final int y;
    private final PokerArenaStyle style;
    private final ArenaShape shape;
    private final int roomX;
    private final int roomZ;
    private final UUID[] players;
    private final String[] names;
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final Map<String, BlackjackAction> blockActions = new HashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final TextDisplay[] playerStatus = new TextDisplay[6];
    private final TextDisplay[] handCards = new TextDisplay[6];
    private TextDisplay centerStatus;
    private TextDisplay dealerCards;
    /** 与 dealerCards 同位置，只对开启 /casino peek 的管理员可见，提前露出暗牌。 */
    private TextDisplay dealerPeek;
    private TextDisplay rulesStatus;
    private TextDisplay bankStatus;
    private boolean closed;
    private boolean holeRevealed;
    private int renderedPot = Integer.MIN_VALUE;
    private final io.github.xingguanglang.casinotables.poker.CasinoChipInventory chips;

    BlackjackArena(CasinoTablesPlugin plugin, ArenaWorld arenaWorld, int slot, UUID[] players, String[] names,
                   List<Player> participants, PokerArenaStyle style, ArenaShape shape) {
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
        this.players = players.clone();
        this.names = names.clone();
        this.chips = new io.github.xingguanglang.casinotables.poker.CasinoChipInventory(plugin, RESERVED_SLOTS);
        try {
            buildRoom();
            for (int side = 0; side < participants.size(); side++) preparePlayer(side, participants.get(side));
            spawnDisplays();
        } catch (RuntimeException | Error throwable) {
            rollback();
            throw throwable;
        }
    }

    private void buildRoom() {
        arenaWorld.clearBox(centerX, 0, roomX + 1, roomZ + 1, y, y + 8);
        PokerArenaStyle.Palette palette = style.palette();
        Material base = palette.floor();
        // 露天材质和开放式轮廓都不砌墙、不封顶。
        boolean roofed = !palette.outdoor() && !shape.open();
        for (int x = -roomX; x <= roomX; x++) {
            for (int z = -roomZ; z <= roomZ; z++) {
                if (!shape.inside(x, z, roomX, roomZ)) continue;
                set(x, 0, z, shape.trim(x, z, roomX, roomZ) ? palette.floorTrim() : base);
                boolean edge = shape.boundary(x, z, roomX, roomZ);
                if (roofed && edge) {
                    for (int dy = 1; dy <= 6; dy++) {
                        Material wall = dy == 3 || dy == 4 ? palette.wallBand()
                                : Math.floorMod(x + z, 7) == 0 ? palette.wallAccent()
                                : palette.wall();
                        set(x, dy, z, wall);
                    }
                } else if (!roofed && edge && Math.floorMod(x + z, 6) == 0) {
                    // 开放式：不砌墙，只在边缘每隔几格立一根矮柱，标出台子的范围。
                    for (int dy = 1; dy <= 3; dy++) set(x, dy, z, palette.wallAccent());
                    set(x, 4, z, palette.ceilingAccent());
                }
                if (roofed) {
                    set(x, 7, z, Math.floorMod(x, 8) == 0 && Math.floorMod(z, 8) == 0
                            ? palette.ceilingAccent() : palette.ceiling());
                }
            }
        }
        for (int x = -10; x <= 10; x++) {
            for (int z = -8; z <= 8; z++) {
                boolean rim = Math.abs(x) == 10 || Math.abs(z) == 8;
                set(x, 1, z, rim ? palette.tableRim() : palette.tableTop());
                if (palette.hasOverlay() && !rim) set(x, 2, z, palette.tableOverlay());
            }
        }
        for (int side = 0; side < CONTROLS.length; side++) {
            for (int action = 0; action < ACTIONS.length; action++) {
                int[] position = CONTROLS[side][action];
                set(position[0], 1, position[1], ACTION_BLOCKS[action]);
                placeButton(position[0], 2, position[1]);
                // 底座和按钮都能触发，右键哪个都行。
                blockActions.put(key(centerX + position[0], y + 1, position[1]), ACTIONS[action]);
                blockActions.put(key(centerX + position[0], y + 2, position[1]), ACTIONS[action]);
            }
        }
        for (int side = 0; side < BET_ZONE.length; side++) renderBetZone(side, false);
        if (palette.outdoor()) {
            int[][] trees = {{-18, -15}, {18, -15}, {-18, 15}, {18, 15}};
            for (int[] tree : trees) {
                for (int dy = 1; dy <= 4; dy++) set(tree[0], dy, tree[1], palette.decorLog());
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) <= 3) set(tree[0] + dx, 5, tree[1] + dz, palette.decorLeaves());
                    }
                }
                set(tree[0], 4, tree[1] + 1, palette.decorLamp());
            }
        }
        buildBank();
    }

    private void buildBank() {
        PokerArenaStyle.Palette palette = style.palette();
        Material body = palette.bankBody();
        Material trim = palette.bankTrim();
        for (int z = -2; z <= 2; z++) {
            set(18, 1, z, body);
            set(18, 2, z, trim);
            set(18, 3, z, Math.abs(z) == 2 ? Material.AMETHYST_BLOCK : Material.BLACK_CONCRETE);
            set(18, 4, z, palette.bankTop());
            set(18, 5, z, z == 0 ? palette.ceilingAccent() : trim);
        }
        set(17, 1, -1, Material.POLISHED_BLACKSTONE);
        set(17, 1, 0, Material.POLISHED_BLACKSTONE);
        set(17, 1, 1, Material.POLISHED_BLACKSTONE);
        set(17, 2, 0, Material.EMERALD_BLOCK);
        placeButton(17, 3, 0);
        blockActions.put(key(centerX + 17, y + 2, 0), BlackjackAction.TOP_UP);
        blockActions.put(key(centerX + 17, y + 3, 0), BlackjackAction.TOP_UP);
    }

    private void spawnDisplays() {
        Villager dealer = world.spawn(new Location(world, centerX + 0.5, y + 2.0, -14.5), Villager.class, villager -> {
            villager.addScoreboardTag(ENTITY_TAG);
            villager.setProfession(Villager.Profession.CLERIC);
            villager.setVillagerLevel(5);
            villager.setAdult();
            villager.setAgeLock(true);
            villager.setAI(false);
            villager.setAware(false);
            villager.setSilent(true);
            villager.setInvulnerable(true);
            villager.setCollidable(false);
            villager.setPersistent(false);
            villager.customName(Text.parse(Messages.msg("blackjack.arena.dealer-name", "brand", plugin.brand())));
            villager.setCustomNameVisible(true);
        });
        entities.add(dealer);
        centerStatus = spawnText(new Location(world, centerX + 0.5, y + 5.4, 0.5),
                Messages.msg("blackjack.arena.title", "brand", plugin.brand()), 380, true, 1.3f);
        Location dealerBoard = new Location(world, centerX + 0.5, y + 3.9, -11.0);
        dealerCards = spawnText(dealerBoard, Messages.msg("blackjack.dealer.hidden"), 380, true, 1.2f);
        dealerPeek = spawnText(dealerBoard.clone(), Messages.msg("blackjack.dealer.hidden"), 380, true, 1.2f);
        rulesStatus = spawnText(new Location(world, centerX + 0.5, y + 2.9, -10.0),
                Messages.msg("blackjack.arena.rules"), 460, true, 0.85f);
        bankStatus = spawnText(new Location(world, centerX + 16.2, y + 5.25, 0.5),
                Messages.msg("blackjack.bank.loading"), 300, true, 0.82f);
        for (int side = 0; side < players.length; side++) {
            Location seat = seat(side);
            playerStatus[side] = spawnText(seat.clone().add(0, 2.3, 0),
                    Messages.msg("blackjack.hologram.seat-empty"), 280, true, 0.92f);
            handCards[side] = spawnText(handLocation(side),
                    Messages.msg("blackjack.arena.seat-boot"), 400, true, 1.0f);
            for (int action = 0; action < ACTIONS.length; action++) {
                int[] position = CONTROLS[side][action];
                spawnText(new Location(world, centerX + position[0] + 0.5, y + 3.05, position[1] + 0.5),
                        ACTIONS[action].display(), 170, true, 0.55f);
            }
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) hidePrivateFrom(online);
    }

    /** 荷官暗牌只有摊牌后才对全场公开；管理员 /casino peek 可提前看到。 */
    void hidePrivateFrom(Player viewer) {
        boolean adminPeek = !holeRevealed && plugin.handPeekEnabled(viewer);
        if (dealerCards != null && dealerCards.isValid()) {
            if (adminPeek) viewer.hideEntity(plugin, dealerCards);
            else viewer.showEntity(plugin, dealerCards);
        }
        if (dealerPeek != null && dealerPeek.isValid()) {
            if (adminPeek) viewer.showEntity(plugin, dealerPeek);
            else viewer.hideEntity(plugin, dealerPeek);
        }
    }

    private void refreshVisibility() {
        if (closed) return;
        for (Player online : plugin.getServer().getOnlinePlayers()) hidePrivateFrom(online);
    }

    void syncDealer(List<PokerCard> cards, boolean revealed) {
        holeRevealed = revealed;
        if (dealerCards != null && dealerCards.isValid()) {
            dealerCards.text(Text.parse(dealerText(cards, revealed)));
        }
        if (dealerPeek != null && dealerPeek.isValid()) {
            dealerPeek.text(Text.parse(dealerText(cards, true)
                    + (revealed ? "" : "\n" + Messages.msg("blackjack.dealer.peek-note"))));
        }
        refreshVisibility();
    }

    private String dealerText(List<PokerCard> cards, boolean revealed) {
        if (cards.isEmpty()) return Messages.msg("blackjack.dealer.hidden");
        StringBuilder text = new StringBuilder(Messages.msg("blackjack.dealer.label")).append("  ");
        for (int index = 0; index < cards.size(); index++) {
            if (index == 1 && !revealed) text.append(Messages.msg("blackjack.card.face-down")).append(' ');
            else text.append(cardText(cards.get(index))).append(' ');
        }
        text.append("  ").append(revealed ? BlackjackHand.describe(cards)
                : Messages.msg("blackjack.dealer.showing", "value", BlackjackHand.cardValue(cards.getFirst())));
        return text.toString();
    }

    void sync(BlackjackGame.View view) {
        StringBuilder center = new StringBuilder(Messages.msg("blackjack.hologram.title",
                "brand", plugin.brand(), "hand", view.handNumber())).append('\n');
        if (view.phase() != null) center.append(view.phase());
        if (view.actor() >= 0) {
            // 剩余时间越少，计时器越红；三档各自是一条完整消息。
            String key = view.seconds() <= 5 ? "blackjack.hologram.turn-timer.urgent"
                    : view.seconds() <= 10 ? "blackjack.hologram.turn-timer.warning"
                    : "blackjack.hologram.turn-timer.normal";
            center.append("  ").append(Messages.msg(key,
                    "player", names[view.actor()], "seconds", view.seconds()));
        } else if (view.seconds() > 0) {
            center.append("  ").append(Messages.msg("blackjack.hologram.countdown", "seconds", view.seconds()));
        }
        center.append('\n').append(Messages.msg("blackjack.hologram.total-wagered",
                "amount", view.totalWagered()));
        centerStatus.text(Text.parse(center.toString()));

        for (int side = 0; side < players.length; side++) {
            TextDisplay status = playerStatus[side];
            if (status != null && status.isValid()) {
                if (!view.seated()[side]) {
                    status.text(Text.parse(Messages.msg("blackjack.hologram.seat-open")));
                } else {
                    status.text(Text.parse(Messages.msg("blackjack.hologram.seat-name", "player", names[side])
                            + (side == view.actor() ? Messages.msg("blackjack.hologram.seat-acting") : "")
                            + "\n" + Messages.msg("blackjack.hologram.seat-chips",
                                    "chips", view.stack()[side], "bet", view.wagered()[side])
                            + (view.insurance()[side] > 0 ? Messages.msg("blackjack.hologram.seat-insurance",
                                    "amount", view.insurance()[side]) : "")
                            + (view.leaveAfterHand()[side]
                                    ? "\n" + Messages.msg("blackjack.hologram.seat-leaving") : "")));
                }
            }
            TextDisplay cards = handCards[side];
            if (cards != null && cards.isValid()) cards.text(Text.parse(view.handText()[side]));
        }
        syncBank(view);
        syncInventories(view);
        renderPot(view.totalWagered());
    }

    private void syncBank(BlackjackGame.View view) {
        if (bankStatus == null || !bankStatus.isValid()) return;
        StringBuilder bank = new StringBuilder(Messages.msg("blackjack.bank.header"))
                .append('\n').append(Messages.msg("blackjack.bank.hint", "limit", view.carryLimit()));
        for (int side = 0; side < players.length; side++) {
            if (!view.seated()[side]) continue;
            int room = Math.max(0, view.carryLimit() - view.stack()[side] - view.wagered()[side]);
            bank.append('\n').append(Messages.msg("blackjack.bank.entry",
                    "player", names[side], "chips", view.stack()[side], "room", room));
        }
        bankStatus.text(Text.parse(bank.toString()));
    }

    private void syncInventories(BlackjackGame.View view) {
        for (int side = 0; side < players.length; side++) {
            Player player = plugin.getServer().getPlayer(players[side]);
            if (player == null || !snapshots.containsKey(players[side])) continue;
            boolean betting = view.betting();
            String bettingOnly = Messages.msg("blackjack.item.betting-only");
            ensureItem(player, 0, Items.item(Material.YELLOW_DYE, Messages.msg("blackjack.item.bet-min.name"),
                    betting ? Messages.msg("blackjack.item.bet-min.lore", "min", view.minBet()) : bettingOnly,
                    Messages.msg("blackjack.item.bet-min.note")));
            ensureItem(player, 1, Items.item(Material.LIME_DYE, Messages.msg("blackjack.item.bet-confirm.name"),
                    betting ? Messages.msg("blackjack.item.bet-confirm.lore") : bettingOnly));
            ensureItem(player, 2, Items.item(Material.ORANGE_DYE, Messages.msg("blackjack.item.bet-reclaim.name"),
                    Messages.msg("blackjack.item.bet-reclaim.lore")));
            ensureItem(player, 3, Items.item(Material.FEATHER, Messages.msg("blackjack.item.hit.name")));
            ensureItem(player, 4, Items.item(Material.SHIELD, Messages.msg("blackjack.item.stand.name")));
            // 双倍、分牌、保险、离桌都在座位旁的实体按钮上，避免占用筹码栏。
            chips.sync(player, side, Math.max(0, view.stack()[side] - view.pending()[side]));
        }
    }

    private void ensureItem(Player player, int slot, org.bukkit.inventory.ItemStack desired) {
        org.bukkit.inventory.ItemStack current = player.getInventory().getItem(slot);
        if (current == null || current.getAmount() != desired.getAmount() || !current.isSimilar(desired)) {
            player.getInventory().setItem(slot, desired);
        }
    }

    /** 下注区在牌桌面上铺一层深色方块；轮到该座位下注时点亮。 */
    private void renderBetZone(int side, boolean active) {
        int baseX = BET_ZONE[side][0];
        int baseZ = BET_ZONE[side][1];
        Material idle = style.palette().zoneIdle();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean center = dx == 0 && dz == 0;
                set(baseX + dx, 1, baseZ + dz, center ? style.palette().floorTrim()
                        : active ? style.palette().zoneActive() : idle);
            }
        }
    }

    void highlightBetZones(boolean[] active) {
        for (int side = 0; side < BET_ZONE.length; side++) {
            renderBetZone(side, side < active.length && active[side]);
        }
    }

    boolean inBetZone(int side, Location location) {
        if (side < 0 || side >= BET_ZONE.length || location == null || !world.equals(location.getWorld())) {
            return false;
        }
        int relativeX = location.getBlockX() - centerX;
        int relativeZ = location.getBlockZ();
        return location.getBlockY() >= y + 2 && location.getBlockY() <= y + 4
                && Math.abs(relativeX - BET_ZONE[side][0]) <= 1
                && Math.abs(relativeZ - BET_ZONE[side][1]) <= 1;
    }

    boolean consumePlacedChip(Player player, int side, Material material) {
        return chips.consumePlaced(player, side, material);
    }

    boolean splitChip(Player player, int side, Material material) {
        return chips.split(player, side, material);
    }

    void mergeChips(Player player, int side, int amount) {
        chips.merge(player, side, amount);
    }

    void resetChips(int side) { chips.reset(side); }

    private void renderPot(int amount) {
        // 每秒都会 sync 一次，金额没变时不重复摆放 196 个方块。
        if (amount == renderedPot) return;
        renderedPot = amount;
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int dy = 2; dy <= 5; dy++) set(x, dy, z, Material.AIR);
            }
        }
        int position = 0;
        for (Map.Entry<PokerChips.Denomination, Integer> entry : PokerChips.breakdown(Math.max(0, amount)).entrySet()) {
            for (int count = 0; count < entry.getValue() && position < 196; count++, position++) {
                int layer = position / 49;
                int within = position % 49;
                set(-3 + within % 7, 2 + layer, -3 + within / 7, entry.getKey().material());
            }
        }
    }

    void announce(String headline, String detail, int wagered) {
        if (centerStatus == null || !centerStatus.isValid()) return;
        centerStatus.text(Text.parse(Messages.msg("blackjack.arena.title", "brand", plugin.brand())
                + "\n" + headline + "\n" + detail));
        renderPot(wagered);
    }

    void celebrate(List<UUID> winners) {
        for (UUID id : winners) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !snapshots.containsKey(id)) continue;
            player.setGlowing(true);
            world.strikeLightningEffect(player.getLocation());
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    void clearCelebration() {
        for (UUID id : snapshots.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) player.setGlowing(false);
        }
    }

    void addPlayer(int side, Player player) {
        names[side] = player.getName();
        players[side] = player.getUniqueId();
        preparePlayer(side, player);
        refreshVisibility();
    }

    void clearSeat(int side, UUID placeholder) {
        players[side] = placeholder;
        names[side] = Messages.msg("blackjack.seat.empty-name");
    }

    private void preparePlayer(int side, Player player) {
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        snapshots.put(player.getUniqueId(), snapshot);
        snapshot.prepare(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(seat(side));
        Text.send(player, Messages.msg("blackjack.enter"));
    }

    BlackjackAction actionAt(Block block) {
        return block == null ? null : blockActions.get(key(block.getX(), block.getY(), block.getZ()));
    }

    boolean protects(UUID player) { return snapshots.containsKey(player); }

    boolean contains(Location location) {
        return location != null && world.equals(location.getWorld())
                && location.getY() >= y && location.getY() <= y + 7
                // 见 PokerArena.contains 的说明：必须按真实轮廓判定，否则会把人卡在虚空。
                && shape.inside(location.getBlockX() - centerX, location.getBlockZ(), roomX, roomZ);
    }

    void release(Player player) {
        PlayerSnapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot == null) return;
        player.setGlowing(false);
        player.teleport(snapshot.location());
        snapshot.restore(player);
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
        arenaWorld.clearBox(centerX, 0, roomX + 1, roomZ + 1, y, y + 8);
        arenaWorld.release(slot);
    }

    private void rollback() {
        for (UUID id : List.copyOf(snapshots.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) release(player);
        }
        snapshots.clear();
        for (Entity entity : entities) if (entity != null && entity.isValid()) entity.remove();
        entities.clear();
        arenaWorld.clearBox(centerX, 0, roomX + 1, roomZ + 1, y, y + 8);
        arenaWorld.release(slot);
    }

    private TextDisplay spawnText(Location location, String text, int width, boolean visible, float scale) {
        TextDisplay display = world.spawn(location, TextDisplay.class, spawned -> {
            spawned.addScoreboardTag(ENTITY_TAG);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setSeeThrough(true);
            spawned.setShadowed(true);
            spawned.setLineWidth(width);
            spawned.setViewRange(2.0f);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setPersistent(false);
            spawned.setVisibleByDefault(visible);
            spawned.text(Text.parse(text));
        });
        org.bukkit.util.Transformation transformation = display.getTransformation();
        transformation.getScale().mul(scale);
        display.setTransformation(transformation);
        entities.add(display);
        return display;
    }

    Location seat(int side) {
        int[] relative = SEAT[side];
        Location location = new Location(world, centerX + relative[0] + 0.5, y + 2.0, relative[1] + 0.5);
        Location dealer = new Location(world, centerX + 0.5, y + 2.0, -14.5);
        location.setDirection(dealer.toVector().subtract(location.toVector()));
        return location;
    }

    /** 手牌全息摆在座位与牌桌之间，朝向桌心。 */
    private Location handLocation(int side) {
        int[] relative = SEAT[side];
        double factor = 0.55;
        return new Location(world, centerX + relative[0] * factor + 0.5, y + 3.0, relative[1] * factor + 0.5);
    }

    /** 在底座上方放一个朝上的石按钮，做法与德州一致。 */
    private void placeButton(int relativeX, int dy, int relativeZ) {
        set(relativeX, dy, relativeZ, Material.POLISHED_BLACKSTONE_BUTTON);
        Block button = world.getBlockAt(centerX + relativeX, y + dy, relativeZ);
        if (button.getBlockData() instanceof Switch data) {
            data.setFace(Switch.Face.FLOOR);
            button.setBlockData(data, false);
        }
    }

    private void set(int relativeX, int dy, int relativeZ, Material material) {
        ArenaBlocks.set(world, centerX + relativeX, y + dy, relativeZ, material);
    }

    private static String key(int x, int y, int z) { return x + ":" + y + ":" + z; }

    static String cardText(PokerCard card) {
        return (card.suit().red() ? "<red>" : "<white>") + "<bold>[ " + card.plainDisplay() + " ]</bold>";
    }
}
