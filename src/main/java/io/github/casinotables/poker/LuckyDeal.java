package io.github.casinotables.poker;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Selects the best of a small random sample, producing an advantage without guaranteeing a premium hand. */
public final class LuckyDeal {
    private static final int TEXAS_CANDIDATES = 8;

    private LuckyDeal() {
    }

    public static PokerCard[] takeTexas(List<PokerCard> deck) {
        return takeTexas(deck, true);
    }

    public static PokerCard[] takeBadTexas(List<PokerCard> deck) {
        return takeTexas(deck, false);
    }

    private static PokerCard[] takeTexas(List<PokerCard> deck, boolean bestWanted) {
        List<PokerCard[]> candidates = takeCandidates(deck, 2, TEXAS_CANDIDATES);
        PokerCard[] best = candidates.getFirst();
        for (PokerCard[] candidate : candidates) {
            int compared = Long.compare(texasScore(candidate), texasScore(best));
            if (bestWanted ? compared > 0 : compared < 0) best = candidate;
        }
        restoreUnused(deck, candidates, best);
        return best;
    }

    public static long texasScore(PokerCard[] pair) {
        if (pair == null || pair.length != 2 || pair[0] == null || pair[1] == null) {
            throw new IllegalArgumentException("Texas hold'em requires exactly two cards");
        }
        int high = Math.max(pair[0].rank(), pair[1].rank());
        int low = Math.min(pair[0].rank(), pair[1].rank());
        if (high == low) return 100_000L + high * 1_000L;
        long score = high * 1_000L + low * 40L;
        if (pair[0].suit() == pair[1].suit()) score += 900L;
        int gap = high - low;
        if (gap == 1) score += 700L;
        else if (gap == 2) score += 350L;
        else if (gap == 3) score += 100L;
        if (high >= 11 && low >= 10) score += 1_000L;
        if (high == 14) score += 400L;
        return score;
    }

    private static List<PokerCard[]> takeCandidates(List<PokerCard> deck, int cards, int requestedCandidates) {
        int candidateCount = Math.min(requestedCandidates, deck.size() / cards);
        if (candidateCount < 1) throw new IllegalArgumentException("Not enough cards for lucky deal");
        List<PokerCard[]> result = new ArrayList<>(candidateCount);
        for (int candidate = 0; candidate < candidateCount; candidate++) {
            PokerCard[] hand = new PokerCard[cards];
            for (int card = 0; card < cards; card++) hand[card] = deck.removeFirst();
            result.add(hand);
        }
        return result;
    }

    private static void restoreUnused(List<PokerCard> deck, List<PokerCard[]> candidates, PokerCard[] selected) {
        for (PokerCard[] candidate : candidates) {
            if (candidate == selected) continue;
            Collections.addAll(deck, candidate);
        }
        Collections.shuffle(deck, ThreadLocalRandom.current());
    }
}
