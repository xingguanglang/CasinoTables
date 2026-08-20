package io.github.casinotables.blackjack;

import io.github.casinotables.CasinoTablesPlugin;
import io.github.casinotables.Messages;
import io.github.casinotables.Text;
import io.github.casinotables.arena.ArenaWorld;
import io.github.casinotables.poker.PokerArenaStyle;
import io.github.casinotables.poker.PokerCard;
import io.github.casinotables.poker.PokerChips;
import io.github.casinotables.poker.PokerMoney;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** 一桌 21 点：1～6 名真人各自与荷官对赌，支持要牌、停牌、双倍、分牌和保险。 */
final class BlackjackGame {
    static final int MAX_SEATS = 6;
    /** 一个座位最多分成四手牌。 */
    private static final int MAX_HANDS = 4;

    private enum Phase {
        BETTING("blackjack.phase.betting"), INSURANCE("blackjack.phase.insurance"),
        ACTIONS("blackjack.phase.actions"), DEALER("blackjack.phase.dealer"),
        SETTLED("blackjack.phase.settled");

        /** 阶段名连同颜色一起放在语言文件里。 */
        private final String key;

        Phase(String key) { this.key = key; }

        String display() { return Messages.msg(key); }
    }

    private final CasinoTablesPlugin plugin;
    private final BlackjackManager manager;
    private final BlackjackArena arena;
    private final UUID hostId;
    /** 开局时定死的房主名。座位名会被「空位」这类本地化文本覆盖，不能拿来当身份。 */
    private final String hostName;

    private final UUID[] players = new UUID[MAX_SEATS];
    private final String[] names = new String[MAX_SEATS];
    private final boolean[] seated = new boolean[MAX_SEATS];
    private final boolean[] leaveAfterHand = new boolean[MAX_SEATS];
    private final int[] stack = new int[MAX_SEATS];
    private final int[] initialStacks = new int[MAX_SEATS];
    private final int[] pendingBet = new int[MAX_SEATS];
    private final boolean[] betConfirmed = new boolean[MAX_SEATS];
    private final boolean[] inHand = new boolean[MAX_SEATS];
    private final int[] insurance = new int[MAX_SEATS];
    private final boolean[] insuranceAnswered = new boolean[MAX_SEATS];
    private final int[] activeHand = new int[MAX_SEATS];
    private final List<List<BlackjackSeatHand>> hands = new ArrayList<>(MAX_SEATS);

    private final List<PokerCard> shoe = new ArrayList<>();
    private int shoeIndex;
    private final List<PokerCard> dealer = new ArrayList<>();
    private boolean holeRevealed;

    private final int carryLimit;
    private final int minBet;
    private final int maxBet;
    private final int deckCount;
    private final int actionTimeoutSeconds;
    private final int betTimeoutSeconds;

    private Phase phase = Phase.BETTING;
    private int actor = -1;
    private int handNumber;
    private long deadline;
    private long resumeAt;
    private boolean ended;
    private final long startedAt = System.currentTimeMillis();

    BlackjackGame(CasinoTablesPlugin plugin, BlackjackManager manager, ArenaWorld arenaWorld, int slot,
                  List<Player> participants, int[] buyIns, int carryLimit, int minBet, int maxBet,
                  PokerArenaStyle style) {
        this.plugin = plugin;
        this.manager = manager;
        this.hostId = participants.getFirst().getUniqueId();
        this.hostName = participants.getFirst().getName();
        this.carryLimit = carryLimit;
        this.minBet = Math.max(1, minBet);
        this.maxBet = Math.max(this.minBet, maxBet);
        this.deckCount = Math.max(1, Math.min(8, plugin.getConfig().getInt("blackjack.deck-count", 6)));
        this.actionTimeoutSeconds = Math.max(5, Math.min(600,
                plugin.getConfig().getInt("blackjack.action-timeout-seconds", 30)));
        this.betTimeoutSeconds = Math.max(5, Math.min(600,
                plugin.getConfig().getInt("blackjack.bet-timeout-seconds", 20)));

        for (int side = 0; side < MAX_SEATS; side++) {
            hands.add(new ArrayList<>());
            players[side] = new UUID(0L, side + 1L);
            names[side] = Messages.msg("blackjack.seat.empty-name");
        }
        for (int side = 0; side < participants.size(); side++) {
            Player player = participants.get(side);
            players[side] = player.getUniqueId();
            names[side] = player.getName();
            seated[side] = true;
            stack[side] = buyIns[side];
            initialStacks[side] = buyIns[side];
        }
        this.arena = new BlackjackArena(plugin, arenaWorld, slot, players, names, participants, style,
                plugin.lobbies().arenaShape(hostId));
        for (Player player : participants) manager.attached(this, player.getUniqueId());
        refillShoe();
        startHand();
    }

    // ---------------------------------------------------------------- 牌靴

    private void refillShoe() {
        shoe.clear();
        for (int deck = 0; deck < deckCount; deck++) shoe.addAll(PokerCard.shuffledDeck());
        Collections.shuffle(shoe, java.util.concurrent.ThreadLocalRandom.current());
        shoeIndex = 0;
    }

