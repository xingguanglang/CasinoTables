package io.github.casinotables.arena;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

public final class EmptyChunkGenerator extends ChunkGenerator {
    @Override public boolean shouldGenerateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return false; }
    @Override public boolean shouldGenerateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return false; }
    @Override public boolean shouldGenerateBedrock() { return false; }
    @Override public boolean shouldGenerateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return false; }
}

