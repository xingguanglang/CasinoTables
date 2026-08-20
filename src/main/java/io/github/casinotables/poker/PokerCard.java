package io.github.casinotables.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public record PokerCard(int rank, Suit suit) {
    public enum Suit {
        CLUBS("♣", false), DIAMONDS("♦", true), HEARTS("♥", true), SPADES("♠", false);

        private final String symbol;
        private final boolean red;

        Suit(String symbol, boolean red) {
            this.symbol = symbol;
            this.red = red;
        }

        public String symbol() { return symbol; }
        public boolean red() { return red; }
    }

    public String rankName() {
        return switch (rank) {
            case 14 -> "A";
            case 13 -> "K";
            case 12 -> "Q";
            case 11 -> "J";
            default -> String.valueOf(rank);
        };
    }

    public String display() {
        return (suit.red() ? "<red>" : "<white>") + rankName() + suit.symbol();
    }

    public String plainDisplay() { return rankName() + suit.symbol(); }

    public static List<PokerCard> shuffledDeck() {
        List<PokerCard> cards = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (int rank = 2; rank <= 14; rank++) cards.add(new PokerCard(rank, suit));
        }
        Collections.shuffle(cards, ThreadLocalRandom.current());
        return cards;
    }
}
