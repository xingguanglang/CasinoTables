package io.github.xingguanglang.casinotables;

import java.util.Locale;

public enum GameType {
    FLIGHT("flight", false),
    POKER("poker", true),
    BLACKJACK("blackjack", true);

    private final String command;
    private final boolean realCurrency;

    GameType(String command, boolean realCurrency) {
        this.command = command;
        this.realCurrency = realCurrency;
    }

    /** 显示名走语言文件，和 PokerArenaStyle / ArenaShape 一样按枚举名取键。 */
    public String display() { return Messages.msg("game." + name().toLowerCase(Locale.ROOT) + ".display"); }
    public String command() { return command; }
    public boolean usesRealCurrency() { return realCurrency; }

    public static GameType parse(String input) {
        String value = input.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "flight", "fly", "ludo", "飞行棋" -> FLIGHT;
            case "poker", "holdem", "texas", "德州", "德州扑克" -> POKER;
            case "blackjack", "bj", "21", "21点", "二十一点" -> BLACKJACK;
            default -> null;
        };
    }
}
