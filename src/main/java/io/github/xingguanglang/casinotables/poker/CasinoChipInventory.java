package io.github.xingguanglang.casinotables.poker;

import io.github.xingguanglang.casinotables.CasinoTablesPlugin;
import io.github.xingguanglang.casinotables.Items;
import io.github.xingguanglang.casinotables.Messages;
import io.github.xingguanglang.casinotables.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实体筹码库存：把德州那套「背包发筹码 → 放进下注区 → 确认」的下注机制抽出来，
 * 供炸金花和 21 点复用。只管背包与筹码布局，下注区的坐标由各自的场地类负责。
 *
 * <p>德州自己仍使用 PokerArena 内的原实现，这里不去动它，避免改坏正在处理真钱的牌桌。
 */
public final class CasinoChipInventory {
    /** 前若干格留给功能按钮，筹码从 reservedSlots 开始连续摆放。 */
    private final int reservedSlots;
    private final int chipSlots;
    private final CasinoTablesPlugin plugin;
    private final Map<Integer, Map<PokerChips.Denomination, Integer>> layouts = new LinkedHashMap<>();
    private final Map<Integer, Boolean> compact = new LinkedHashMap<>();
    private final Map<Integer, Boolean> dirty = new LinkedHashMap<>();

    /** 快捷栏格数。筹码排在保留格之后，超出这个数就落进背包，手上拿不到了。 */
    public static final int HOTBAR_SIZE = 9;

    /** 保留格最多能占几格，才不至于把某个面额挤出快捷栏。 */
    public static int maxReservedForHotbar() {
        return HOTBAR_SIZE - PokerChips.denominations().size();
    }

    public CasinoChipInventory(CasinoTablesPlugin plugin, int reservedSlots) {
        this.plugin = plugin;
        this.reservedSlots = Math.max(0, Math.min(35, reservedSlots));
        if (this.reservedSlots > maxReservedForHotbar()) {
            // 不致命，但玩家会发现最小面额摸不到——21 点曾经留了五格，一元和五元直接进了背包。
            plugin.getLogger().warning("Reserved " + this.reservedSlots + " hotbar slots, but only "
                    + maxReservedForHotbar() + " can be spared before the smallest chips are pushed"
                    + " out of the hotbar and become unreachable.");
        }
        this.chipSlots = 36 - this.reservedSlots;
    }

    public void markDirty(int side) { dirty.put(side, true); }

    public void reset(int side) {
        layouts.remove(side);
        compact.remove(side);
        dirty.put(side, true);
    }

    private Map<PokerChips.Denomination, Integer> layout(int side) {
        return layouts.computeIfAbsent(side, ignored -> new LinkedHashMap<>());
    }

    /** 按可用金额重新发放实体筹码；金额没变且布局未失效时不动背包，避免每 tick 刷新。 */
    public void sync(Player player, int side, int available) {
        int safe = Math.max(0, available);
        Map<PokerChips.Denomination, Integer> current = layout(side);
        boolean stale = dirty.getOrDefault(side, true) || value(current) != safe;
        if (!stale) return;
        Map<PokerChips.Denomination, Integer> next = new LinkedHashMap<>(
                compact.getOrDefault(side, false) || safe < PokerChips.MIN_OPENING_COUNT
                        ? PokerChips.breakdown(safe) : PokerChips.playableBreakdown(safe));
        layouts.put(side, next);
        dirty.put(side, false);
        clearChips(player);
        putChips(player, next);
    }

    /**
     * 玩家把一枚筹码放进下注区时调用。放块事件本身会被取消，这里只更新布局，
     * 并在下一 tick 从手持堆里扣掉一枚。
     *
     * @return true 表示确实消耗了一枚，可以计入待确认下注
     */
    public boolean consumePlaced(Player player, int side, Material material) {
        PokerChips.Denomination denomination = denomination(material);
        if (denomination == null) return false;
        Map<PokerChips.Denomination, Integer> current = layout(side);
        int count = current.getOrDefault(denomination, 0);
        if (count <= 0) {
            dirty.put(side, true);
            return false;
        }
        if (count == 1) current.remove(denomination); else current.put(denomination, count - 1);
        int heldSlot = player.getInventory().getHeldItemSlot();
        plugin.getServer().getScheduler().runTask(plugin, () -> decrementOne(player, heldSlot, material));
        return true;
    }

