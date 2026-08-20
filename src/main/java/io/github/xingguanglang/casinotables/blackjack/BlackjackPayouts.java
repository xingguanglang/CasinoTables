package io.github.xingguanglang.casinotables.blackjack;

import io.github.xingguanglang.casinotables.CasinoTablesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Vault 暂时入账失败时持久化挂账，避免玩家真钱丢失。 */
final class BlackjackPayouts {
    private final CasinoTablesPlugin plugin;
    private final File file;
    private final Map<UUID, Long> pending = new HashMap<>();

    BlackjackPayouts(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "blackjack-pending-payouts.yml");
        load();
        retryAll();
    }

    boolean pay(UUID player, long amount, String reason) {
        if (amount <= 0) return true;
        if (plugin.economy().deposit(Bukkit.getOfflinePlayer(player), amount)) return true;
        pending.merge(player, amount, Long::sum);
        save();
        plugin.getLogger().severe("Blackjack payout failed and was queued for retry: " + player
                + " amount=" + amount + " reason=" + reason);
        return false;
    }

    void retry(UUID player) {
        Long amount = pending.get(player);
        if (amount == null || amount <= 0 || !plugin.economy().deposit(Bukkit.getOfflinePlayer(player), amount)) return;
        pending.remove(player);
        save();
    }

    void save() {
        if (pending.isEmpty() && !file.exists()) return;
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        pending.forEach((player, amount) -> yaml.set("pending." + player, amount));
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save pending blackjack payouts: " + exception.getMessage());
        }
    }

    private void retryAll() {
        for (UUID player : List.copyOf(pending.keySet())) retry(player);
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
