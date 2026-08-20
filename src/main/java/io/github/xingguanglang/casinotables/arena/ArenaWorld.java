package io.github.xingguanglang.casinotables.arena;

import io.github.xingguanglang.casinotables.CasinoTablesPlugin;
import io.github.xingguanglang.casinotables.arena.EmptyChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.util.BitSet;

public final class ArenaWorld {
    private final World world;
    private final int baseY;
    private final int spacing;
    private final BitSet usedSlots = new BitSet();

    public ArenaWorld(CasinoTablesPlugin plugin, String configRoot, String defaultName) {
        String name = plugin.getConfig().getString(configRoot + ".world-name", defaultName);
        World existing = Bukkit.getWorld(name);
        world = existing != null ? existing : WorldCreator.name(name)
                .generator(new EmptyChunkGenerator()).generateStructures(false).createWorld();
        if (world == null) throw new IllegalStateException("Failed to create the arena world " + name);
        baseY = plugin.getConfig().getInt(configRoot + ".base-y", 64);
        spacing = Math.max(48, plugin.getConfig().getInt(configRoot + ".slot-spacing", 64));
        world.setAutoSave(false);
        world.setKeepSpawnInMemory(false);
        world.setPVP(false);
        world.setSpawnFlags(false, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setTime(6000L);
    }

    public int allocate() {
        int slot = usedSlots.nextClearBit(0);
        usedSlots.set(slot);
        return slot;
    }

    public void release(int slot) { usedSlots.clear(slot); }
    public World world() { return world; }
    public int baseY() { return baseY; }
    public int centerX(int slot) { return slot * spacing; }

    public void clearBox(int centerX, int centerZ, int radiusX, int radiusZ, int minY, int maxY) {
        for (int x = centerX - radiusX; x <= centerX + radiusX; x++) {
            for (int z = centerZ - radiusZ; z <= centerZ + radiusZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }
}
