package io.github.casinotables.poker;

import io.github.casinotables.Messages;
import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PokerChips {
    public record Denomination(Material material, int value, String nameKey) {
        /** 面额名从语言文件读取；DENOMINATIONS 在插件启用前就初始化，所以不能提前取文本。 */
        public String display() { return Messages.msg(nameKey); }
    }
    public record Split(Denomination target, int count) { }

    public static final int MIN_OPENING_COUNT = 5;

    private static final List<Denomination> DENOMINATIONS = List.of(
            new Denomination(Material.DIAMOND_BLOCK, 1000, "poker.chips.diamond"),
            new Denomination(Material.GOLD_BLOCK, 200, "poker.chips.gold"),
            new Denomination(Material.EMERALD_BLOCK, 50, "poker.chips.emerald"),
            new Denomination(Material.REDSTONE_BLOCK, 15, "poker.chips.redstone"),
            new Denomination(Material.IRON_BLOCK, 5, "poker.chips.iron"),
            new Denomination(Material.COPPER_BLOCK, 1, "poker.chips.copper"));

    private PokerChips() { }

    public static List<Denomination> denominations() { return DENOMINATIONS; }

    public static int value(Material material) {
        for (Denomination denomination : DENOMINATIONS) {
            if (denomination.material() == material) return denomination.value();
        }
        return 0;
    }

    public static Map<Denomination, Integer> breakdown(int amount) {
        if (amount < 0) throw new IllegalArgumentException("amount must be non-negative");
        Map<Denomination, Integer> result = new LinkedHashMap<>();
        int remaining = amount;
        for (Denomination denomination : DENOMINATIONS) {
            int count = remaining / denomination.value();
            if (count > 0) result.put(denomination, count);
            remaining %= denomination.value();
        }
        return result;
    }

    /**
     * Produces a value-preserving chip mix where every issued denomination has at least five pieces.
     * Expensive denominations that cannot meet that minimum while reserving five of all lower
     * denominations are skipped. Callers must provide at least {@link #MIN_OPENING_COUNT} coins.
     */
    public static Map<Denomination, Integer> playableBreakdown(int amount) {
        if (amount < MIN_OPENING_COUNT) {
            throw new IllegalArgumentException("amount must be at least " + MIN_OPENING_COUNT);
        }
        Map<Denomination, Integer> result = new LinkedHashMap<>();
        int remaining = amount;
        for (int index = 0; index < DENOMINATIONS.size(); index++) {
            Denomination denomination = DENOMINATIONS.get(index);
            int lowerReserve = 0;
            for (int lower = index + 1; lower < DENOMINATIONS.size(); lower++) {
                lowerReserve += DENOMINATIONS.get(lower).value() * MIN_OPENING_COUNT;
            }
            int count = Math.max(0, remaining - lowerReserve) / denomination.value();
            if (count < MIN_OPENING_COUNT) continue;
            result.put(denomination, count);
            remaining -= count * denomination.value();
        }
        if (remaining != 0) throw new IllegalStateException("chip breakdown lost value: " + remaining);
        return result;
    }

    /** Splits one chip into at least five equal chips of the first exactly divisible lower value. */
    public static Split split(Material material) {
        for (int source = 0; source < DENOMINATIONS.size(); source++) {
            Denomination denomination = DENOMINATIONS.get(source);
            if (denomination.material() != material) continue;
            for (int target = source + 1; target < DENOMINATIONS.size(); target++) {
                Denomination lower = DENOMINATIONS.get(target);
                if (denomination.value() % lower.value() != 0) continue;
                int count = denomination.value() / lower.value();
                if (count >= MIN_OPENING_COUNT) return new Split(lower, count);
            }
            return null;
        }
        return null;
    }

    public static String summary(int amount) {
        if (amount == 0) return Messages.msg("poker.chips.summary-empty");
        StringBuilder result = new StringBuilder();
        for (Map.Entry<Denomination, Integer> entry : breakdown(amount).entrySet()) {
            if (!result.isEmpty()) result.append(Messages.msg("poker.chips.summary-separator"));
            result.append(Messages.msg("poker.chips.summary-entry",
                    "name", entry.getKey().display(), "count", entry.getValue()));
        }
        return result.toString();
    }
}
