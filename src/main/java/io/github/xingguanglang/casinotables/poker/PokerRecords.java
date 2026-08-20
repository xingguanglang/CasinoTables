package io.github.xingguanglang.casinotables.poker;

import io.github.xingguanglang.casinotables.CasinoTablesPlugin;
import io.github.xingguanglang.casinotables.Messages;
import io.github.xingguanglang.casinotables.Reason;
import io.github.xingguanglang.casinotables.Text;
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

    /** settleDraw 这类没有手数的记录用它占位。 */
    static final int NO_HAND = -1;

    /** 把内层理由套进「第 N 手」或「平局」的外壳里。 */
    private static String renderReason(String key, List<String> args, int handNumber) {
        String inner = Messages.msg(key, args.toArray());
        return handNumber == NO_HAND
                ? Messages.msg("poker.history.draw-reason", "reason", inner)
                : Messages.msg("poker.history.hand-reason", "hand", handNumber, "reason", inner);
    }

    /**
     * 取一条记录的理由。
     *
     * <p>新记录存的是键和参数，按当前语言现场渲染；升级前写下的老记录只有渲染死的
     * reason 字段，那就原样用——不能因为格式换了就让历史一片空白。
     */
    private static String storedReason(Map<String, Object> entry) {
        if (entry.get("reason-key") instanceof String key) {
            List<String> args = new ArrayList<>();
            if (entry.get("reason-args") instanceof List<?> list) {
                for (Object item : list) args.add(String.valueOf(item));
            }
            Object hand = entry.get("hand");
            return renderReason(key, args, hand instanceof Number number ? number.intValue() : NO_HAND);
        }
        return String.valueOf(entry.getOrDefault("reason", ""));
    }

    void record(List<UUID> players, List<String> names, List<UUID> winners, int smallBlind, int bigBlind,
                int carryLimit, int[] initialStacks, int[] cashOuts, List<PokerCard> board,
                int[] finalStacks, Reason reason, int handNumber, long startedAt) {
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
        // 存键和参数，不存渲染好的句子：服主换语言后整段历史跟着换。
        // 同时保留渲染结果，一来给直接翻 yml 的人看，二来给降级回旧版本的读取兜底。
        entry.put("reason-key", reason.key());
        entry.put("reason-args", new ArrayList<>(reason.args()));
        if (handNumber != NO_HAND) entry.put("hand", handNumber);
        entry.put("reason", renderReason(reason.key(), reason.args(), handNumber));
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
            // 直接塞 List 会渲染成 [Steve, Alex]（带方括号），而且绕过语言文件里的分隔符。
            Object rawNames = entry.get("names");
            String names = rawNames instanceof List<?> list
                    ? list.stream().map(String::valueOf)
                            .collect(java.util.stream.Collectors.joining(
                                    Messages.msg("poker.common.name-separator")))
                    : String.valueOf(rawNames == null ? "" : rawNames);
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
                    "reason", storedReason(entry),
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
