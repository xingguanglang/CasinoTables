package io.github.xingguanglang.casinotables.poker;

import org.bukkit.Material;

import io.github.xingguanglang.casinotables.Messages;

import java.util.Locale;

/**
 * 房主可为每个赌场房间选择的实体装修模板，德州、炸金花和 21 点共用。
 *
 * <p>每种风格自带一份完整调色板，地板、墙体、天花、牌桌、银行、下注区各用各的材料，
 * 场地类只按调色板取材，不针对某个具体风格写分支；新增风格只需在这里加一行数据。
 */
public enum PokerArenaStyle {
    CLASSIC(1,
            new Palette(Material.SMOOTH_QUARTZ, Material.GOLD_BLOCK,
                    Material.POLISHED_BLACKSTONE_BRICKS, Material.GOLD_BLOCK, Material.TINTED_GLASS,
                    Material.CHISELED_QUARTZ_BLOCK, Material.SEA_LANTERN,
                    Material.GREEN_CONCRETE, Material.GOLD_BLOCK,
                    Material.GREEN_CONCRETE, Material.GREEN_CONCRETE, null, null,
                    Material.GILDED_BLACKSTONE, Material.GOLD_BLOCK, Material.CHISELED_QUARTZ_BLOCK,
                    Material.BLACK_CONCRETE, Material.SEA_LANTERN,
                    Material.OAK_LOG, Material.OAK_LEAVES, Material.LANTERN, false)),
    LUMINOUS(2,
            new Palette(Material.SMOOTH_QUARTZ, Material.GOLD_BLOCK,
                    Material.POLISHED_BLACKSTONE_BRICKS, Material.GOLD_BLOCK, Material.TINTED_GLASS,
                    Material.CHISELED_QUARTZ_BLOCK, Material.SEA_LANTERN,
                    Material.SEA_LANTERN, Material.GOLD_BLOCK,
                    Material.GLOWSTONE, Material.SEA_LANTERN,
                    Material.GREEN_STAINED_GLASS, Material.CYAN_STAINED_GLASS,
                    Material.GILDED_BLACKSTONE, Material.SEA_LANTERN, Material.CHISELED_QUARTZ_BLOCK,
                    Material.BLACK_CONCRETE, Material.SEA_LANTERN,
                    Material.OAK_LOG, Material.OAK_LEAVES, Material.LANTERN, false)),
    NATURE(3,
            new Palette(Material.GRASS_BLOCK, Material.GOLD_BLOCK,
                    Material.OAK_LOG, Material.OAK_LEAVES, Material.OAK_LEAVES,
                    Material.OAK_LEAVES, Material.SEA_LANTERN,
                    Material.GREEN_WOOL, Material.STRIPPED_OAK_WOOD,
                    Material.GREEN_WOOL, Material.GREEN_WOOL, null, null,
                    Material.STRIPPED_OAK_WOOD, Material.GOLD_BLOCK, Material.OAK_PLANKS,
                    Material.MOSSY_COBBLESTONE, Material.SEA_LANTERN,
                    Material.OAK_LOG, Material.OAK_LEAVES, Material.LANTERN, true)),
    MIDNIGHT(4,
            new Palette(Material.POLISHED_BLACKSTONE, Material.GOLD_BLOCK,
                    Material.DEEPSLATE_TILES, Material.GOLD_BLOCK, Material.GRAY_STAINED_GLASS,
                    Material.POLISHED_BLACKSTONE_BRICKS, Material.GLOWSTONE,
                    Material.BLACK_CONCRETE, Material.GOLD_BLOCK,
                    Material.BLACK_CONCRETE, Material.BLACK_CONCRETE, null, null,
                    Material.POLISHED_DEEPSLATE, Material.GOLD_BLOCK, Material.CHISELED_DEEPSLATE,
                    Material.BLACKSTONE, Material.GLOWSTONE,
                    Material.DARK_OAK_LOG, Material.DARK_OAK_LEAVES, Material.LANTERN, false)),
    CRIMSON(5,
            new Palette(Material.POLISHED_DEEPSLATE, Material.GOLD_BLOCK,
                    Material.DARK_OAK_PLANKS, Material.GOLD_BLOCK, Material.RED_STAINED_GLASS,
                    Material.STRIPPED_DARK_OAK_WOOD, Material.SHROOMLIGHT,
                    Material.RED_CONCRETE, Material.STRIPPED_DARK_OAK_WOOD,
                    Material.RED_CONCRETE, Material.RED_CONCRETE, null, null,
                    Material.DARK_OAK_PLANKS, Material.GOLD_BLOCK, Material.DARK_OAK_LOG,
                    Material.BLACK_CONCRETE, Material.SHROOMLIGHT,
                    Material.DARK_OAK_LOG, Material.DARK_OAK_LEAVES, Material.LANTERN, false)),
    AZURE(6,
            new Palette(Material.PRISMARINE_BRICKS, Material.LAPIS_BLOCK,
                    Material.DARK_PRISMARINE, Material.LAPIS_BLOCK, Material.LIGHT_BLUE_STAINED_GLASS,
                    Material.PRISMARINE, Material.SEA_LANTERN,
                    Material.BLUE_CONCRETE, Material.LAPIS_BLOCK,
                    Material.BLUE_CONCRETE, Material.BLUE_CONCRETE, null, null,
                    Material.DARK_PRISMARINE, Material.LAPIS_BLOCK, Material.PRISMARINE_BRICKS,
                    Material.BLUE_TERRACOTTA, Material.SEA_LANTERN,
                    Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, Material.SEA_LANTERN, false)),
    AMETHYST(7,
            new Palette(Material.SMOOTH_QUARTZ, Material.AMETHYST_BLOCK,
                    Material.PURPUR_BLOCK, Material.AMETHYST_BLOCK, Material.PURPLE_STAINED_GLASS,
                    Material.PURPUR_PILLAR, Material.PEARLESCENT_FROGLIGHT,
                    Material.PURPLE_CONCRETE, Material.AMETHYST_BLOCK,
                    Material.PURPLE_CONCRETE, Material.PURPLE_CONCRETE, null, null,
                    Material.PURPUR_BLOCK, Material.AMETHYST_BLOCK, Material.QUARTZ_PILLAR,
                    Material.PURPLE_TERRACOTTA, Material.PEARLESCENT_FROGLIGHT,
                    Material.DARK_OAK_LOG, Material.FLOWERING_AZALEA_LEAVES, Material.LANTERN, false)),
    SAKURA(8,
            new Palette(Material.MOSS_BLOCK, Material.CHERRY_LOG,
                    Material.CHERRY_LOG, Material.CHERRY_LEAVES, Material.CHERRY_LEAVES,
                    Material.CHERRY_LEAVES, Material.PEARLESCENT_FROGLIGHT,
                    Material.PINK_CONCRETE, Material.CHERRY_LOG,
                    Material.PINK_CONCRETE, Material.PINK_CONCRETE, null, null,
                    Material.CHERRY_PLANKS, Material.PINK_CONCRETE, Material.STRIPPED_CHERRY_LOG,
                    Material.MOSSY_COBBLESTONE, Material.PEARLESCENT_FROGLIGHT,
                    Material.CHERRY_LOG, Material.CHERRY_LEAVES, Material.LANTERN, true)),
    DESERT(9,
            new Palette(Material.SMOOTH_SANDSTONE, Material.GOLD_BLOCK,
                    Material.CUT_SANDSTONE, Material.GOLD_BLOCK, Material.ORANGE_STAINED_GLASS,
                    Material.SANDSTONE, Material.OCHRE_FROGLIGHT,
                    Material.ORANGE_TERRACOTTA, Material.CHISELED_SANDSTONE,
                    Material.ORANGE_TERRACOTTA, Material.ORANGE_TERRACOTTA, null, null,
                    Material.CUT_SANDSTONE, Material.GOLD_BLOCK, Material.SMOOTH_SANDSTONE,
                    Material.TERRACOTTA, Material.OCHRE_FROGLIGHT,
                    Material.ACACIA_LOG, Material.ACACIA_LEAVES, Material.LANTERN, false)),
    NETHER(10,
            new Palette(Material.NETHER_BRICKS, Material.GOLD_BLOCK,
                    Material.RED_NETHER_BRICKS, Material.GOLD_BLOCK, Material.RED_STAINED_GLASS,
                    Material.CHISELED_NETHER_BRICKS, Material.SHROOMLIGHT,
                    Material.CRIMSON_PLANKS, Material.CRIMSON_HYPHAE,
                    Material.CRIMSON_PLANKS, Material.CRIMSON_PLANKS, null, null,
                    Material.RED_NETHER_BRICKS, Material.GOLD_BLOCK, Material.NETHER_BRICKS,
                    Material.NETHERRACK, Material.SHROOMLIGHT,
                    Material.CRIMSON_STEM, Material.NETHER_WART_BLOCK, Material.SHROOMLIGHT, false)),
    GLACIER(11,
            new Palette(Material.SNOW_BLOCK, Material.PACKED_ICE,
                    Material.PACKED_ICE, Material.BLUE_ICE, Material.LIGHT_BLUE_STAINED_GLASS,
                    Material.BLUE_ICE, Material.SEA_LANTERN,
                    Material.LIGHT_BLUE_CONCRETE, Material.PACKED_ICE,
                    Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_BLUE_CONCRETE, null, null,
                    Material.BLUE_ICE, Material.DIAMOND_BLOCK, Material.SNOW_BLOCK,
                    Material.CYAN_TERRACOTTA, Material.SEA_LANTERN,
                    Material.SPRUCE_LOG, Material.SNOW_BLOCK, Material.SEA_LANTERN, false)),
    COPPER(12,
            new Palette(Material.POLISHED_DEEPSLATE, Material.COPPER_BLOCK,
                    Material.CUT_COPPER, Material.COPPER_BLOCK, Material.BROWN_STAINED_GLASS,
                    Material.OXIDIZED_CUT_COPPER, Material.OCHRE_FROGLIGHT,
                    Material.OXIDIZED_COPPER, Material.CUT_COPPER,
                    Material.OXIDIZED_COPPER, Material.OXIDIZED_COPPER, null, null,
                    Material.CUT_COPPER, Material.COPPER_BLOCK, Material.DEEPSLATE_TILES,
                    Material.DEEPSLATE_BRICKS, Material.OCHRE_FROGLIGHT,
                    Material.STRIPPED_JUNGLE_LOG, Material.JUNGLE_LEAVES, Material.LANTERN, false)),
    ENDER(13,
            new Palette(Material.END_STONE_BRICKS, Material.PURPUR_BLOCK,
                    Material.PURPUR_BLOCK, Material.PURPUR_PILLAR, Material.PURPLE_STAINED_GLASS,
                    Material.END_STONE, Material.VERDANT_FROGLIGHT,
                    Material.BLACK_CONCRETE, Material.PURPUR_PILLAR,
                    Material.BLACK_CONCRETE, Material.BLACK_CONCRETE, null, null,
                    Material.END_STONE_BRICKS, Material.PURPUR_BLOCK, Material.OBSIDIAN,
                    Material.OBSIDIAN, Material.VERDANT_FROGLIGHT,
                    Material.PURPUR_PILLAR, Material.CHORUS_FLOWER, Material.END_ROD, true));

