package io.github.casinotables.economy;

import io.github.casinotables.CasinoTablesPlugin;
import io.github.casinotables.Messages;
import io.github.casinotables.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class WagerService {
    private final CasinoTablesPlugin plugin;
    private final EconomyHook economy;

    public WagerService(CasinoTablesPlugin plugin, EconomyHook economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public boolean escrow(Player first, Player second, double bet) {
        return escrow(List.of(first, second), bet);
    }

    public boolean escrow(List<Player> players, double bet) {
        for (Player player : players) {
            if (!economy.has(player, bet)) {
                for (Player member : players) {
                    Text.send(member, Messages.msg("economy.escrow.insufficient",
                            "player", player.getName(), "amount", economy.format(bet)));
                }
                return false;
            }
        }
        List<Player> charged = new ArrayList<>();
        for (Player player : players) {
            if (!economy.withdraw(player, bet)) {
                for (Player paid : charged) economy.deposit(paid, bet);
                for (Player member : players) Text.send(member, Messages.msg("economy.escrow.failed"));
                return false;
            }
            charged.add(player);
        }
        return true;
    }

    public void win(OfflinePlayer winner, double bet) {
        payWinners(List.of(winner), bet, 2);
    }

    public double payWinners(Collection<? extends OfflinePlayer> winners, double bet, int playerCount) {
        if (winners.isEmpty()) return 0;
        double grossPool = bet * playerCount;
        double payoutRate = Math.max(0.0, Math.min(1.0,
                plugin.getConfig().getDouble("economy.payout-multiplier", 1.9) / 2.0));
        double totalPayout = grossPool * payoutRate;
        double share = totalPayout / winners.size();
        for (OfflinePlayer winner : winners) economy.deposit(winner, share);
        double fee = grossPool - totalPayout;
        String feeAccount = plugin.getConfig().getString("economy.fee-account", "").trim();
        if (fee > 0 && !feeAccount.isEmpty()) {
            economy.deposit(Bukkit.getOfflinePlayer(feeAccount), fee);
        }
        return share;
    }

    /** 按总奖金池比例依名次发奖；未发出的份额与比例余数均计入系统手续费。 */
    public double[] payRanked(List<? extends OfflinePlayer> standings, double bet,
                              int playerCount, double[] rates) {
        double grossPool = Math.max(0.0, bet * playerCount);
        double[] payouts = new double[standings.size()];
        double totalPayout = 0.0;
        for (int rank = 0; rank < standings.size() && rank < rates.length; rank++) {
            double safeRate = Math.max(0.0, Math.min(1.0, rates[rank]));
            double amount = grossPool * safeRate;
            payouts[rank] = amount;
            totalPayout += amount;
            economy.deposit(standings.get(rank), amount);
        }
        double fee = Math.max(0.0, grossPool - Math.min(grossPool, totalPayout));
        String feeAccount = plugin.getConfig().getString("economy.fee-account", "").trim();
        if (fee > 0 && !feeAccount.isEmpty()) {
            economy.deposit(Bukkit.getOfflinePlayer(feeAccount), fee);
        }
        return payouts;
    }

    public void refund(OfflinePlayer first, OfflinePlayer second, double bet) {
        refund(List.of(first, second), bet);
    }

    public void refund(Collection<? extends OfflinePlayer> players, double bet) {
        for (OfflinePlayer player : players) economy.deposit(player, bet);
    }

    public double refundWithFee(Collection<? extends OfflinePlayer> players, double bet, double feeRate) {
        double safeRate = Math.max(0.0, Math.min(1.0, feeRate));
        double refund = bet * (1.0 - safeRate);
        for (OfflinePlayer player : players) economy.deposit(player, refund);
        double totalFee = (bet - refund) * players.size();
        String feeAccount = plugin.getConfig().getString("economy.fee-account", "").trim();
        if (totalFee > 0 && !feeAccount.isEmpty()) {
            economy.deposit(Bukkit.getOfflinePlayer(feeAccount), totalFee);
        }
        return refund;
    }

    public String format(double amount) {
        return economy.format(amount);
    }
}
