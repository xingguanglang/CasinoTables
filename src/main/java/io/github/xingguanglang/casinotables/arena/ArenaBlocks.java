package io.github.xingguanglang.casinotables.arena;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

/** 竞技场摆建筑方块的唯一出口。 */
public final class ArenaBlocks {
    private ArenaBlocks() { }

    /**
     * 摆一个建筑方块。
     *
     * <p>树叶必须特殊处理：原版把「附近 6 格内没有原木」的树叶当成被砍断的树，
     * 会随机枯萎消失并掉出树苗和木棍。赌场里的树叶是建材不是树，露天自然、樱花庭院
     * 这类风格拿它当墙和装饰用，不钉死的话房间会自己烂出洞来，地上还散一堆树苗。
     *
     * <p>钉死的办法是把 persistent 置为 true，和玩家自己手放的树叶一样，永不枯萎。
     */
    public static void set(World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(material, false);
        BlockData data = block.getBlockData();
        if (data instanceof Leaves leaves && !leaves.isPersistent()) {
            leaves.setPersistent(true);
            block.setBlockData(leaves, false);
        }
    }
}