    /** 牌靴剩余不足以打完一手时重新洗牌，避免中途抽空。 */
    private void ensureShoe(int required) {
        if (shoeIndex + required >= shoe.size()) {
            refillShoe();
            broadcast(Messages.msg("blackjack.shoe.reshuffled"));
        }
    }

    private PokerCard draw() {
        ensureShoe(1);
        return shoe.get(shoeIndex++);
    }

    // ---------------------------------------------------------------- 每手流程

    private void startHand() {
        if (ended) return;
        handNumber++;
        holeRevealed = false;
        dealer.clear();
        phase = Phase.BETTING;
        actor = -1;
        arena.syncDealer(dealer, false);
        for (int side = 0; side < MAX_SEATS; side++) {
            hands.get(side).clear();
            activeHand[side] = 0;
            insurance[side] = 0;
            insuranceAnswered[side] = false;
            inHand[side] = false;
            betConfirmed[side] = false;
            pendingBet[side] = 0;
        }
        // 每手至少需要 6 张公共余量，加上分牌与补牌的富余。
        ensureShoe(MAX_SEATS * 6 + 12);
        deadline = System.currentTimeMillis() + betTimeoutSeconds * 1000L;
        broadcast(Messages.msg("blackjack.hand.betting-open",
                "hand", handNumber, "seconds", betTimeoutSeconds));
        sync();
    }

    private int affordable(int side) {
        return Math.max(0, Math.min(maxBet, stack[side]));
    }

    private void finishBetting() {
        phase = Phase.INSURANCE;
        int active = 0;
        for (int side = 0; side < MAX_SEATS; side++) {
            if (!seated[side]) continue;
            int bet = Math.min(pendingBet[side], stack[side]);
            if (bet < minBet) {
                pendingBet[side] = 0;
                send(side, Messages.msg("blackjack.bet.below-minimum", "min", minBet));
                continue;
            }
            stack[side] -= bet;
            pendingBet[side] = 0;
            BlackjackSeatHand hand = new BlackjackSeatHand(bet);
            hands.get(side).add(hand);
            inHand[side] = true;
            active++;
        }
        if (active == 0) {
            broadcast(Messages.msg("blackjack.hand.no-bets"));
            endHand();
            return;
        }
        dealOpeningCards();
    }

    private void dealOpeningCards() {
        for (int round = 0; round < 2; round++) {
            for (int side = 0; side < MAX_SEATS; side++) {
                if (!inHand[side]) continue;
                hands.get(side).getFirst().add(draw());
            }
            dealer.add(draw());
        }
        arena.syncDealer(dealer, false);
        broadcastHands(Messages.msg("blackjack.hand.dealt-header"));

        if (BlackjackHand.ace(dealer.getFirst())) {
            deadline = System.currentTimeMillis() + Math.max(5, betTimeoutSeconds / 2) * 1000L;
            broadcast(Messages.msg("blackjack.insurance.offer"));
            sync();
            return;
        }
        // 明牌为 10 点时荷官暗中查看暗牌，若成黑杰克立即结束本手。
        if (BlackjackHand.cardValue(dealer.getFirst()) == 10 && BlackjackHand.blackjack(dealer)) {
            broadcast(Messages.msg("blackjack.dealer.blackjack"));
            dealerTurnDone();
            return;
        }
        beginActions();
    }

    private void finishInsurance() {
        boolean dealerBlackjack = BlackjackHand.blackjack(dealer);
        for (int side = 0; side < MAX_SEATS; side++) {
            if (!inHand[side] || insurance[side] <= 0) continue;
            send(side, Messages.msg(dealerBlackjack
                    ? "blackjack.insurance.hit"
                    : "blackjack.insurance.miss"));
        }
        if (dealerBlackjack) {
            broadcast(Messages.msg("blackjack.dealer.blackjack"));
            dealerTurnDone();
            return;
        }
        beginActions();
    }

    private void beginActions() {
        phase = Phase.ACTIONS;
        for (int side = 0; side < MAX_SEATS; side++) {
            if (!inHand[side]) continue;
            // 起手黑杰克无需行动。
            for (BlackjackSeatHand hand : hands.get(side)) {
                if (hand.blackjack()) hand.finish();
            }
        }
        actor = nextActor(-1);
        if (actor < 0) {
            dealerTurn();
            return;
        }
        resetActionDeadline();
        announceTurn();
        sync();
    }

    private int nextActor(int after) {
        for (int step = 1; step <= MAX_SEATS; step++) {
            int side = (after + step) % MAX_SEATS;
            if (!inHand[side]) continue;
            int index = firstUnfinished(side);
            if (index < 0) continue;
            activeHand[side] = index;
            return side;
        }
        return -1;
    }

    private int firstUnfinished(int side) {
        List<BlackjackSeatHand> list = hands.get(side);
        for (int index = 0; index < list.size(); index++) {
            if (!list.get(index).finished()) return index;
        }
        return -1;
    }

    private void advance() {
        if (ended || phase != Phase.ACTIONS) return;
        int index = firstUnfinished(actor);
        if (index >= 0) {
            activeHand[actor] = index;
            resetActionDeadline();
            announceTurn();
            sync();
            return;
        }
        int next = nextActor(actor);
        if (next < 0) {
            dealerTurn();
            return;
        }
        actor = next;
        resetActionDeadline();
        announceTurn();
        sync();
    }

