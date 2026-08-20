package io.github.xingguanglang.casinotables.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Monte-Carlo Texas Hold'em policy which only uses the bot's cards and public cards. */
public final class PokerBotStrategy {
    public enum Action { CHECK, CALL, RAISE, ALL_IN, FOLD }
    public record Decision(Action action, int raiseSteps, double equity) { }

    private PokerBotStrategy() { }

    public static Decision decide(PokerCard[] hole, List<PokerCard> board, int opponents,
                                  int stack, int toCall, int pot, int samples,
                                  CasinoBot.Profile profile, Random random) {
        // 水平低的 BOT 采样更少，牌力本身就估得毛糙。
        double equity = estimateEquity(hole, board, opponents, profile.scaledSamples(samples), random);
        int safeStack = Math.max(0, stack);
        int safeCall = Math.max(0, Math.min(toCall, safeStack));
        double potOdds = safeCall == 0 ? 0.0 : safeCall / (double) Math.max(1, pot + safeCall);
        double pressure = safeCall / (double) Math.max(1, safeStack);

        // 在采样误差之外再叠一层「看错牌」的偏差：新手常把中等牌当成好牌。
        double misread = (random.nextDouble() - 0.5) * profile.misreadRange();
        double adjusted = equity + misread
                + profile.aggression() * 0.035 - profile.tightness() * 0.025;
        // 水平越低越不看底池赔率；高手会严格按赔率决定跟不跟。
        double oddsWeight = 0.25 + 0.75 * profile.skill();
        boolean bluff = random.nextDouble() < bluffChance(profile, opponents, safeCall);

        if (safeStack == 0) return new Decision(Action.CHECK, 0, equity);
        if (safeCall >= safeStack) {
            return new Decision(adjusted >= Math.max(0.48, potOdds + 0.10) || (bluff && pressure < 0.45)
                    ? Action.ALL_IN : Action.FOLD, 0, equity);
        }
        if (safeCall > 0 && !bluff
                && adjusted < potOdds * oddsWeight + 0.07
                + pressure * (0.16 + profile.tightness() * 0.12)) {
            return new Decision(Action.FOLD, 0, equity);
        }
        double raiseThreshold = 0.70 - profile.aggression() * 0.16 + opponents * 0.018;
        if ((adjusted >= raiseThreshold || bluff) && pressure < 0.42) {
            int steps = adjusted > 0.84 ? 3 : adjusted > 0.72 ? 2 : 1;
            if (adjusted > 0.92 && safeStack <= Math.max(1, pot) * 2) {
                return new Decision(Action.ALL_IN, 0, equity);
            }
            return new Decision(Action.RAISE, steps, equity);
        }
        return new Decision(safeCall == 0 ? Action.CHECK : Action.CALL, 0, equity);
    }

    /**
     * 高手挑人少的时候诈唬，新手则不分场合乱诈唬。
     */
    private static double bluffChance(CasinoBot.Profile profile, int opponents, int toCall) {
        double base = profile.bluff() * (toCall == 0 ? 1.0 : 0.45);
        if (profile.skill() < 0.5) return base;
        double fewOpponents = opponents <= 2 ? 1.25 : opponents >= 4 ? 0.45 : 0.85;
        return base * fewOpponents;
    }

    public static double estimateEquity(PokerCard[] hole, List<PokerCard> board, int opponents,
                                        int samples, Random random) {
        if (hole == null || hole.length != 2 || hole[0] == null || hole[1] == null) return 0.0;
        int safeOpponents = Math.max(1, Math.min(5, opponents));
        int safeSamples = Math.max(20, Math.min(1000, samples));
        List<PokerCard> available = fullDeck();
        available.remove(hole[0]);
        available.remove(hole[1]);
        for (PokerCard card : board) available.remove(card);
        double wins = 0.0;
        for (int sample = 0; sample < safeSamples; sample++) {
            Collections.shuffle(available, random);
            int cursor = 0;
            List<PokerCard> completedBoard = new ArrayList<>(board);
            while (completedBoard.size() < 5) completedBoard.add(available.get(cursor++));
            List<PokerCard> own = new ArrayList<>(completedBoard);
            own.add(hole[0]);
            own.add(hole[1]);
            long ownScore = PokerHandEvaluator.evaluate(own);
            int tied = 1;
            boolean beaten = false;
            for (int opponent = 0; opponent < safeOpponents; opponent++) {
                List<PokerCard> other = new ArrayList<>(completedBoard);
                other.add(available.get(cursor++));
                other.add(available.get(cursor++));
                long otherScore = PokerHandEvaluator.evaluate(other);
                if (otherScore > ownScore) {
                    beaten = true;
                    break;
                }
                if (otherScore == ownScore) tied++;
            }
            if (!beaten) wins += 1.0 / tied;
        }
        return wins / safeSamples;
    }

    private static List<PokerCard> fullDeck() {
        List<PokerCard> cards = new ArrayList<>(52);
        for (PokerCard.Suit suit : PokerCard.Suit.values()) {
            for (int rank = 2; rank <= 14; rank++) cards.add(new PokerCard(rank, suit));
        }
        return cards;
    }
}
