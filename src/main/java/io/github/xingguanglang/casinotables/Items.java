package io.github.xingguanglang.casinotables;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public final class Items {
    private Items() {
    }

    public static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        // displayName() works from 1.21 onwards; ItemMeta.customName() only exists
        // since 1.21.4, and using it would drop support for the three releases below that.
        meta.displayName(Text.parse(name));
        if (lore.length > 0) {
            List<Component> lines = Arrays.stream(lore).map(Text::parse).toList();
            meta.lore(lines);
        }
        // 这些都是界面图标和道具，玩家该看到的只有我们写的名字和说明。
        // 原版会往提示框里追加自己的东西——烟花的「飞行时间」、药水效果、旗帜图案、
        // 属性修饰符之类——而且那几行走的是客户端语言，会在一个英文界面里冒出中文。
        //
        // 用 values() 而不是逐个点名常量：常量表每个版本都在变（HIDE_ADDITIONAL_TOOLTIP
        // 是 1.20.5 才有的），点名会在老版本上抛 NoSuchFieldError；values() 在运行期
        // 取服务端自己的枚举，1.21 到 26.2 都安全。
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }
}

