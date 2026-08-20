package io.github.casinotables.blackjack;

import io.github.casinotables.Messages;
import io.github.casinotables.poker.PokerCard;

import java.util.List;

/** 21 点手牌估值：A 自动按 1 或 11 取最优，并区分软硬牌。 */
public final class BlackjackHand {
    public static final int TARGET = 21;
    /** 庄家点数达到该值即停牌；软 17 同样停牌。 */
    public static final int DEALER_STAND = 17;

    private BlackjackHand() { }

    /** 单张牌的基础点数：A 先记 11，J/Q/K 记 10。 */
    public static int cardValue(PokerCard card) {
        int rank = card.rank();
        if (rank == 14) return 11;
        return Math.min(rank, 10);
    }

    public static boolean ace(PokerCard card) { return card.rank() == 14; }

    /** 不爆牌前提下的最大点数；实在爆了就返回把所有 A 都当 1 的最小点数。 */
    public static int value(List<PokerCard> cards) {
        int total = 0;
        int aces = 0;
        for (PokerCard card : cards) {
            total += cardValue(card);
            if (ace(card)) aces++;
        }
        while (total > TARGET && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    /** 仍有 A 被当作 11 使用时为软牌。 */
    public static boolean soft(List<PokerCard> cards) {
        int total = 0;
        int aces = 0;
        for (PokerCard card : cards) {
            total += cardValue(card);
            if (ace(card)) aces++;
        }
        while (total > TARGET && aces > 0) {
            total -= 10;
            aces--;
        }
        return aces > 0;
    }

    public static boolean bust(List<PokerCard> cards) { return value(cards) > TARGET; }

    /** 只有起手两张凑成 21 才是黑杰克；分牌后拿到的 21 不算。 */
    public static boolean blackjack(List<PokerCard> cards) {
        return cards.size() == 2 && value(cards) == TARGET;
    }

    /** 起手两张点数相同即可分牌，K+Q 这类同为 10 点的牌也算。 */
    public static boolean splittable(List<PokerCard> cards) {
        return cards.size() == 2 && cardValue(cards.get(0)) == cardValue(cards.get(1));
    }

    /** 庄家是否必须继续要牌：硬软一律 17 停。 */
    public static boolean dealerMustHit(List<PokerCard> cards) {
        return value(cards) < DEALER_STAND;
    }

    public static String describe(List<PokerCard> cards) {
        if (cards.isEmpty()) return Messages.msg("blackjack.hand.none");
        int total = value(cards);
        if (blackjack(cards)) return Messages.msg("blackjack.hand.blackjack");
        if (total > TARGET) return Messages.msg("blackjack.hand.bust", "total", total);
        return Messages.msg(soft(cards) ? "blackjack.hand.soft" : "blackjack.hand.hard", "total", total);
    }
}
