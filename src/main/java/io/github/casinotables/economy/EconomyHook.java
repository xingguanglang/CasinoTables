package io.github.casinotables.economy;

import io.github.casinotables.CasinoTablesPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyHook {
    private final CasinoTablesPlugin plugin;
    private Economy economy;

    public EconomyHook(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> registration =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration != null) {
            economy = registration.getProvider();
        }
    }

    public boolean ready() {
        return economy != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        return amount <= 0 || economy.has(player, amount);
    }

    public double balance(OfflinePlayer player) {
        try {
            double balance = economy.getBalance(player);
            return Double.isFinite(balance) ? Math.max(0.0, balance) : 0.0;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to read the balance of " + player.getName()
                    + ": " + throwable.getMessage());
            return 0.0;
        }
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0) return true;
        try {
            EconomyResponse response = economy.withdrawPlayer(player, amount);
            return response != null && response.transactionSuccess();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to withdraw from " + player.getName()
                    + ": " + throwable.getMessage());
            return false;
        }
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) return true;
        try {
            EconomyResponse response = economy.depositPlayer(player, amount);
            return response != null && response.transactionSuccess();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Failed to deposit to " + player.getName()
                    + ": " + throwable.getMessage());
            return false;
        }
    }

    public String format(double amount) {
        try {
            return economy.format(amount);
        } catch (Throwable ignored) {
            return String.format("%.2f", amount);
        }
    }
}
