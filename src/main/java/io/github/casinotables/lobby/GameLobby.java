package io.github.casinotables.lobby;

import io.github.casinotables.GameType;
import io.github.casinotables.poker.PokerArenaStyle;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public final class GameLobby {
    private final UUID host;
    private final GameType type;
    private double bet;
    private final int maximum;
    private int smallBlind;
    private int bigBlind;
    private int buyIn;
    private int flightPieces;
    private PokerArenaStyle pokerArenaStyle;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

    GameLobby(UUID host, GameType type, double bet, int maximum, int smallBlind, int bigBlind,
              int buyIn, int flightPieces, PokerArenaStyle pokerArenaStyle) {
        this.host = host;
        this.type = type;
        this.bet = bet;
        this.maximum = maximum;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.buyIn = buyIn;
        this.flightPieces = flightPieces;
        this.pokerArenaStyle = pokerArenaStyle;
        members.add(host);
    }

    public UUID host() { return host; }
    public GameType type() { return type; }
    public double bet() { return bet; }
    public int maximum() { return maximum; }
    public int smallBlind() { return smallBlind; }
    public int bigBlind() { return bigBlind; }
    public int buyIn() { return buyIn; }
    public int flightPieces() { return flightPieces; }
    public PokerArenaStyle pokerArenaStyle() { return pokerArenaStyle; }
    public List<UUID> members() { return List.copyOf(members); }
    boolean add(UUID player) { return members.size() < maximum && members.add(player); }
    boolean remove(UUID player) { return !host.equals(player) && members.remove(player); }
    int size() { return members.size(); }
    void blinds(int smallBlind, int bigBlind) {
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
    }
    void bet(double bet) { this.bet = bet; }
    void buyIn(int buyIn) { this.buyIn = buyIn; }
    void flightPieces(int flightPieces) { this.flightPieces = flightPieces; }
    void pokerArenaStyle(PokerArenaStyle pokerArenaStyle) { this.pokerArenaStyle = pokerArenaStyle; }
}