    /**
     * 一套装修用到的全部材料。
     *
     * @param floor           地面主材
     * @param floorTrim       地面内圈装饰环
     * @param wall            墙体主材
     * @param wallAccent      墙体点缀，按坐标间隔出现
     * @param wallBand        墙体中段的透光带
     * @param ceiling         天花主材
     * @param ceilingAccent   天花发光点，必须是完整方块
     * @param tableTop        牌桌桌面
     * @param tableRim        牌桌外沿
     * @param tableGlowA      桌面下层棋盘格 A（只有带覆盖层的风格看得见）
     * @param tableGlowB      桌面下层棋盘格 B
     * @param tableOverlay    桌面覆盖层内部，没有就填 null；有覆盖层时筹码整体抬高一格
     * @param tableOverlayRim 桌面覆盖层外沿
     * @param bankBody        银行/ATM 主体
     * @param bankTrim        银行装饰
     * @param bankTop         银行顶面
     * @param zoneIdle        下注区未激活时的颜色
     * @param zoneActive      下注区激活时的颜色，必须是完整的实心亮方块（不能用灯笼这类小物件）
     * @param decorLog        露天装饰树的树干
     * @param decorLeaves     露天装饰树的树叶
     * @param decorLamp       露天装饰树上的灯，允许是灯笼这类非完整方块
     * @param outdoor         true 表示露天：不生成墙和天花，改为种树
     */
    public record Palette(Material floor, Material floorTrim, Material wall, Material wallAccent,
                          Material wallBand, Material ceiling, Material ceilingAccent,
                          Material tableTop, Material tableRim, Material tableGlowA, Material tableGlowB,
                          Material tableOverlay, Material tableOverlayRim,
                          Material bankBody, Material bankTrim, Material bankTop,
                          Material zoneIdle, Material zoneActive,
                          Material decorLog, Material decorLeaves, Material decorLamp,
                          boolean outdoor) {
        public boolean hasOverlay() { return tableOverlay != null; }

        public Material overlayRim() { return tableOverlayRim == null ? tableOverlay : tableOverlayRim; }
    }

