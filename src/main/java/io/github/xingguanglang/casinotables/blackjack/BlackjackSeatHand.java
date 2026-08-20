package io.github.xingguanglang.casinotables.blackjack;

import io.github.xingguanglang.casinotables.poker.PokerCard;

import java.util.ArrayList;
import java.util.List;

/** 一个座位上的一手牌；分牌后同一座位会有多手。 */
public final class BlackjackSeatHand {
    private final List<PokerCard> cards = new ArrayList<>(2);
    private int bet;
    private boolean doubled;
    private boolean finished;
    /** 由分牌产生，用于禁止把分牌后的 21 当成黑杰克，以及限制分 A 之后继续要牌。 */
    private boolean fromSplit;
    private boolean splitAce;
    /** 结算文案，摊牌后写入。 */
    private String outcome = "";
    private int payout;

    public BlackjackSeatHand(int bet) { this.bet = bet; }

    public List<PokerCard> cards() { return cards; }
    public void add(PokerCard card) { cards.add(card); }

    public int bet() { return bet; }
    public void bet(int value) { bet = value; }

    public boolean doubled() { return doubled; }
    public void doubled(boolean value) { doubled = value; }

    public boolean finished() { return finished; }
    public void finish() { finished = true; }

    public boolean fromSplit() { return fromSplit; }
    public void fromSplit(boolean value) { fromSplit = value; }

    public boolean splitAce() { return splitAce; }
    public void splitAce(boolean value) { splitAce = value; }

    public String outcome() { return outcome; }
    public void outcome(String value) { outcome = value; }

    public int payout() { return payout; }
    public void payout(int value) { payout = value; }

    public int value() { return BlackjackHand.value(cards); }
    public boolean bust() { return BlackjackHand.bust(cards); }

    /** 分牌得到的 21 只算普通 21 点，不享受 3:2。 */
    public boolean blackjack() { return !fromSplit && BlackjackHand.blackjack(cards); }

    public boolean canDouble(int stack) {
        return !finished && !doubled && cards.size() == 2 && !splitAce && stack >= bet;
    }

    public boolean canSplit(int stack, int handCount, int maxHands) {
        return !finished && !doubled && handCount < maxHands && !splitAce
                && BlackjackHand.splittable(cards) && stack >= bet;
    }

    public boolean canHit() {
        return !finished && !splitAce && !bust() && value() < BlackjackHand.TARGET;
    }
}
