package io.github.xingguanglang.casinotables.blackjack;

import io.github.xingguanglang.casinotables.Reason;
import io.github.xingguanglang.casinotables.CasinoTablesPlugin;
import io.github.xingguanglang.casinotables.Messages;
import io.github.xingguanglang.casinotables.Text;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class BlackjackRecords {
    private static final int LIMIT = 50;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final CasinoTablesPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    BlackjackRecords(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "blackjack-history.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    /** 把内层理由套进「第 N 手」的外壳里。 */
    private static String renderReason(String key, java.util.List<String> args, int handNumber) {
        return Messages.msg("blackjack.history.reason.hand", "hand", handNumber,
                "reason", Messages.msg(key, args.toArray()));
    }

    /** 新记录按当前语言现场渲染；升级前的老记录只有死文本，原样用。 */
    private String storedReason(String key) {
        String reasonKey = yaml.getString("hands." + key + ".reason-key");
        if (reasonKey != null) {
            return renderReason(reasonKey, yaml.getStringList("hands." + key + ".reason-args"),
                    yaml.getInt("hands." + key + ".hand"));
        }
        return yaml.getString("hands." + key + ".reason",
                Messages.msg("blackjack.history.reason.default"));
    }

    void record(List<UUID> players, List<String> names, List<UUID> winners, int wagered,
                int houseProfit, Reason reason, int handNumber, long startedAt) {
        String id = Long.toString(System.currentTimeMillis());
        String root = "hands." + id;
        yaml.set(root + ".started-at", startedAt);
        yaml.set(root + ".players", names);
        yaml.set(root + ".player-ids", players.stream().map(UUID::toString).toList());
        yaml.set(root + ".winner-ids", winners.stream().map(UUID::toString).toList());
        yaml.set(root + ".wagered", wagered);
        yaml.set(root + ".house-profit", houseProfit);
        // 存键和参数而不是渲染好的句子，服主换语言后整段历史跟着换；
        // reason 字段保留渲染结果，供直接翻 yml 的人和旧版本读取兜底。
        yaml.set(root + ".reason-key", reason.key());
        yaml.set(root + ".reason-args", new java.util.ArrayList<>(reason.args()));
        yaml.set(root + ".hand", handNumber);
        yaml.set(root + ".reason", renderReason(reason.key(), reason.args(), handNumber));
        trim();
        save();
    }

    void show(Player player) {
        var hands = yaml.getConfigurationSection("hands");
        if (hands == null) {
            Text.send(player, Messages.msg("blackjack.history.empty"));
            return;
        }
        List<String> keys = new ArrayList<>(hands.getKeys(false));
        keys.sort(java.util.Comparator.reverseOrder());
        int shown = 0;
        Text.send(player, Messages.msg("blackjack.history.header"));
        for (String key : keys) {
            List<String> ids = yaml.getStringList("hands." + key + ".player-ids");
            if (!ids.contains(player.getUniqueId().toString())) continue;
            List<String> names = yaml.getStringList("hands." + key + ".players");
            List<String> winners = yaml.getStringList("hands." + key + ".winner-ids");
            boolean won = winners.contains(player.getUniqueId().toString());
            long time = yaml.getLong("hands." + key + ".started-at");
            int wagered = yaml.getInt("hands." + key + ".wagered");
            String reason = storedReason(key);
            Text.send(player, Messages.msg(won ? "blackjack.history.entry-won" : "blackjack.history.entry-lost",
                    "time", TIME.format(Instant.ofEpochMilli(time)),
                    "players", String.join(Messages.msg("blackjack.history.player-separator"), names),
                    "wagered", wagered,
                    "reason", reason));
            if (++shown >= 10) break;
        }
        if (shown == 0) Text.send(player, Messages.msg("blackjack.history.none-for-you"));
    }

    void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) return;
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save blackjack history: " + exception.getMessage());
        }
    }

    private void trim() {
        var hands = yaml.getConfigurationSection("hands");
        if (hands == null || hands.getKeys(false).size() <= LIMIT) return;
        List<String> keys = new ArrayList<>(hands.getKeys(false));
        keys.sort(String::compareTo);
        for (int index = 0; index < keys.size() - LIMIT; index++) yaml.set("hands." + keys.get(index), null);
    }
}
