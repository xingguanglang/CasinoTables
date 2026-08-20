package io.github.casinotables.poker;

import io.github.casinotables.CasinoTablesPlugin;
import io.github.casinotables.Messages;
import io.github.casinotables.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PokerRecords {
    record Stats(int games, int wins) {
        double winRate() { return games == 0 ? 0.0 : wins * 100.0 / games; }
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final CasinoTablesPlugin plugin;
    private final File statsFile;
    private final File historyFile;
    private final Map<UUID, MutableStats> stats = new HashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    PokerRecords(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "poker-stats.yml");
        this.historyFile = new File(plugin.getDataFolder(), "poker-history.yml");
        load();
    }

    Stats stats(UUID player) {
        MutableStats value = stats.get(player);
        return value == null ? new Stats(0, 0) : new Stats(value.games, value.wins);
    }

    void record(List<UUID> players, List<String> names, List<UUID> winners, int smallBlind, int bigBlind,
                int carryLimit, int[] initialStacks, int[] cashOuts, List<PokerCard> board,
                int[] finalStacks, String reason, long startedAt) {
        for (UUID id : players) {
            MutableStats value = stats.computeIfAbsent(id, ignored -> new MutableStats());
            value.games++;
            if (winners.contains(id)) value.wins++;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", System.currentTimeMillis());
        entry.put("started-at", startedAt);
        entry.put("players", players.stream().map(UUID::toString).toList());
        entry.put("names", new ArrayList<>(names));
        entry.put("winners", winners.stream().map(UUID::toString).toList());
        entry.put("small-blind", smallBlind);
        entry.put("big-blind", bigBlind);
        entry.put("currency-mode", "vault-real");
        entry.put("carry-limit", carryLimit);
        entry.put("initial-stacks", java.util.Arrays.stream(initialStacks).boxed().toList());
        entry.put("early-cash-outs", java.util.Arrays.stream(cashOuts).boxed().toList());
        entry.put("board", board.stream().map(PokerCard::plainDisplay).toList());
        entry.put("final-stacks", java.util.Arrays.stream(finalStacks).boxed().toList());
        entry.put("reason", reason);
        history.add(0, entry);
        int limit = Math.max(100, plugin.getConfig().getInt("poker.history-limit", 1000));
        while (history.size() > limit) history.removeLast();
        save();
    }

    void showHistory(Player player) {
        String id = player.getUniqueId().toString();
        Stats own = stats(player.getUniqueId());
        Text.send(player, Messages.msg("poker.history.header"));
        Text.send(player, Messages.msg("poker.history.summary", "games", own.games(),
                "wins", own.wins(), "rate", String.format("%.1f%%", own.winRate())));
        int shown = 0;
        for (Map<String, Object> entry : history) {
            if (!(entry.get("players") instanceof List<?> values) || !values.contains(id)) continue;
            long time = number(entry.get("time"), 0L);
            boolean won = entry.get("winners") instanceof List<?> winners && winners.contains(id);
            Object names = entry.get("names");
            int side = values.indexOf(id);
            long initial = listNumber(entry.get("initial-stacks"), side);
            long returned = listNumber(entry.get("early-cash-outs"), side)
                    + listNumber(entry.get("final-stacks"), side);
            String money = "vault-real".equals(entry.get("currency-mode"))
                    ? Messages.msg("poker.history.money", "returned", returned,
                            "net", (returned - initial >= 0 ? "+" : "") + (returned - initial))
                    : "";
            Text.send(player, Messages.msg("poker.history.entry",
                    "result", Messages.msg(won ? "poker.history.won" : "poker.history.lost"),
                    "time", TIME.format(Instant.ofEpochMilli(time)),
                    "names", names,
                    "reason", entry.getOrDefault("reason", ""),
                    "money", money));
            if (++shown >= 10) break;
        }
        if (shown == 0) Text.send(player, Messages.msg("poker.history.empty"));
    }

    void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create the CasinoTables data folder.");
            return;
        }
        YamlConfiguration statsYaml = new YamlConfiguration();
        for (Map.Entry<UUID, MutableStats> entry : stats.entrySet()) {
            String root = entry.getKey().toString();
            statsYaml.set(root + ".games", entry.getValue().games);
            statsYaml.set(root + ".wins", entry.getValue().wins);
        }
        YamlConfiguration historyYaml = new YamlConfiguration();
        historyYaml.set("history", history);
        try {
            statsYaml.save(statsFile);
            historyYaml.save(historyFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save Hold'em records: " + exception.getMessage());
        }
    }

    private void load() {
        if (statsFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(statsFile);
            for (String key : yaml.getKeys(false)) {
                ConfigurationSection section = yaml.getConfigurationSection(key);
                if (section == null) continue;
                try {
                    MutableStats value = new MutableStats();
                    value.games = section.getInt("games");
                    value.wins = section.getInt("wins");
                    stats.put(UUID.fromString(key), value);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        if (historyFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(historyFile);
            List<?> values = yaml.getList("history", List.of());
            for (Object value : values) {
                if (value instanceof Map<?, ?> raw) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    raw.forEach((key, item) -> entry.put(String.valueOf(key), item));
                    history.add(entry);
                }
            }
        }
    }

    private long number(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private long listNumber(Object value, int index) {
        if (!(value instanceof List<?> list) || index < 0 || index >= list.size()) return 0L;
        return number(list.get(index), 0L);
    }

    private static final class MutableStats {
        int games;
        int wins;
    }
}
