package io.github.casinotables.poker;

import io.github.casinotables.Messages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class PokerHandEvaluator {
    private static final long CATEGORY_DIVISOR = 759_375L; // 15^5

    private PokerHandEvaluator() {
    }

    public static long evaluate(List<PokerCard> cards) {
        if (cards.size() < 5 || cards.size() > 7) throw new IllegalArgumentException("hand evaluation needs 5 to 7 cards");
        long best = Long.MIN_VALUE;
        int n = cards.size();
        for (int a = 0; a < n - 4; a++)
            for (int b = a + 1; b < n - 3; b++)
                for (int c = b + 1; c < n - 2; c++)
                    for (int d = c + 1; d < n - 1; d++)
                        for (int e = d + 1; e < n; e++)
                            best = Math.max(best, scoreFive(List.of(cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e))));
        return best;
    }

    public static String name(long score) {
        return Messages.msg(switch ((int) (score / CATEGORY_DIVISOR)) {
            case 8 -> "poker.hand-type.straight-flush";
            case 7 -> "poker.hand-type.four-of-a-kind";
            case 6 -> "poker.hand-type.full-house";
            case 5 -> "poker.hand-type.flush";
            case 4 -> "poker.hand-type.straight";
            case 3 -> "poker.hand-type.three-of-a-kind";
            case 2 -> "poker.hand-type.two-pair";
            case 1 -> "poker.hand-type.pair";
            default -> "poker.hand-type.high-card";
        });
    }

    /** 描述玩家在当前已知牌中的最大组合；两张底牌阶段也能显示对子或高牌。 */
    public static String describeCurrent(List<PokerCard> cards) {
        List<PokerCard> known = cards.stream().filter(java.util.Objects::nonNull).toList();
        if (known.isEmpty()) return Messages.msg("poker.hand-desc.none");
        if (known.size() >= 5) return describeScore(evaluate(known));

        int[] counts = new int[15];
        for (PokerCard card : known) counts[card.rank()]++;
        List<Integer> groups = new ArrayList<>();
        for (int rank = 14; rank >= 2; rank--) if (counts[rank] > 0) groups.add(rank);
        groups.sort(Comparator.<Integer>comparingInt(rank -> counts[rank]).reversed()
                .thenComparing(Comparator.reverseOrder()));
        int first = groups.getFirst();
        if (counts[first] == 4) {
            return Messages.msg("poker.hand-desc.four-of-a-kind", "rank", rankName(first));
        }
        if (counts[first] == 3) {
            return Messages.msg("poker.hand-desc.three-of-a-kind", "rank", rankName(first));
        }
        if (counts[first] == 2 && groups.size() > 1 && counts[groups.get(1)] == 2) {
            return Messages.msg("poker.hand-desc.two-pair", "rank", rankName(first),
                    "kicker", rankName(groups.get(1)));
        }
        if (counts[first] == 2) return Messages.msg("poker.hand-desc.pair", "rank", rankName(first));
        return Messages.msg("poker.hand-desc.high-card", "rank", rankName(first));
    }

    private static String describeScore(long score) {
        int category = (int) (score / CATEGORY_DIVISOR);
        long remainder = score % CATEGORY_DIVISOR;
        int[] values = new int[5];
        for (int index = values.length - 1; index >= 0; index--) {
            values[index] = (int) (remainder % 15);
            remainder /= 15;
        }
        String high = rankName(values[0]);
        String second = rankName(values[1]);
        return switch (category) {
            case 8 -> Messages.msg("poker.hand-desc.straight-flush", "rank", high);
            case 7 -> Messages.msg("poker.hand-desc.four-of-a-kind", "rank", high);
            case 6 -> Messages.msg("poker.hand-desc.full-house", "rank", high, "kicker", second);
            case 5 -> Messages.msg("poker.hand-desc.flush", "rank", high);
            case 4 -> Messages.msg("poker.hand-desc.straight", "rank", high);
            case 3 -> Messages.msg("poker.hand-desc.three-of-a-kind", "rank", high);
            case 2 -> Messages.msg("poker.hand-desc.two-pair", "rank", high, "kicker", second);
            case 1 -> Messages.msg("poker.hand-desc.pair", "rank", high);
            default -> Messages.msg("poker.hand-desc.high-card", "rank", high);
        };
    }

    private static String rankName(int rank) {
        return switch (rank) {
            case 14 -> "A";
            case 13 -> "K";
            case 12 -> "Q";
            case 11 -> "J";
            default -> String.valueOf(rank);
        };
    }

    private static long scoreFive(List<PokerCard> cards) {
        int[] counts = new int[15];
        int[] ranks = new int[5];
        boolean flush = true;
        PokerCard.Suit suit = cards.getFirst().suit();
        for (int i = 0; i < cards.size(); i++) {
            PokerCard card = cards.get(i);
            counts[card.rank()]++;
            ranks[i] = card.rank();
            if (card.suit() != suit) flush = false;
        }
        Arrays.sort(ranks);
        reverse(ranks);
        int straight = straightHigh(counts);
        if (flush && straight > 0) return encode(8, straight);

        List<Integer> groups = new ArrayList<>();
        for (int rank = 2; rank <= 14; rank++) if (counts[rank] > 0) groups.add(rank);
        groups.sort(Comparator.<Integer>comparingInt(rank -> counts[rank]).reversed()
                .thenComparing(Comparator.reverseOrder()));

        if (counts[groups.getFirst()] == 4) return encode(7, groups.get(0), groups.get(1));
        if (counts[groups.getFirst()] == 3 && groups.size() > 1 && counts[groups.get(1)] == 2) {
            return encode(6, groups.get(0), groups.get(1));
        }
        if (flush) return encode(5, ranks);
        if (straight > 0) return encode(4, straight);
        if (counts[groups.getFirst()] == 3) return encode(3, groups.get(0), groups.get(1), groups.get(2));
        if (counts[groups.getFirst()] == 2 && groups.size() > 1 && counts[groups.get(1)] == 2) {
            return encode(2, groups.get(0), groups.get(1), groups.size() > 2 ? groups.get(2) : 0);
        }
        if (counts[groups.getFirst()] == 2) {
            return encode(1, groups.get(0), groups.get(1), groups.get(2), groups.get(3));
        }
        return encode(0, ranks);
    }

    private static int straightHigh(int[] counts) {
        for (int high = 14; high >= 5; high--) {
            boolean found = true;
            for (int rank = high; rank > high - 5; rank--) if (counts[rank] == 0) found = false;
            if (found) return high;
        }
        return counts[14] > 0 && counts[2] > 0 && counts[3] > 0 && counts[4] > 0 && counts[5] > 0 ? 5 : 0;
    }

    private static long encode(int category, int... values) {
        long score = category;
        for (int i = 0; i < 5; i++) score = score * 15 + (i < values.length ? values[i] : 0);
        return score;
    }

    private static void reverse(int[] values) {
        for (int i = 0; i < values.length / 2; i++) {
            int other = values.length - 1 - i;
            int value = values[i];
            values[i] = values[other];
            values[other] = value;
        }
    }
}
