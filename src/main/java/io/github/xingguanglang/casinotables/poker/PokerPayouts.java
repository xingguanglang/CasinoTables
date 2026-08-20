package io.github.xingguanglang.casinotables.poker;

import io.github.xingguanglang.casinotables.CasinoTablesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class PokerPayouts {
    private final CasinoTablesPlugin plugin;
    private final File file;
    private final Map<UUID, Long> pending = new HashMap<>();

    PokerPayouts(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "poker-pending-payouts.yml");
        load();
        retryAll();
    }

    boolean pay(UUID player, long amount, String reason) {
        if (amount <= 0) return true;
        if (plugin.economy().deposit(Bukkit.getOfflinePlayer(player), amount)) return true;
        pending.merge(player, amount, Long::sum);
        save();
        plugin.getLogger().severe("Hold'em payout failed and was queued for retry: " + player
                + " amount=" + amount + " reason=" + reason);
        return false;
    }

    void retry(UUID player) {
        Long amount = pending.get(player);
        if (amount == null || amount <= 0) return;
        if (!plugin.economy().deposit(Bukkit.getOfflinePlayer(player), amount)) return;
        pending.remove(player);
        save();
        plugin.getLogger().info("Paid out a queued Hold'em balance: " + player + " amount=" + amount);
    }

    void save() {
        if (pending.isEmpty() && !file.exists()) return;
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().severe("Could not create the folder for queued Hold'em payouts.");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : pending.entrySet()) {
            yaml.set("pending." + entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save queued Hold'em payouts: " + exception.getMessage());
        }
    }

    private void retryAll() {
        for (UUID player : java.util.List.copyOf(pending.keySet())) retry(player);
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("pending");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                long amount = section.getLong(key);
                if (amount > 0) pending.put(UUID.fromString(key), amount);
            } catch (IllegalArgumentException ignored) { }
        }
    }
}
