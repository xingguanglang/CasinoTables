package io.github.xingguanglang.casinotables.luck;

import io.github.xingguanglang.casinotables.CasinoTablesPlugin;
import io.github.xingguanglang.casinotables.GameType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Persistent, administrator-controlled card-luck weights. No player-facing message is emitted here. */
public final class LuckService {
    private final CasinoTablesPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<GameType, Map<UUID, Integer>> boosts = new EnumMap<>(GameType.class);

    public LuckService(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "luck.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        boosts.put(GameType.POKER, new HashMap<>());
        load(GameType.POKER, "poker");
    }

    public int boost(GameType type, UUID player) {
        Map<UUID, Integer> values = boosts.get(type);
        return values == null ? 0 : values.getOrDefault(player, 0);
    }

    /** @return 1 for a good-luck deal, -1 for a bad-luck deal, or 0 for an ordinary deal. */
    public int roll(GameType type, UUID player) {
        int weight = boost(type, player);
        if (weight == 0 || ThreadLocalRandom.current().nextInt(100) >= Math.abs(weight)) return 0;
        return Integer.signum(weight);
    }

    public void set(GameType type, UUID player, String playerName, int signedPercent) {
        if (type != GameType.POKER) {
            throw new IllegalArgumentException("Luck only supports Texas Hold'em");
        }
        int safe = Math.max(-100, Math.min(100, signedPercent));
        Map<UUID, Integer> values = boosts.get(type);
        if (safe == 0) values.remove(player);
        else values.put(player, safe);
        String root = "players." + player;
        data.set(root + ".name", playerName);
        data.set(root + "." + key(type), safe == 0 ? null : safe);
        save();
    }

    private void load(GameType type, String key) {
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return;
        for (String rawId : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(rawId);
                int value = Math.max(-100, Math.min(100, players.getInt(rawId + "." + key, 0)));
                if (value != 0) boosts.get(type).put(id, value);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in luck.yml: " + rawId);
            }
        }
    }

    private String key(GameType type) {
        return "poker";
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save luck.yml: " + exception.getMessage());
        }
    }
}