    private void resetActionDeadline() {
        deadline = System.currentTimeMillis() + actionTimeoutSeconds * 1000L;
    }

    private void announceTurn() {
        if (actor < 0) return;
        BlackjackSeatHand hand = current(actor);
        if (hand == null) return;
        send(actor, Messages.msg("blackjack.turn.dealer-upcard",
                "card", BlackjackArena.cardText(dealer.getFirst()),
                "value", BlackjackHand.cardValue(dealer.getFirst())));
        // 可选操作是一串并列词，用语言文件里的分隔符拼起来。
        StringBuilder options = new StringBuilder(Messages.msg("blackjack.turn.option.hit"))
                .append(Messages.msg("blackjack.turn.option-separator"))
                .append(Messages.msg("blackjack.turn.option.stand"));
        if (hand.canDouble(stack[actor])) {
            options.append(Messages.msg("blackjack.turn.option-separator"))
                    .append(Messages.msg("blackjack.turn.option.double"));
        }
        if (hand.canSplit(stack[actor], hands.get(actor).size(), MAX_HANDS)) {
            options.append(Messages.msg("blackjack.turn.option-separator"))
                    .append(Messages.msg("blackjack.turn.option.split"));
        }
        send(actor, Messages.msg("blackjack.turn.prompt",
                "hand", handSummary(hand), "options", options.toString()));
    }