    private final int id;
    private final Palette palette;

    PokerArenaStyle(int id, Palette palette) {
        this.id = id;
        this.palette = palette;
    }

    public int id() { return id; }
    public String display() { return Messages.msg("decor." + name().toLowerCase(Locale.ROOT) + ".name"); }
    public String description() { return Messages.msg("decor." + name().toLowerCase(Locale.ROOT) + ".description"); }
    public Palette palette() { return palette; }

    /** 桌面有覆盖层时筹码要抬高一格，避免埋进玻璃里。 */
    public int chipBaseDy() { return palette.hasOverlay() ? 2 : 1; }

    public static int count() { return values().length; }

    public static PokerArenaStyle byId(int id) {
        for (PokerArenaStyle style : values()) if (style.id == id) return style;
        return CLASSIC;
    }

    public static PokerArenaStyle parse(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (PokerArenaStyle style : values()) {
            if (value.equals(Integer.toString(style.id))) return style;
            if (value.equals(style.name().toLowerCase(Locale.ROOT))) return style;
            // 按当前语言里的名字匹配，服主换语言后照样能用名字选装潢。
            if (value.equalsIgnoreCase(style.display())) return style;
        }
        return switch (value) {
            case "classic", "green", "皇家", "绿毯", "经典" -> CLASSIC;
            case "light", "glass", "luminous", "海晶", "玻璃", "灯光" -> LUMINOUS;
            case "outdoor", "自然", "露天", "花园" -> NATURE;
            case "black", "dark", "午夜", "黑金" -> MIDNIGHT;
            case "red", "velvet", "绯红", "丝绒" -> CRIMSON;
            case "blue", "ocean", "深海", "蓝调" -> AZURE;
            case "purple", "紫晶", "紫水晶" -> AMETHYST;
            case "cherry", "樱花", "庭院" -> SAKURA;
            case "sand", "沙漠", "绿洲" -> DESERT;
            case "hell", "地狱", "熔岩" -> NETHER;
            case "ice", "snow", "极地", "冰宫" -> GLACIER;
            case "steam", "蒸汽", "铜" -> COPPER;
            case "end", "void", "末地", "虚空" -> ENDER;
            default -> null;
        };
    }
}
