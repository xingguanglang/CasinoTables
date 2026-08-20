package io.github.casinotables;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
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
        item.setItemMeta(meta);
        return item;
    }
}

