package io.github.casinotables.blackjack;

import io.github.casinotables.CasinoTablesPlugin;
import io.github.casinotables.Messages;
import io.github.casinotables.Text;
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

    void record(List<UUID> players, List<String> names, List<UUID> winners, int wagered,
                int houseProfit, String reason, long startedAt) {
        String id = Long.toString(System.currentTimeMillis());
        String root = "hands." + id;
        yaml.set(root + ".started-at", startedAt);
        yaml.set(root + ".players", names);
        yaml.set(root + ".player-ids", players.stream().map(UUID::toString).toList());
        yaml.set(root + ".winner-ids", winners.stream().map(UUID::toString).toList());
        yaml.set(root + ".wagered", wagered);
        yaml.set(root + ".house-profit", houseProfit);
        yaml.set(root + ".reason", reason);
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
            String reason = yaml.getString("hands." + key + ".reason",
                    Messages.msg("blackjack.history.reason.default"));
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
