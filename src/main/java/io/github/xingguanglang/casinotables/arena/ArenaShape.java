package io.github.xingguanglang.casinotables.arena;

import io.github.xingguanglang.casinotables.Messages;

import java.util.Locale;

/**
 * 赌场房间的轮廓形状，与 PokerArenaStyle 的材质是两个互不相干的维度：
 * 材质决定用什么方块，形状决定这些方块摆成什么轮廓。
 *
 * <p>实现方式是「外接矩形 + 内部谓词」：每种形状仍然在一个矩形范围里逐格判断，
 * 这样玩家离场判定 {@code contains()} 和收场清理 {@code clearBox()} 都能继续用矩形，
 * 既不会把玩家误判成出界，也不会在虚空里留下清不掉的残块。
 *
 * <p>非矩形轮廓统一把外接矩形放大 {@link #GROWTH} 格，因为座位、按钮、银行和露天装饰树
 * 的坐标是写死的，最远的一处在 (18, 15)；不放大的话圆形会把它们甩到墙外悬空。
 */
public enum ArenaShape {
    RECTANGLE(1, false, false),
    ROUND(2, true, false),
    OCTAGON(3, true, false),
    OPEN(4, true, true);

    /** 非矩形轮廓的外接矩形放大量，保证写死的设施都还在轮廓内。 */
    public static final int GROWTH = 6;
    /** 八边形切角的松紧：越大越接近方形。 */
    private static final double OCTAGON_LIMIT = 1.45;

    private final int id;
    private final boolean grown;
    private final boolean open;

    ArenaShape(int id, boolean grown, boolean open) {
        this.id = id;
        this.grown = grown;
        this.open = open;
    }

    public int id() { return id; }
    public String display() { return Messages.msg("shape." + name().toLowerCase(Locale.ROOT) + ".name"); }
    public String description() { return Messages.msg("shape." + name().toLowerCase(Locale.ROOT) + ".description"); }

    /** 开放式没有墙和天花，露天材质之外也能用。 */
    public boolean open() { return open; }

    public int roomX(int baseRoomX) { return grown ? baseRoomX + GROWTH : baseRoomX; }

    public int roomZ(int baseRoomZ) { return grown ? baseRoomZ + GROWTH : baseRoomZ; }

    /** 该格是否属于房间内部。 */
    public boolean inside(int x, int z, int roomX, int roomZ) {
        if (Math.abs(x) > roomX || Math.abs(z) > roomZ) return false;
        return switch (this) {
            case RECTANGLE -> true;
            case ROUND, OPEN -> {
                double nx = x / (double) roomX;
                double nz = z / (double) roomZ;
                yield nx * nx + nz * nz <= 1.0;
            }
            case OCTAGON -> Math.abs(x) / (double) roomX + Math.abs(z) / (double) roomZ <= OCTAGON_LIMIT;
        };
    }

    /**
     * 是否是外墙格。用「自己在内部且四邻中至少一格在外部」判断，
     * 这样圆形和八边形的斜边也是四连通闭合的，不会留下能走出去的对角缝。
     */
    public boolean boundary(int x, int z, int roomX, int roomZ) {
        if (!inside(x, z, roomX, roomZ)) return false;
        return !inside(x + 1, z, roomX, roomZ) || !inside(x - 1, z, roomX, roomZ)
                || !inside(x, z + 1, roomX, roomZ) || !inside(x, z - 1, roomX, roomZ);
    }

    /** 紧贴外墙内侧的一圈，用来铺地面装饰环。 */
    public boolean trim(int x, int z, int roomX, int roomZ) {
        if (!inside(x, z, roomX, roomZ) || boundary(x, z, roomX, roomZ)) return false;
        return boundary(x + 1, z, roomX, roomZ) || boundary(x - 1, z, roomX, roomZ)
                || boundary(x, z + 1, roomX, roomZ) || boundary(x, z - 1, roomX, roomZ);
    }

    public static int count() { return values().length; }

    public static ArenaShape byId(int id) {
        for (ArenaShape shape : values()) if (shape.id == id) return shape;
        return RECTANGLE;
    }

    public static ArenaShape parse(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (ArenaShape shape : values()) {
            if (value.equals(Integer.toString(shape.id))) return shape;
            if (value.equals(shape.name().toLowerCase(Locale.ROOT))) return shape;
            // 按当前语言里的名字匹配，服主换语言后照样能用名字选形状。
            if (value.equalsIgnoreCase(shape.display())) return shape;
        }
        return switch (value) {
            case "rect", "square", "方", "方形", "矩形" -> RECTANGLE;
            case "circle", "round", "圆", "圆形", "圆厅" -> ROUND;
            case "octa", "octagon", "八边", "八角" -> OCTAGON;
            case "open", "terrace", "开放", "露台", "开放式" -> OPEN;
            default -> null;
        };
    }
}