    private void decrementOne(Player player, int preferredSlot, Material material) {
        ItemStack item = player.getInventory().getItem(preferredSlot);
        int slot = preferredSlot;
        if (item == null || item.getType() != material) {
            slot = -1;
            for (int candidate = reservedSlots; candidate < 36; candidate++) {
                ItemStack candidateItem = player.getInventory().getItem(candidate);
                if (candidateItem != null && candidateItem.getType() == material) {
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

    /** 手持筹码右键空气：等值分解一枚为更小面额。 */
    public boolean split(Player player, int side, Material material) {
        PokerChips.Split split = PokerChips.split(material);
        if (split == null) {
            Text.send(player, Messages.msg("poker.chips.split-smallest"));
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != material || held.getAmount() <= 0) return false;
        ItemStack replacement = chipItem(split.target(), split.count());
        if (held.getAmount() == 1) player.getInventory().setItemInMainHand(null);
        else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItemInMainHand(held);
        }
        if (!player.getInventory().addItem(replacement).isEmpty()) {
            Text.send(player, Messages.msg("poker.chips.split-no-space"));
            dirty.put(side, true);
            return false;
        }
        Map<PokerChips.Denomination, Integer> current = layout(side);
        PokerChips.Denomination source = denomination(material);
        if (source != null && current.getOrDefault(source, 0) > 0) {
            int left = current.get(source) - 1;
            if (left == 0) current.remove(source); else current.put(source, left);
            current.merge(split.target(), split.count(), Integer::sum);
        }
        compact.put(side, false);
        dirty.put(side, false);
        Text.send(player, Messages.msg("poker.chips.split-done", "from", display(material),
                "count", split.count(), "to", split.target().display()));
        return true;
    }

    /** 潜行右键空气：把可用筹码合并成尽可能高的面额。 */
    public void merge(Player player, int side, int amount) {
        Map<PokerChips.Denomination, Integer> merged = new LinkedHashMap<>(PokerChips.breakdown(Math.max(0, amount)));
        if (slotsNeeded(merged) > chipSlots) {
            Text.send(player, Messages.msg("poker.chips.merge-no-space"));
            return;
        }
        compact.put(side, true);
        dirty.put(side, false);
        layouts.put(side, merged);
        clearChips(player);
        putChips(player, merged);
        int pieces = merged.values().stream().mapToInt(Integer::intValue).sum();
        Text.send(player, Messages.msg("poker.chips.merge-done",
                "chips", PokerChips.summary(Math.max(0, amount)), "pieces", pieces));
    }

    public void clearChips(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && PokerChips.value(item.getType()) > 0) player.getInventory().setItem(slot, null);
        }
    }

    private void putChips(Player player, Map<PokerChips.Denomination, Integer> breakdown) {
        int slot = reservedSlots;
        for (Map.Entry<PokerChips.Denomination, Integer> entry : breakdown.entrySet()) {
            int remaining = entry.getValue();
            while (remaining > 0 && slot < 36) {
                int count = Math.min(64, remaining);
                player.getInventory().setItem(slot++, chipItem(entry.getKey(), count));
                remaining -= count;
            }
        }
    }

    private ItemStack chipItem(PokerChips.Denomination denomination, int count) {
        ItemStack item = Items.item(denomination.material(),
                Messages.msg("poker.chips.item-name", "name", denomination.display(),
                        "value", denomination.value()),
                Messages.msg("poker.chips.item-lore-place"),
                Messages.msg(denomination.value() > 1 ? "poker.chips.item-lore-split"
                        : "poker.chips.item-lore-smallest"),
                Messages.msg("poker.chips.item-lore-merge"));
        item.setAmount(Math.max(1, count));
        return item;
    }

    private int value(Map<PokerChips.Denomination, Integer> layout) {
        int total = 0;
        for (Map.Entry<PokerChips.Denomination, Integer> entry : layout.entrySet()) {
            total += entry.getKey().value() * entry.getValue();
        }
        return total;
    }

    private int slotsNeeded(Map<PokerChips.Denomination, Integer> layout) {
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

    private String display(Material material) {
        PokerChips.Denomination denomination = denomination(material);
        return denomination == null ? material.name() : denomination.display();
    }
}
