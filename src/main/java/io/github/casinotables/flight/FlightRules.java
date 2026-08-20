package io.github.casinotables.flight;

import java.util.ArrayList;
import java.util.List;

public final class FlightRules {
    private static final int[] START_INDEXES = {44, 5, 18, 31};
    /** 棋盘外圈每象限 12 格，总计 48 格；象限长度严格为 4 的倍数。 */
    public static final int TRACK_CELLS = 48;
    /** 从基地旁起点绕行 44 格后进入自己的终点航道。 */
    public static final int OUTER = 44;
    public static final int HOME = 6;
    public static final int FINISHED = OUTER + HOME;
    public static final int FLIGHT_LANE_START = 15;
    public static final int FLIGHT_LANE_DISTANCE = 12;

    private FlightRules() {
    }

    /** 返回同色跳跃前的落点；越过终点时按多出的点数折返。 */
    public static int normalDestination(int current, int rolled) {
        if (current < 0) return rolled == 6 ? 0 : current;
        if (current >= FINISHED) return FINISHED;
        int distanceToFinish = FINISHED - current;
        if (rolled <= distanceToFinish) return current + rolled;
        return FINISHED - (rolled - distanceToFinish);
    }

    public static int shortcutDestination(int color, int progress) {
        if (progress < 0 || progress >= OUTER) return progress;
        if (progress == FLIGHT_LANE_START) return progress + FLIGHT_LANE_DISTANCE;
        int global = trackIndex(color, progress);
        // 出生点使用矿物块，不属于四色混凝土跳跃格；其余同色混凝土才执行四格跳跃。
        if (!isStartIndex(global) && trackColorIndex(global) == color && progress + 4 < OUTER) {
            return progress + 4;
        }
        return progress;
    }

    /** 四个起飞点位于对应基地旁，且自身仍处在红黄蓝绿序列的本色位置。 */
    public static int startIndex(int color) {
        return START_INDEXES[Math.floorMod(color, START_INDEXES.length)];
    }

    /** 外圈格的固定颜色序号：0 红、1 黄、2 蓝、3 绿。 */
    public static int trackColorIndex(int globalIndex) {
        return Math.floorMod(globalIndex, 4);
    }

    /** 出生点是外圈中仅有的四个矿物块例外。 */
    public static boolean isStartIndex(int globalIndex) {
        int normalized = Math.floorMod(globalIndex, TRACK_CELLS);
        for (int color = 0; color < 4; color++) {
            if (startIndex(color) == normalized) return true;
        }
        return false;
    }

    /** 从基地旁的起点出发，绕外圈后到达自己颜色的终点航道入口。 */
    public static int trackIndex(int color, int progress) {
        return Math.floorMod(startIndex(color) - progress, TRACK_CELLS);
    }

    /** Returns every outer/home grid position visited before an optional shortcut flight. */
    public static List<Integer> movementPath(int current, int rolled, int color) {
        int normal = normalDestination(current, rolled);
        if (normal == current) return List.of();
        if (current < 0) return List.of(normal);
        List<Integer> path = new ArrayList<>(Math.max(1, rolled));
        int forwardSteps = Math.min(rolled, FINISHED - current);
        for (int step = 1; step <= forwardSteps; step++) path.add(current + step);
        int backwardSteps = rolled - forwardSteps;
        for (int step = 1; step <= backwardSteps; step++) path.add(FINISHED - step);
        return List.copyOf(path);
    }
}
