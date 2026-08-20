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

    /**
     * 底池里真正被人跟过、因而可以抽水的部分。
     *
     * <p>按下注额分层切片：某一层只有一个人出过钱，说明这部分没人跟，那是赢家自己的筹码，
     * 会原样退回，抽它等于凭空罚钱。摊牌和弃牌收底共用这一套算法，
     * 否则「无人跟注的筹码不抽水」这条规则只在摊牌时成立。
     *
     * @param contribution 每个座位本手投入底池的总额，未参与的位置为 0
     */
    public static int contestedPot(int[] contribution) {
        java.util.TreeSet<Integer> levels = new java.util.TreeSet<>();
        for (int value : contribution) if (value > 0) levels.add(value);
        int rakeable = 0;
        int previousLevel = 0;
        for (int level : levels) {
            int contributors = 0;
            for (int value : contribution) if (value >= level) contributors++;
            if (contributors > 1) rakeable += (level - previousLevel) * contributors;
            previousLevel = level;
        }
        return rakeable;
    }
}