    private BlackjackSeatHand current(int side) {
        List<BlackjackSeatHand> list = hands.get(side);
        int index = activeHand[side];
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    private void dealerTurn() {
        phase = Phase.DEALER;
        actor = -1;
        holeRevealed = true;
        arena.syncDealer(dealer, true);
        boolean anyLive = false;
        for (int side = 0; side < MAX_SEATS; side++) {
            if (!inHand[side]) continue;
            for (BlackjackSeatHand hand : hands.get(side)) {
                if (!hand.bust()) anyLive = true;
            }
        }
        broadcast(Messages.msg("blackjack.dealer.reveal",
                "cards", cardsText(dealer), "total", BlackjackHand.describe(dealer)));
        if (anyLive) {
            while (BlackjackHand.dealerMustHit(dealer)) {
                dealer.add(draw());
                broadcast(Messages.msg("blackjack.dealer.draw",
                        "card", BlackjackArena.cardText(dealer.getLast()),
                        "total", BlackjackHand.describe(dealer)));
            }
        } else {
            broadcast(Messages.msg("blackjack.dealer.no-draw"));
        }
        arena.syncDealer(dealer, true);
        dealerTurnDone();
    }

    private void dealerTurnDone() {
        holeRevealed = true;
        arena.syncDealer(dealer, true);
        settle();
    }

    // ---------------------------------------------------------------- 结算

    private void settle() {
        phase = Phase.SETTLED;
        actor = -1;
        boolean dealerBlackjack = BlackjackHand.blackjack(dealer);
        boolean dealerBust = BlackjackHand.bust(dealer);
        int dealerValue = BlackjackHand.value(dealer);
        long staked = 0;
        long returned = 0;
        List<UUID> winners = new ArrayList<>();
        List<String> lines = new ArrayList<>();

        for (int side = 0; side < MAX_SEATS; side++) {
            if (!inHand[side]) continue;
            int seatReturn = 0;
            int seatStake = 0;
            for (BlackjackSeatHand hand : hands.get(side)) {
                seatStake += hand.bet();
                int payout;
                String outcome;
                if (hand.bust()) {
                    payout = 0;
                    outcome = Messages.msg("blackjack.outcome.bust", "amount", hand.bet());
                } else if (hand.blackjack() && !dealerBlackjack) {
                    payout = hand.bet() + hand.bet() * 3 / 2;
                    outcome = Messages.msg("blackjack.outcome.blackjack", "amount", payout - hand.bet());
                } else if (dealerBlackjack && !hand.blackjack()) {
                    payout = 0;
                    outcome = Messages.msg("blackjack.outcome.dealer-blackjack", "amount", hand.bet());
                } else if (dealerBlackjack) {
                    payout = hand.bet();
                    outcome = Messages.msg("blackjack.outcome.push-blackjack");
                } else if (dealerBust) {
                    payout = hand.bet() * 2;
                    outcome = Messages.msg("blackjack.outcome.dealer-bust", "amount", hand.bet());
                } else if (hand.value() > dealerValue) {
                    payout = hand.bet() * 2;
                    outcome = Messages.msg("blackjack.outcome.win", "amount", hand.bet());
                } else if (hand.value() == dealerValue) {
                    payout = hand.bet();
                    outcome = Messages.msg("blackjack.outcome.push");
                } else {
                    payout = 0;
                    outcome = Messages.msg("blackjack.outcome.lose", "amount", hand.bet());
                }
                hand.payout(payout);
                hand.outcome(outcome);
                seatReturn += payout;
                lines.add(Messages.msg("blackjack.settle.line",
                        "player", names[side], "hand", handSummary(hand), "outcome", outcome));
            }
            if (insurance[side] > 0) {
                seatStake += insurance[side];
                if (dealerBlackjack) {
                    seatReturn += insurance[side] * 3;
                    lines.add(Messages.msg("blackjack.settle.insurance-win",
                            "player", names[side], "amount", insurance[side] * 2));
                } else {
                    lines.add(Messages.msg("blackjack.settle.insurance-lose",
                            "player", names[side], "amount", insurance[side]));
                }
            }
            stack[side] += seatReturn;
            staked += seatStake;
            returned += seatReturn;
            if (seatReturn > seatStake) winners.add(players[side]);
        }

        broadcast(Messages.msg("blackjack.settle.header", "hand", handNumber));
        for (String line : lines) broadcast(line);
        StringBuilder assets = new StringBuilder();
        for (int side = 0; side < MAX_SEATS; side++) {
            if (!seated[side]) continue;
            if (!assets.isEmpty()) assets.append(Messages.msg("blackjack.settle.stack-separator"));
            assets.append(Messages.msg("blackjack.settle.stack-entry",
                    "player", names[side], "chips", stack[side]));
        }
        broadcast(Messages.msg("blackjack.settle.stacks", "stacks", assets.toString()));

        int houseProfit = (int) Math.max(0, staked - returned);
        manager.collectHouseProfit(houseProfit);
        manager.recordHand(this, winners, (int) staked, houseProfit,
                Messages.msg(dealerBust ? "blackjack.history.reason.dealer-bust"
                        : "blackjack.history.reason.dealer-total", "total", BlackjackHand.value(dealer)));
        arena.celebrate(winners);
        arena.announce(Messages.msg("blackjack.announce.hand-over", "hand", handNumber),
                Messages.msg("blackjack.announce.hand-detail",
                        "dealer", BlackjackHand.describe(dealer),
                        "seconds", Math.max(1, nextHandDelaySeconds())), (int) staked);
        sync();
        resumeAt = System.currentTimeMillis() + nextHandDelaySeconds() * 1000L;
    }

    private int nextHandDelaySeconds() {
        return Math.max(1, plugin.getConfig().getInt("blackjack.next-hand-delay-seconds", 8));
    }

    private void endHand() {
        resumeAt = System.currentTimeMillis() + 2000L;
        phase = Phase.SETTLED;
        sync();
    }

    /** 结算展示结束后处理离桌、清空筹码和补码，然后开下一手。 */
    private void continueOrFinish() {
        if (ended) return;
        arena.clearCelebration();
        for (int side = 0; side < MAX_SEATS; side++) {
            if (!seated[side]) continue;
            Player player = Bukkit.getPlayer(players[side]);
            if (leaveAfterHand[side]) {
                cashOutSeat(side, "scheduled leave after hand");
                releaseSeat(side, player, Messages.msg("blackjack.leave.scheduled-done"));
                continue;
            }
            if (player == null || !arena.protects(players[side])) {
                cashOutSeat(side, "player no longer in the casino");
                releaseSeat(side, player, null);
                continue;
            }
            if (stack[side] < minBet) {
                double balance = plugin.economy().balance(player);
                int room = PokerMoney.topUpRoom(carryLimit, stack[side], 0, 0, false);
                int topUp = Math.min(room, PokerMoney.carryAmount(balance, carryLimit));
                if (topUp >= minBet && plugin.economy().withdraw(player, topUp)) {
                    stack[side] += topUp;
                    Text.send(player, Messages.msg("blackjack.topup.auto", "amount", topUp));
                } else {
                    cashOutSeat(side, "not enough chips and unable to top up");
                    releaseSeat(side, player, Messages.msg("blackjack.leave.broke"));
                }
            }
        }
        if (seatedCount() < 1) {
            ended = true;
            manager.finishSession(this, Messages.msg("blackjack.session.reason.all-left"));
            return;
        }
        startHand();
    }

    private void releaseSeat(int side, Player player, String message) {
        seated[side] = false;
        inHand[side] = false;
        leaveAfterHand[side] = false;
        hands.get(side).clear();
        manager.detached(this, players[side]);
        if (player != null) {
            if (message != null) Text.send(player, message);
            arena.release(player);
        }
        arena.clearSeat(side, new UUID(0L, side + 1L));
        players[side] = new UUID(0L, side + 1L);
        names[side] = Messages.msg("blackjack.seat.empty-name");
    }

    private int cashOutSeat(int side, String reason) {
        int amount = Math.max(0, stack[side]);
        stack[side] = 0;
        if (amount <= 0) return 0;
        boolean paid = manager.cashOut(players[side], amount, reason);
        Player player = Bukkit.getPlayer(players[side]);
        if (player != null) {
            Text.send(player, Messages.msg(paid ? "blackjack.cashout.paid" : "blackjack.cashout.pending",
                    "amount", plugin.economy().format(amount)));
        }
        return amount;
    }

    // ---------------------------------------------------------------- 玩家操作

    boolean act(Player player, BlackjackAction action) {
        if (ended) return false;
        int side = side(player.getUniqueId());
        if (side < 0 || !seated[side]) return false;
        switch (action) {
            case BET_MIN -> betMinimum(side);
            case BET_RECLAIM -> reclaimBet(side);
            case BET_CONFIRM -> confirmBet(side);
            case HIT -> hit(side);
            case STAND -> stand(side);
            case DOUBLE -> doubleDown(side);
            case SPLIT -> split(side);
            case INSURANCE -> buyInsurance(side);
            case TOP_UP -> topUp(side);
            case LEAVE_AFTER_HAND -> toggleLeave(side);
        }
        return true;
    }

    /** 玩家把一枚实体筹码放进自己座位前的下注区。 */
    boolean placeChip(Player player, org.bukkit.Material material, Location location) {
        int value = PokerChips.value(material);
        if (value <= 0) return false;
        int side = side(player.getUniqueId());
        if (ended || side < 0 || !seated[side]) return false;
        if (phase != Phase.BETTING) {
            Text.send(player, Messages.msg("blackjack.chip.not-betting"));
            return true;
        }
        if (betConfirmed[side]) {
            Text.send(player, Messages.msg("blackjack.chip.locked"));
            return true;
        }
        if (!arena.inBetZone(side, location)) {
            Text.send(player, Messages.msg("blackjack.chip.wrong-zone"));
            return true;
        }
        if (pendingBet[side] + value > stack[side]) {
            int remaining = Math.max(0, stack[side] - pendingBet[side]);
            Text.send(player, Messages.msg("blackjack.chip.insufficient",
                    "remaining", remaining, "value", value));
            return true;
        }
        if (pendingBet[side] + value > maxBet) {
            Text.send(player, Messages.msg("blackjack.chip.over-max", "max", maxBet));
            return true;
        }
        arena.consumePlacedChip(player, side, material);
        pendingBet[side] += value;
        Text.send(player, Messages.msg("blackjack.chip.placed",
                "value", value, "total", pendingBet[side], "min", minBet));
        sync();
        return true;
    }

    private void betMinimum(int side) {
        if (phase != Phase.BETTING) {
            send(side, Messages.msg("blackjack.bet.not-betting"));
            return;
        }
        if (betConfirmed[side]) {
            send(side, Messages.msg("blackjack.bet.already-locked"));
            return;
        }
        int missing = Math.max(0, minBet - pendingBet[side]);
        int available = Math.max(0, stack[side] - pendingBet[side]);
        int added = Math.min(missing, available);
        if (added <= 0) {
            send(side, Messages.msg(missing <= 0
                    ? "blackjack.bet.already-minimum" : "blackjack.bet.no-chips"));
            return;
        }
        pendingBet[side] += added;
        arena.resetChips(side);
        send(side, Messages.msg("blackjack.bet.topped-to-minimum", "amount", pendingBet[side]));
        sync();
    }

    private void reclaimBet(int side) {
        if (betConfirmed[side]) {
            send(side, Messages.msg("blackjack.bet.locked-reclaim"));
            return;
        }
        if (pendingBet[side] <= 0) {
            send(side, Messages.msg("blackjack.bet.nothing-to-reclaim"));
            return;
        }
        send(side, Messages.msg("blackjack.bet.reclaimed", "amount", pendingBet[side]));
        pendingBet[side] = 0;
        arena.resetChips(side);
        sync();
    }

    private void confirmBet(int side) {
        if (phase != Phase.BETTING) {
            send(side, Messages.msg("blackjack.bet.not-betting"));
            return;
        }
        if (betConfirmed[side]) {
            send(side, Messages.msg("blackjack.bet.already-confirmed"));
            return;
        }
        if (pendingBet[side] < minBet) {
            send(side, Messages.msg("blackjack.bet.under-minimum", "min", minBet));
            return;
        }
        betConfirmed[side] = true;
        send(side, Messages.msg("blackjack.bet.confirmed", "amount", pendingBet[side]));
        sync();
        if (allBetsIn()) finishBetting();
    }

    private boolean allBetsIn() {
        for (int side = 0; side < MAX_SEATS; side++) {
            if (!seated[side]) continue;
            if (affordable(side) < minBet) continue;
            if (!betConfirmed[side]) return false;
        }
        return true;
    }

    private void hit(int side) {
        if (!isActor(side)) return;
        BlackjackSeatHand hand = current(side);
        if (hand == null || !hand.canHit()) {
            send(side, Messages.msg("blackjack.hit.not-allowed"));
            return;
        }
        hand.add(draw());
        send(side, Messages.msg("blackjack.hit.dealt",
                "card", BlackjackArena.cardText(hand.cards().getLast()), "hand", handSummary(hand)));
        if (hand.bust()) {
            hand.finish();
            send(side, Messages.msg("blackjack.hit.bust", "amount", hand.bet()));
        } else if (hand.value() == BlackjackHand.TARGET) {
            hand.finish();
            send(side, Messages.msg("blackjack.hit.twenty-one"));
        }
        advance();
    }

    private void stand(int side) {
        if (!isActor(side)) return;
        BlackjackSeatHand hand = current(side);
        if (hand == null) return;
        hand.finish();
        send(side, Messages.msg("blackjack.stand.done", "hand", handSummary(hand)));
        advance();
    }

    private void doubleDown(int side) {
        if (!isActor(side)) return;
        BlackjackSeatHand hand = current(side);
        if (hand == null || !hand.canDouble(stack[side])) {
            send(side, Messages.msg("blackjack.double.not-allowed"));
            return;
        }
        stack[side] -= hand.bet();
        hand.bet(hand.bet() * 2);
        hand.doubled(true);
        hand.add(draw());
        hand.finish();
        send(side, Messages.msg("blackjack.double.done", "amount", hand.bet(),
                "card", BlackjackArena.cardText(hand.cards().getLast()), "hand", handSummary(hand)));
        if (hand.bust()) send(side, Messages.msg("blackjack.double.bust"));
        advance();
    }

    private void split(int side) {
        if (!isActor(side)) return;
        List<BlackjackSeatHand> list = hands.get(side);
        BlackjackSeatHand hand = current(side);
        if (hand == null || !hand.canSplit(stack[side], list.size(), MAX_HANDS)) {
            send(side, Messages.msg("blackjack.split.not-allowed"));
            return;
        }
        boolean aces = BlackjackHand.ace(hand.cards().getFirst());
        stack[side] -= hand.bet();
        PokerCard moved = hand.cards().removeLast();
        BlackjackSeatHand extra = new BlackjackSeatHand(hand.bet());
        extra.fromSplit(true);
        extra.add(moved);
        hand.fromSplit(true);
        hand.add(draw());
        extra.add(draw());
        list.add(activeHand[side] + 1, extra);
        if (aces) {
            // 分 A 后每手只补一张，且不再行动。
            hand.splitAce(true);
            extra.splitAce(true);
            hand.finish();
            extra.finish();
            send(side, Messages.msg("blackjack.split.aces"));
        } else {
            send(side, Messages.msg("blackjack.split.done", "amount", hand.bet()));
        }
        advance();
    }

    private void buyInsurance(int side) {
        if (phase != Phase.INSURANCE) {
            send(side, Messages.msg("blackjack.insurance.not-now"));
            return;
        }
        if (!inHand[side]) return;
        if (insuranceAnswered[side]) {
            send(side, Messages.msg("blackjack.insurance.already"));
            return;
        }
        int cost = hands.get(side).getFirst().bet() / 2;
        if (cost <= 0 || stack[side] < cost) {
            send(side, Messages.msg("blackjack.insurance.too-poor", "cost", cost));
            insuranceAnswered[side] = true;
            return;
        }
        stack[side] -= cost;
        insurance[side] = cost;
        insuranceAnswered[side] = true;
        send(side, Messages.msg("blackjack.insurance.bought", "amount", cost));
        sync();
        if (allInsuranceAnswered()) finishInsurance();
    }

    private boolean allInsuranceAnswered() {
        for (int side = 0; side < MAX_SEATS; side++) {
            if (inHand[side] && !insuranceAnswered[side]) return false;
        }
        return true;
    }

    private void topUp(int side) {
        Player player = Bukkit.getPlayer(players[side]);
        if (player == null) return;
        int room = PokerMoney.topUpRoom(carryLimit, stack[side], wagered(side), 0, inHand[side]);
        if (room <= 0) {
            send(side, Messages.msg("blackjack.topup.at-limit", "limit", carryLimit));
            return;
        }
        int amount = Math.min(room, PokerMoney.carryAmount(plugin.economy().balance(player), carryLimit));
        if (amount <= 0) {
            send(side, Messages.msg("blackjack.topup.no-balance"));
            return;
        }
        if (!plugin.economy().withdraw(player, amount)) {
            send(side, Messages.msg("blackjack.topup.failed"));
            return;
        }
        stack[side] += amount;
        send(side, Messages.msg("blackjack.topup.done", "amount", amount));
        sync();
    }

    private void toggleLeave(int side) {
        leaveAfterHand[side] = !leaveAfterHand[side];
        send(side, Messages.msg(leaveAfterHand[side]
                ? "blackjack.leave.scheduled-on"
                : "blackjack.leave.scheduled-off"));
        sync();
    }

    boolean chipAction(Player player, org.bukkit.Material material, boolean merge) {
        int side = side(player.getUniqueId());
        if (side < 0 || !seated[side]) return false;
        int available = Math.max(0, stack[side] - pendingBet[side]);
        if (merge) {
            arena.mergeChips(player, side, available);
            return true;
        }
        if (PokerChips.value(material) <= 0) return false;
        return arena.splitChip(player, side, material);
    }

    private boolean isActor(int side) {
        if (phase != Phase.ACTIONS || actor != side) {
            send(side, Messages.msg("blackjack.turn.not-yours"));
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- 生命周期

    void tick() {
        if (ended) return;
        long now = System.currentTimeMillis();
        if (phase == Phase.SETTLED) {
            if (resumeAt > 0 && now >= resumeAt) {
                resumeAt = 0;
                continueOrFinish();
            }
            return;
        }
        if (now < deadline) {
            sync();
            return;
        }
        switch (phase) {
            case BETTING -> {
                for (int side = 0; side < MAX_SEATS; side++) {
                    if (!seated[side] || affordable(side) < minBet) continue;
                    if (pendingBet[side] < minBet) pendingBet[side] = Math.min(minBet, stack[side]);
                    betConfirmed[side] = true;
                }
                broadcast(Messages.msg("blackjack.bet.timeout"));
                finishBetting();
            }
            case INSURANCE -> {
                for (int side = 0; side < MAX_SEATS; side++) insuranceAnswered[side] = true;
                broadcast(Messages.msg("blackjack.insurance.closed"));
                finishInsurance();
            }
            case ACTIONS -> {
                if (actor >= 0) {
                    BlackjackSeatHand hand = current(actor);
                    if (hand != null) {
                        hand.finish();
                        send(actor, Messages.msg("blackjack.turn.timeout"));
                    }
                }
                advance();
            }
            default -> { }
        }
    }

    boolean join(Player player) {
        if (ended) return false;
        int side = freeSeat();
        if (side < 0) {
            Text.send(player, Messages.msg("blackjack.join.full", "max", MAX_SEATS));
            return false;
        }
        int amount = PokerMoney.carryAmount(plugin.economy().balance(player), carryLimit);
        if (amount < minBet) {
            Text.send(player, Messages.msg("blackjack.join.min-buyin", "amount", minBet));
            return false;
        }
        if (!plugin.economy().withdraw(player, amount)) {
            Text.send(player, Messages.msg("blackjack.join.charge-failed"));
            return false;
        }
        players[side] = player.getUniqueId();
        names[side] = player.getName();
        seated[side] = true;
        inHand[side] = false;
        leaveAfterHand[side] = false;
        stack[side] = amount;
        initialStacks[side] = amount;
        pendingBet[side] = 0;
        betConfirmed[side] = false;
        hands.get(side).clear();
        try {
            arena.addPlayer(side, player);
        } catch (RuntimeException | Error throwable) {
            manager.cashOut(player.getUniqueId(), amount, "rollback after failed mid-game join");
            players[side] = new UUID(0L, side + 1L);
            names[side] = Messages.msg("blackjack.seat.empty-name");
            seated[side] = false;
            stack[side] = 0;
            plugin.getLogger().severe("Failed to seat a player in the blackjack arena: " + throwable);
            Text.send(player, Messages.msg("blackjack.join.arena-failed"));
            return false;
        }
        manager.attached(this, player.getUniqueId());
        broadcast(Messages.msg(phase == Phase.BETTING
                        ? "blackjack.join.announce" : "blackjack.join.announce-waiting",
                "player", player.getName(), "count", seatedCount(), "max", MAX_SEATS));
        sync();
        return true;
    }

    boolean leave(Player player, String reason) {
        int side = side(player.getUniqueId());
        if (side < 0 || !seated[side]) return false;
        if (inHand[side] && phase != Phase.SETTLED) {
            for (BlackjackSeatHand hand : hands.get(side)) hand.finish();
            broadcast(Messages.msg("blackjack.leave.mid-hand", "player", names[side]));
        }
        int amount = cashOutSeat(side, reason);
        releaseSeat(side, player, amount > 0 ? null : Messages.msg("blackjack.leave.left"));
        if (phase == Phase.ACTIONS && actor == side) advance();
        else if (phase == Phase.BETTING && allBetsIn()) finishBetting();
        else if (phase == Phase.INSURANCE && allInsuranceAnswered()) finishInsurance();
        else sync();
        if (!ended && seatedCount() < 1) {
            ended = true;
            manager.finishSession(this, Messages.msg("blackjack.session.reason.all-left"));
        }
        return true;
    }

    /** 服务器关闭时按现有筹码全额退款。 */
    int[] abortRefunds() {
        int[] refunds = new int[MAX_SEATS];
        for (int side = 0; side < MAX_SEATS; side++) {
            if (!seated[side]) continue;
            refunds[side] = Math.max(0, stack[side] + wagered(side) + insurance[side]);
        }
        return refunds;
    }

    private int wagered(int side) {
        int total = 0;
        for (BlackjackSeatHand hand : hands.get(side)) total += hand.bet();
        return total;
    }

    private int seatedCount() {
        int count = 0;
        for (boolean value : seated) if (value) count++;
        return count;
    }

    private int freeSeat() {
        for (int side = 0; side < MAX_SEATS; side++) if (!seated[side]) return side;
        return -1;
    }

    int side(UUID player) {
        for (int side = 0; side < MAX_SEATS; side++) if (players[side].equals(player)) return side;
        return -1;
    }

    // ---------------------------------------------------------------- 展示

    private void sync() {
        if (arena == null) return;
        int seconds = (int) Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000);
        if (phase == Phase.SETTLED) seconds = (int) Math.max(0, (resumeAt - System.currentTimeMillis() + 999) / 1000);
        String[] handText = new String[MAX_SEATS];
        int[] wagered = new int[MAX_SEATS];
        int total = 0;
        for (int side = 0; side < MAX_SEATS; side++) {
            wagered[side] = wagered(side);
            total += wagered[side] + insurance[side];
            handText[side] = seatHandText(side);
        }
        boolean[] awaiting = new boolean[MAX_SEATS];
        for (int side = 0; side < MAX_SEATS; side++) {
            awaiting[side] = phase == Phase.BETTING && seated[side] && !betConfirmed[side];
        }
        arena.highlightBetZones(awaiting);
        arena.sync(new View(handNumber, phase.display(), actor, seconds, total, stack.clone(), wagered,
                insurance.clone(), seated.clone(), leaveAfterHand.clone(), handText, carryLimit,
                phase == Phase.BETTING, minBet, pendingBet.clone()));
    }

    /** 座位全息固定三段：第一行荷官牌，第二行自己的牌，第三行下注额。 */
    private String seatHandText(int side) {
        if (!seated[side]) return Messages.msg("blackjack.hologram.hand-empty");
        StringBuilder text = new StringBuilder(dealerLine());
        if (phase == Phase.BETTING) {
            text.append('\n').append(Messages.msg("blackjack.hologram.waiting-deal"));
            text.append('\n');
            if (betConfirmed[side]) {
                text.append(Messages.msg("blackjack.hologram.bet-confirmed", "amount", pendingBet[side]));
            } else if (pendingBet[side] > 0) {
                text.append(Messages.msg("blackjack.hologram.bet-pending", "amount", pendingBet[side]));
            } else {
                text.append(Messages.msg("blackjack.hologram.place-chips"));
            }
            return text.toString();
        }
        List<BlackjackSeatHand> list = hands.get(side);
        if (list.isEmpty()) {
            text.append('\n').append(Messages.msg("blackjack.hologram.sitting-out"));
            return text.toString();
        }
        for (int index = 0; index < list.size(); index++) {
            BlackjackSeatHand hand = list.get(index);
            text.append('\n');
            if (list.size() > 1) {
                text.append(Messages.msg("blackjack.hologram.hand-index", "index", index + 1));
            }
            if (side == actor && index == activeHand[side] && phase == Phase.ACTIONS) {
                text.append("<green>▶ </green>");
            }
            text.append(cardsText(hand.cards())).append("  ").append(BlackjackHand.describe(hand.cards()));
            text.append('\n').append(Messages.msg("blackjack.hologram.bet", "amount", hand.bet()));
            if (hand.doubled()) text.append(Messages.msg("blackjack.hologram.doubled"));
            if (!hand.outcome().isEmpty()) text.append(' ').append(hand.outcome());
        }
        if (insurance[side] > 0) {
            text.append('\n').append(Messages.msg("blackjack.hologram.insurance", "amount", insurance[side]));
        }
        return text.toString();
    }

    /** 座位全息最上面那行：荷官的牌，暗牌保持隐藏。 */
    private String dealerLine() {
        if (dealer.isEmpty()) return Messages.msg("blackjack.dealer.hidden");
        StringBuilder text = new StringBuilder(Messages.msg("blackjack.dealer.label")).append(' ');
        for (int index = 0; index < dealer.size(); index++) {
            if (index == 1 && !holeRevealed) text.append(Messages.msg("blackjack.card.face-down")).append(' ');
            else text.append(BlackjackArena.cardText(dealer.get(index))).append(' ');
        }
        text.append(' ').append(holeRevealed ? BlackjackHand.describe(dealer)
                : Messages.msg("blackjack.dealer.showing", "value", BlackjackHand.cardValue(dealer.getFirst())));
        return text.toString();
    }

    private String handSummary(BlackjackSeatHand hand) {
        return cardsText(hand.cards()) + " " + BlackjackHand.describe(hand.cards());
    }

    private static String cardsText(List<PokerCard> cards) {
        StringBuilder text = new StringBuilder();
        for (PokerCard card : cards) text.append(BlackjackArena.cardText(card)).append(' ');
        return text.toString().trim();
    }

    private void broadcastHands(String header) {
        broadcast(header);
        broadcastDealerUpCard();
        for (int side = 0; side < MAX_SEATS; side++) {
            if (!inHand[side]) continue;
            broadcast(Messages.msg("blackjack.hand.line",
                    "player", names[side], "hand", handSummary(hands.get(side).getFirst())));
        }
    }

    /** 荷官明牌在聊天框里单独播报一行，暗牌保持隐藏。 */
    private void broadcastDealerUpCard() {
        if (dealer.isEmpty()) return;
        broadcast(Messages.msg("blackjack.dealer.upcard",
                "card", BlackjackArena.cardText(dealer.getFirst()),
                "value", BlackjackHand.cardValue(dealer.getFirst())));
    }

    private void broadcast(String message) {
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && arena != null && arena.protects(id)) Text.send(player, message);
        }
    }

