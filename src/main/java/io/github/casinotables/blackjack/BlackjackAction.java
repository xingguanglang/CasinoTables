package io.github.casinotables.blackjack;

import io.github.casinotables.Messages;

/** 座位旁实体按钮对应的操作。 */
public enum BlackjackAction {
    BET_MIN("blackjack.button.bet-min"),
    BET_RECLAIM("blackjack.button.bet-reclaim"),
    BET_CONFIRM("blackjack.button.bet-confirm"),
    HIT("blackjack.button.hit"),
    STAND("blackjack.button.stand"),
    DOUBLE("blackjack.button.double"),
    SPLIT("blackjack.button.split"),
    INSURANCE("blackjack.button.insurance"),
    TOP_UP("blackjack.button.top-up"),
    LEAVE_AFTER_HAND("blackjack.button.leave-after-hand");

    /** 按钮文案放在语言文件里，这里只记键名。 */
    private final String key;

    BlackjackAction(String key) { this.key = key; }

    public String display() { return Messages.msg(key); }
}
