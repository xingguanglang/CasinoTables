package io.github.casinotables.poker;

public final class PokerMoney {
    private PokerMoney() { }

    public static int carryAmount(double balance, int carryLimit) {
        if (!Double.isFinite(balance) || balance <= 0 || carryLimit <= 0) return 0;
        return (int) Math.min(carryLimit, Math.floor(balance + 1.0E-7));
    }

    public static int topUpRoom(int carryLimit, int stack, int handContribution,
                                int queuedRebuy, boolean handActive) {
        long counted = Math.max(0, stack) + Math.max(0, queuedRebuy)
                + (handActive ? Math.max(0, handContribution) : 0L);
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) carryLimit - counted));
    }
}