    private void send(int side, String message) {
        Player player = Bukkit.getPlayer(players[side]);
        if (player != null) Text.send(player, message);
    }

    // ---------------------------------------------------------------- 对外访问

    boolean protects(UUID player) { return arena != null && arena.protects(player); }
    boolean contains(Location location) { return arena != null && arena.contains(location); }
    BlackjackAction actionAt(Block block) { return arena == null ? null : arena.actionAt(block); }
    void hidePrivateFrom(Player player) { if (arena != null) arena.hidePrivateFrom(player); }
    void closeArena() { if (arena != null) arena.close(); }
    UUID hostId() { return hostId; }

    String hostName() { return hostName; }
    UUID playerAt(int side) { return players[side]; }
    String nameAt(int side) { return names[side]; }
    boolean seatedAt(int side) { return seated[side]; }
    int seatCapacity() { return MAX_SEATS; }
    int playerCount() { return seatedCount(); }
    int handNumber() { return handNumber; }
    long startedAt() { return startedAt; }
    int[] stacks() { return stack.clone(); }
    int[] initialStacks() { return initialStacks.clone(); }
    boolean ended() { return ended; }
    void markEnded() { ended = true; }

    /** 传给场地渲染的一次性快照。 */
    record View(int handNumber, String phase, int actor, int seconds, int totalWagered,
                int[] stack, int[] wagered, int[] insurance, boolean[] seated, boolean[] leaveAfterHand,
                String[] handText, int carryLimit, boolean betting, int minBet, int[] pending) { }
}
