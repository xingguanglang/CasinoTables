package io.github.xingguanglang.casinotables;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public final class Text {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component parse(String value) {
        return MINI.deserialize(value);
    }

    /** 聊天前缀同样来自语言文件，服主可以改成自己的名字。 */
    public static Component prefixed(String value) {
        return MINI.deserialize(Messages.msg("prefix") + value);
    }

    public static void send(CommandSender sender, String value) {
        sender.sendMessage(prefixed(value));
    }
}

