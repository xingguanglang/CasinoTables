package io.github.casinotables.poker;

import io.github.casinotables.CasinoTablesPlugin;
import io.github.casinotables.GameType;
import io.github.casinotables.Messages;
import io.github.casinotables.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class PokerGame {
    static final int MAX_SEATS = 6;
    private enum Street {
        PREFLOP("poker.street.preflop"), FLOP("poker.street.flop"),
        TURN("poker.street.turn"), RIVER("poker.street.river");
        private final String key;
        Street(String key) { this.key = key; }
        String display() { return Messages.msg(key); }
    }

    private final PokerManager manager;
    private final CasinoTablesPlugin plugin;
    private final UUID[] players;
    private final String[] names;
    private final boolean[] realSeat;
    private final boolean[] botSeat = new boolean[MAX_SEATS];
    /** 每个 BOT 座位当前使用的性格原型编号；-1 表示该座位不是 BOT。 */
    private final int[] botArchetype = new int[MAX_SEATS];
    private final UUID hostId;
    private final String hostName;
    private List<PokerCard> deck = PokerCard.shuffledDeck();
    private final PokerCard[][] hole;
    private final List<PokerCard> board = new ArrayList<>(5);
    private final Set<Integer> acted = new HashSet<>();
    private final Set<UUID> drawVotes = new HashSet<>();
    private final int[] stack;
    private final int[] roundBet;
    private final int[] contribution;
    private final int[] initialStacks;
    private final int[] cashOuts;
    private final boolean[] folded;
    private final boolean[] seated;
    private final boolean[] leaveAfterHand;
    private int dealer;
    private int smallBlindSide;
    private int bigBlindSide;
    private final int smallBlind;
    private final int bigBlind;
    private final int carryLimit;
    private final int actionTimeoutSeconds;
    private final int[] pendingBet;
    private final int[] queuedRebuy;
    private final int[] handAwards;
    private int lastHandRake;
    private double handRakeRate;
    private final long startedAt = System.currentTimeMillis();
    private long handStartedAt = startedAt;
    private int[] handInitialStacks;
    private PokerArena arena;
    private int deckIndex;
    private int actor;
    private int pot;
    private int currentBet;
    private int lastRaise;
    private Street street = Street.PREFLOP;
    private long deadline;
    private long botActAt;
    private boolean ended;
    private boolean revealing;
    private boolean handPaused;
    private int handNumber = 1;
    private Set<Integer> lastWinningSides = Set.of();

    PokerGame(PokerManager manager, CasinoTablesPlugin plugin, List<Player> participants, int[] buyIns,
              int requestedSmallBlind, int requestedBigBlind, int requestedCarryLimit) {
        this.manager = manager;
        this.plugin = plugin;
        if (participants.size() < 1 || participants.size() > MAX_SEATS) {
            throw new IllegalArgumentException("participant count out of range");
        }
        this.hostId = participants.getFirst().getUniqueId();
        this.hostName = participants.getFirst().getName();
        this.players = new UUID[MAX_SEATS];
        this.names = new String[MAX_SEATS];
        this.realSeat = new boolean[MAX_SEATS];
        for (int side = 0; side < MAX_SEATS; side++) clearIdentity(side);
        for (int side = 0; side < participants.size(); side++) {
            players[side] = participants.get(side).getUniqueId();
            names[side] = participants.get(side).getName();
            realSeat[side] = true;
        }
        this.hole = new PokerCard[players.length][2];
        this.stack = new int[players.length];
        this.initialStacks = new int[players.length];
        System.arraycopy(buyIns, 0, stack, 0, buyIns.length);
        System.arraycopy(buyIns, 0, initialStacks, 0, buyIns.length);
        this.cashOuts = new int[players.length];
        this.roundBet = new int[players.length];
        this.contribution = new int[players.length];
        this.folded = new boolean[players.length];
        this.seated = new boolean[players.length];
        this.leaveAfterHand = new boolean[players.length];
        for (int side = 0; side < participants.size(); side++) seated[side] = true;
        this.pendingBet = new int[players.length];
        this.queuedRebuy = new int[players.length];
        this.handAwards = new int[players.length];
        this.handRakeRate = configuredRakeRate();
        this.smallBlind = Math.max(1, requestedSmallBlind);
        this.carryLimit = Math.max(requestedBigBlind, requestedCarryLimit);
        this.actionTimeoutSeconds = Math.max(5,
                plugin.getConfig().getInt("poker.action-timeout-seconds", 60));
        this.bigBlind = Math.max(this.smallBlind + 1, requestedBigBlind);
        if (buyIns.length != participants.size()) throw new IllegalArgumentException("buy-in count mismatch");
        for (int buyIn : buyIns) {
            if (buyIn < this.bigBlind) throw new IllegalArgumentException("buy-in below big blind");
        }
        java.util.Arrays.fill(botArchetype, -1);
        for (int side = participants.size(); side < MAX_SEATS; side++) installBot(side);
        this.handInitialStacks = stack.clone();
        java.util.Arrays.fill(folded, true);
        for (int side = 0; side < MAX_SEATS; side++) if (seated[side]) folded[side] = false;
        dealHoleCards();
        dealer = ThreadLocalRandom.current().nextInt(MAX_SEATS);
        smallBlindSide = nextSeatedAfter(dealer);
        bigBlindSide = nextSeatedAfter(smallBlindSide);
        post(smallBlindSide, smallBlind);
        post(bigBlindSide, bigBlind);
        currentBet = Math.max(roundBet[smallBlindSide], roundBet[bigBlindSide]);
        lastRaise = bigBlind;
        actor = nextCanActAfter(bigBlindSide);
        resetDeadline();
    }

    void start() {
        syncArena();
        String roster = String.join(Messages.msg("poker.common.name-separator"), participantsNames());
        for (int side = 0; side < players.length; side++) {
            if (!realSeat[side]) continue;
            Player player = Bukkit.getPlayer(players[side]);
            if (player == null) continue;
            Text.send(player, Messages.msg("poker.hand.start", "players", roster));
            Text.send(player, Messages.msg("poker.hand.start-seat", "small", smallBlind,
                    "big", bigBlind, "role", role(side), "stack", initialStacks[side],
                    "limit", carryLimit));
            Text.send(player, Messages.msg("poker.hand.start-controls",
                    "seconds", actionTimeoutSeconds));
        }
        if (actor < 0 || (canActCount() <= 1 && roundComplete())) runOutAndShowdown();
        else announceTurn();
    }

    void showStatus(Player player) {
        int side = side(player.getUniqueId());
        if (ended || side < 0 || arena == null || !arena.protects(player.getUniqueId())) return;
        if (handPaused) {
            Text.send(player, Messages.msg("poker.status.hand-paused"));
            return;
        }
        if (revealing) {
            Text.send(player, Messages.msg("poker.status.revealing"));
            return;
        }
        if (folded[side]) {
            Text.send(player, Messages.msg("poker.status.folded"));
            return;
        }
        Text.send(player, Messages.msg("poker.status.controls"));
        Text.send(player, side == actor ? Messages.msg("poker.status.your-turn")
                : Messages.msg("poker.status.waiting", "player", names[actor]));
    }

    void requestDraw(Player player) {
        int side = side(player.getUniqueId());
        if (ended || side < 0 || arena == null || !arena.protects(player.getUniqueId())) return;
        if (!drawVotes.add(player.getUniqueId())) {
            Text.send(player, Messages.msg("poker.draw.already-voted"));
            return;
        }
        int required = 0;
        for (UUID id : players) if (arena.protects(id)) required++;
        broadcast(Messages.msg("poker.draw.requested", "player", names[side],
                "votes", drawVotes.size(), "required", required));
        if (required > 0 && drawVotes.size() >= required) settleMutualDraw();
    }

    /** 只补齐待确认筹码，仍需玩家按绿色按钮确认，避免误操作直接结束回合。 */
    private void fillCall(int side) {
        int needed = Math.max(0, currentBet - roundBet[side]);
        if (needed == 0) {
            send(side, Messages.msg("poker.action.nothing-to-call"));
            return;
        }
        int missing = Math.max(0, needed - pendingBet[side]);
        int available = Math.max(0, stack[side] - pendingBet[side]);
        int added = Math.min(missing, available);
        pendingBet[side] += added;
        int shortfall = Math.max(0, needed - pendingBet[side]);
        send(side, Messages.msg(shortfall == 0
                        ? "poker.action.call-filled" : "poker.action.call-filled-all-in",
                "added", added, "needed", needed, "pending", pendingBet[side]));
        Player player = Bukkit.getPlayer(players[side]);
        if (player != null) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
        refresh();
    }

    private void raise(int side) {
        if (!hasRaiseRights(side)) {
            send(side, Messages.msg("poker.action.raise-closed"));
            return;
        }
        int target = currentBet + Math.max(lastRaise, bigBlind);
        if (target > roundBet[side] + stack[side]) {
            send(side, Messages.msg("poker.action.raise-too-poor"));
            return;
        }
        betTo(side, target);
    }

    private void allIn(int side) {
        if (stack[side] <= 0) {
            send(side, Messages.msg("poker.action.no-chips"));
            return;
        }
        pendingBet[side] = stack[side];
        int target = roundBet[side] + pendingBet[side];
        if (target > currentBet && !hasRaiseRights(side)) {
            pendingBet[side] = 0;
            send(side, Messages.msg("poker.action.raise-not-reopened"));
            refresh();
            return;
        }
        confirmPlaced(side);
    }

    boolean placeChip(Player player, Material material, org.bukkit.Location location) {
        int value = PokerChips.value(material);
        if (value <= 0) return false;
        int side = side(player.getUniqueId());
        if (ended || handPaused || revealing || side < 0 || folded[side]) return true;
        if (side != actor) {
            Text.send(player, Messages.msg("poker.action.not-your-turn-bet"));
            return true;
        }
        if (!arena.inBetZone(side, location)) {
            Text.send(player, Messages.msg("poker.action.wrong-bet-zone"));
            return true;
        }
        if (pendingBet[side] + value > stack[side]) {
            int remaining = Math.max(0, stack[side] - pendingBet[side]);
            Text.send(player, Messages.msg("poker.action.chip-too-large",
                    "remaining", remaining, "value", value));
            return true;
        }
        arena.consumePlacedChip(player, material);
        pendingBet[side] += value;
        int needed = Math.max(0, currentBet - roundBet[side]);
        int shortfall = Math.max(0, needed - pendingBet[side]);
        if (shortfall > 0) {
            Text.send(player, Messages.msg("poker.action.chip-placed-short", "value", value,
                    "pending", pendingBet[side], "needed", needed, "short", shortfall));
        } else if (pendingBet[side] > needed) {
            Text.send(player, Messages.msg("poker.action.chip-placed-over", "value", value,
                    "pending", pendingBet[side], "needed", needed,
                    "over", pendingBet[side] - needed));
        } else {
            Text.send(player, Messages.msg("poker.action.chip-placed-exact", "value", value,
                    "pending", pendingBet[side], "needed", needed));
        }
        refresh();
        return true;
    }

    boolean control(Player player, Material material) {
        int side = side(player.getUniqueId());
        if (side < 0 || ended) return false;
        if (handPaused || revealing) {
            send(side, Messages.msg(handPaused
                    ? "poker.status.hand-paused-short" : "poker.status.revealing-short"));
            return true;
        }
        if (material == Material.YELLOW_DYE) {
            if (side == actor) fillCall(side); else send(side, Messages.msg("poker.action.not-your-turn"));
            return true;
        }
        if (material == Material.LIME_DYE) {
            if (side == actor) confirmPlaced(side); else send(side, Messages.msg("poker.action.not-your-turn"));
            return true;
        }
        if (material == Material.CHEST) {
            returnPending(side);
            return true;
        }
        if (material == Material.RED_DYE) {
            if (side == actor) fold(player.getUniqueId(), Messages.msg("poker.reason.fold"));
            else send(side, Messages.msg("poker.action.not-your-turn"));
            return true;
        }
        if (material == Material.NETHER_STAR) {
            if (side == actor) allIn(side); else send(side, Messages.msg("poker.action.not-your-turn"));
            return true;
        }
        return false;
    }

    boolean interactExitButton(Player player, Block block) {
        int side = side(player.getUniqueId());
        if (side < 0 || ended || arena == null || !arena.isExitButton(side, block)) return false;
        if (handPaused) {
            leave(player, Messages.msg("poker.reason.leave-after-hand"));
            return true;
        }
        leaveAfterHand[side] = !leaveAfterHand[side];
        arena.syncExitButton(side, leaveAfterHand[side]);
        send(side, Messages.msg(leaveAfterHand[side]
                ? "poker.exit.armed" : "poker.exit.cancelled"));
        return true;
    }

    boolean interactControlButton(Player player, Block block) {
        if (ended || arena == null) return false;
        PokerArena.ControlButton button = arena.controlButton(block);
        if (button == null) return false;
        int side = side(player.getUniqueId());
        if (side != button.side()) {
            if (side >= 0) {
                send(side, Messages.msg("poker.action.other-seat-button",
                        "player", names[button.side()]));
            }
            return true;
        }
        return control(player, button.action());
    }

    boolean interactAtm(Player player, Block block) {
        int side = side(player.getUniqueId());
        if (side < 0 || ended || arena == null || !arena.isAtm(block)) return false;
        if (!seated[side]) {
            send(side, Messages.msg("poker.atm.left-table"));
            return true;
        }
        int committed = handPaused ? 0 : contribution[side];
        int room = PokerMoney.topUpRoom(carryLimit, stack[side], contribution[side],
                queuedRebuy[side], !handPaused);
        int tableTotal = carryLimit - room;
        if (room <= 0) {
            String committedPart = committed > 0
                    ? Messages.msg("poker.atm.limit-committed", "amount", committed) : "";
            String queuedPart = queuedRebuy[side] > 0
                    ? Messages.msg("poker.atm.limit-queued", "amount", queuedRebuy[side]) : "";
            send(side, Messages.msg("poker.atm.limit-reached", "limit", carryLimit,
                    "stack", stack[side], "committed", committedPart, "queued", queuedPart));
            return true;
        }
        int amount = PokerMoney.carryAmount(plugin.economy().balance(player), room);
        if (amount <= 0) {
            send(side, Messages.msg("poker.atm.no-balance", "room", room));
            return true;
        }
        if (!plugin.economy().withdraw(player, amount)) {
            send(side, Messages.msg("poker.atm.charge-failed"));
            return true;
        }
        initialStacks[side] += amount;
        if (handPaused) {
            stack[side] += amount;
            send(side, Messages.msg("poker.atm.bought-paused", "amount", amount,
                    "stack", stack[side], "limit", carryLimit));
            broadcast(Messages.msg("poker.atm.broadcast-paused", "player", names[side],
                    "amount", amount));
        } else {
            queuedRebuy[side] += amount;
            send(side, Messages.msg("poker.atm.bought-live", "amount", amount,
                    "stack", stack[side], "committed", committed, "queued", queuedRebuy[side],
                    "total", stack[side] + committed + queuedRebuy[side], "limit", carryLimit));
            broadcast(Messages.msg("poker.atm.broadcast-live", "player", names[side],
                    "amount", amount));
        }
        syncArena();
        return true;
    }

    boolean join(Player player) {
        if (ended || arena == null) {
            Text.send(player, Messages.msg("poker.join.ended"));
            return false;
        }
        int side = availableSeat();
        if (side < 0) {
            Text.send(player, Messages.msg("poker.join.full", "max", MAX_SEATS));
            return false;
        }
        int minimum = Math.max(bigBlind, PokerChips.MIN_OPENING_COUNT);
        int amount = PokerMoney.carryAmount(plugin.economy().balance(player), carryLimit);
        if (amount < minimum) {
            Text.send(player, Messages.msg("poker.join.min-buyin", "minimum", minimum,
                    "limit", carryLimit));
            return false;
        }
        if (!plugin.economy().withdraw(player, amount)) {
            Text.send(player, Messages.msg("poker.join.charge-failed"));
            return false;
        }

        String replacedBot = botSeat[side] ? names[side] : null;
        int botContribution = contribution[side];
        if (botSeat[side] && !handPaused && !folded[side]) {
            fold(players[side], Messages.msg("poker.reason.bot-yield"));
        }
        boolean joinDuringHand = !handPaused;
        players[side] = player.getUniqueId();
        names[side] = player.getName();
        realSeat[side] = true;
        botSeat[side] = false;
        seated[side] = true;
        folded[side] = true;
        leaveAfterHand[side] = false;
        initialStacks[side] = amount;
        cashOuts[side] = 0;
        stack[side] = joinDuringHand ? 0 : amount;
        queuedRebuy[side] = joinDuringHand ? amount : 0;
        roundBet[side] = 0;
        pendingBet[side] = 0;
        contribution[side] = joinDuringHand ? botContribution : 0;
        hole[side][0] = null;
        hole[side][1] = null;
        try {
            arena.addPlayer(side, player);
        } catch (RuntimeException | Error throwable) {
            manager.cashOut(player.getUniqueId(), amount, "mid-hand join failure rollback");
            clearSeat(side);
            plugin.getLogger().severe("A player failed to join a running Hold'em arena: " + throwable);
            Text.send(player, Messages.msg("poker.join.arena-failed"));
            return false;
        }
        manager.attached(this, player.getUniqueId());
        broadcast(replacedBot == null
                ? Messages.msg("poker.join.broadcast", "player", player.getName(),
                        "count", currentPlayerCount(), "max", MAX_SEATS)
                : Messages.msg("poker.join.broadcast-replaced", "player", player.getName(),
                        "count", currentPlayerCount(), "max", MAX_SEATS, "bot", replacedBot));
        send(side, Messages.msg(handPaused
                ? "poker.join.bought-paused" : "poker.join.bought-live", "amount", amount));
        syncArena();
        return true;
    }

    boolean chipAction(Player player, Material material, boolean merge) {
        if (PokerChips.value(material) <= 0) return false;
        int side = side(player.getUniqueId());
        if (side < 0 || ended) return false;
        if (handPaused || revealing) {
            send(side, Messages.msg("poker.action.not-betting-phase"));
            return true;
        }
        if (folded[side]) {
            send(side, Messages.msg("poker.chips.folded-no-sort"));
            return true;
        }
        if (merge) {
            arena.mergeChips(player, Math.max(0, stack[side] - pendingBet[side]));
            return true;
        }
        if (arena.splitChip(player, material)) return true;
        refresh();
        send(side, Messages.msg("poker.chips.split-failed"));
        return true;
    }

    boolean splitChip(Player player, Material material) {
        return chipAction(player, material, false);
    }

    void mergeChips(Player player) {
        int side = side(player.getUniqueId());
        if (side < 0 || ended) return;
        if (handPaused || revealing) {
            send(side, Messages.msg("poker.action.not-betting-phase"));
            return;
        }
        if (folded[side]) {
            send(side, Messages.msg("poker.chips.folded-no-sort"));
            return;
        }
        arena.mergeChips(player, Math.max(0, stack[side] - pendingBet[side]));
    }

    private void confirmPlaced(int side) {
        int amount = pendingBet[side];
        int needed = Math.max(0, currentBet - roundBet[side]);
        boolean allIn = amount == stack[side] && stack[side] > 0;
        if (amount < needed && !allIn) {
            send(side, Messages.msg("poker.action.need-more", "amount", needed - amount));
            return;
        }
        int target = roundBet[side] + amount;
        if (target > currentBet) {
            int raiseSize = target - currentBet;
            if (!hasRaiseRights(side)) {
                send(side, Messages.msg("poker.action.raise-locked"));
                return;
            }
            if (raiseSize < lastRaise && !allIn) {
                send(side, Messages.msg("poker.action.raise-short", "amount", lastRaise - raiseSize));
                return;
            }
            pendingBet[side] = 0;
            betTo(side, target);
            return;
        }
        pendingBet[side] = 0;
        int paid = Math.min(amount, stack[side]);
        post(side, paid);
        acted.add(side);
        send(side, needed == 0 && paid == 0 ? Messages.msg("poker.action.checked")
                : Messages.msg(stack[side] == 0 ? "poker.action.all-in" : "poker.action.bet-confirmed",
                        "amount", paid));
        afterAction(side);
    }

    private void returnPending(int side) {
        if (pendingBet[side] == 0) {
            send(side, Messages.msg("poker.action.no-pending"));
            return;
        }
        pendingBet[side] = 0;
        send(side, Messages.msg("poker.action.pending-returned"));
        refresh();
    }

    private boolean hasRaiseRights(int side) {
        return !acted.contains(side) || roundBet[side] >= currentBet;
    }

    private void betTo(int side, int target) {
        int oldCurrent = currentBet;
        post(side, target - roundBet[side]);
        if (target > oldCurrent) {
            int raiseSize = target - oldCurrent;
            currentBet = target;
            if (raiseSize >= lastRaise) {
                lastRaise = raiseSize;
                acted.clear();
            }
        }
        acted.add(side);
        send(side, stack[side] == 0
                ? Messages.msg("poker.action.all-in-total", "amount", roundBet[side])
                : target > oldCurrent ? Messages.msg("poker.action.raised-to", "amount", target)
                : Messages.msg("poker.action.called-to", "amount", roundBet[side]));
        afterAction(side);
    }

    private void afterAction(int side) {
        if (ended) return;
        if (notFoldedCount() == 1) {
            awardUncontested(Messages.msg("poker.reason.everyone-folded"));
            return;
        }
        if (roundComplete()) {
            advanceStreet();
            return;
        }
        int next = nextRequiredAfter(side);
        if (next < 0) {
            advanceStreet();
            return;
        }
        actor = next;
        resetDeadline();
        refresh();
        announceTurn();
    }

    private boolean roundComplete() {
        for (int side = 0; side < players.length; side++) {
            if (folded[side] || stack[side] == 0) continue;
            if (!acted.contains(side) || roundBet[side] != currentBet) return false;
        }
        return true;
    }

    private void advanceStreet() {
        if (street == Street.RIVER) {
            showdown();
            return;
        }
        for (int side = 0; side < players.length; side++) {
            roundBet[side] = 0;
            pendingBet[side] = 0;
        }
        currentBet = 0;
        lastRaise = bigBlind;
        acted.clear();
        arena.prepareBettingRound();
        if (street == Street.PREFLOP) {
            street = Street.FLOP;
            startGradualFlopReveal();
            return;
        } else if (street == Street.FLOP) {
            street = Street.TURN;
            board.add(draw());
        } else {
            street = Street.RIVER;
            board.add(draw());
        }
        arena.lightningTable();
        arena.flashLamps();
        finishStreetAdvance();
    }

    private void startGradualFlopReveal() {
        actor = -1;
        revealing = true;
        syncArena();
        scheduleNormalFlopCard();
    }

    private void scheduleNormalFlopCard() {
        if (ended || !revealing || board.size() >= 3) return;
        int delaySeconds = Math.max(1,
                plugin.getConfig().getInt("poker.flop-card-reveal-delay-seconds", 2));
        arena.syncCenterMessage(Messages.msg("poker.reveal.flop-title"),
                Messages.msg("poker.reveal.flop-subtitle", "index", board.size() + 1,
                        "seconds", delaySeconds), pot);
        Bukkit.getScheduler().runTaskLater(plugin, this::revealNormalFlopCard, delaySeconds * 20L);
    }

    private void revealNormalFlopCard() {
        if (ended || !revealing || board.size() >= 3) return;
        board.add(draw());
        arena.lightningTable();
        arena.flashLamps();
        syncArena();
        if (board.size() < 3) {
            scheduleNormalFlopCard();
            return;
        }
        revealing = false;
        finishStreetAdvance();
    }

    private void finishStreetAdvance() {
        if (canActCount() <= 1) {
            runOutAndShowdown();
            return;
        }
        actor = nextRequiredAfter(dealer);
        if (actor < 0) {
            runOutAndShowdown();
            return;
        }
        resetDeadline();
        refresh();
        announceTurn();
    }

    private void runOutAndShowdown() {
        if (ended || revealing || handPaused) return;
        actor = -1;
        revealing = true;
        syncArena();
        broadcast(Messages.msg("poker.reveal.all-in-broadcast"));
        scheduleRunoutStep();
    }

    private void scheduleRunoutStep() {
        if (ended || !revealing) return;
        String next = board.size() < 3
                ? Messages.msg("poker.reveal.next-flop", "index", board.size() + 1)
                : board.size() == 3 ? Messages.msg("poker.reveal.next-turn")
                : board.size() == 4 ? Messages.msg("poker.reveal.next-river")
                : Messages.msg("poker.reveal.next-showdown");
        int delaySeconds = board.size() < 3
                ? Math.max(1, plugin.getConfig().getInt("poker.flop-card-reveal-delay-seconds", 2))
                : Math.max(1, plugin.getConfig().getInt("poker.all-in-reveal-delay-seconds", 5));
        arena.syncCenterMessage(Messages.msg("poker.reveal.all-in-title"),
                Messages.msg("poker.reveal.all-in-subtitle", "seconds", delaySeconds, "next", next),
                pot);
        Bukkit.getScheduler().runTaskLater(plugin, this::revealRunoutStep, delaySeconds * 20L);
    }

    private void revealRunoutStep() {
        if (ended || !revealing) return;
        if (board.size() < 3) {
            street = Street.FLOP;
            board.add(draw());
        } else if (board.size() == 3) {
            street = Street.TURN;
            board.add(draw());
        } else if (board.size() == 4) {
            street = Street.RIVER;
            board.add(draw());
        } else {
            revealing = false;
            showdown();
            return;
        }
        arena.lightningTable();
        arena.flashLamps();
        syncArena();
        scheduleRunoutStep();
    }

    private void showdown() {
        if (ended) return;
        long[] scores = new long[players.length];
        for (int side = 0; side < players.length; side++) {
            if (folded[side]) continue;
            List<PokerCard> cards = new ArrayList<>(board);
            cards.add(hole[side][0]);
            cards.add(hole[side][1]);
            scores[side] = PokerHandEvaluator.evaluate(cards);
            broadcast(Messages.msg("poker.showdown.reveal", "player", names[side],
                    "cards", cards(hole[side]), "hand", PokerHandEvaluator.name(scores[side])));
        }
        arena.syncBoard(board, peekBoard());
        broadcastBoard(Messages.msg("poker.board.final"));
        syncArena();
        Set<Integer> winningSides = distributePot(scores);
        if (winningSides.isEmpty()) {
            long best = 0L;
            for (int side = 0; side < players.length; side++) if (!folded[side]) best = Math.max(best, scores[side]);
            for (int side = 0; side < players.length; side++) {
                if (!folded[side] && scores[side] == best) winningSides.add(side);
            }
        }
        settleWithWinners(winningSides, Messages.msg("poker.reason.showdown"));
    }

    private Set<Integer> distributePot(long[] scores) {
        Set<Integer> winningSides = new HashSet<>();
        TreeSet<Integer> levels = new TreeSet<>();
        for (int value : contribution) if (value > 0) levels.add(value);
        int rakeablePot = 0;
        int previousLevel = 0;
        for (int level : levels) {
            int contributors = 0;
            for (int value : contribution) if (value >= level) contributors++;
            int layer = (level - previousLevel) * contributors;
            previousLevel = level;
            if (contributors > 1) rakeablePot += layer;
        }
        lastHandRake = calculateRake(rakeablePot);
        int rakeTaken = 0;
        int cumulativeRakeable = 0;
        int previous = 0;
        for (int level : levels) {
            List<Integer> contributors = new ArrayList<>();
            for (int side = 0; side < players.length; side++) {
                if (contribution[side] >= level) contributors.add(side);
            }
            int layer = (level - previous) * contributors.size();
            previous = level;
            if (layer <= 0) continue;
            if (contributors.size() == 1) {
                stack[contributors.getFirst()] += layer;
                continue;
            }
            cumulativeRakeable += layer;
            int targetRake = rakeablePot == 0 ? 0
                    : (int) ((long) cumulativeRakeable * lastHandRake / rakeablePot);
            int layerRake = targetRake - rakeTaken;
            rakeTaken = targetRake;
            int distributable = layer - layerRake;
            List<Integer> eligible = contributors.stream().filter(side -> !folded[side]).toList();
            if (eligible.isEmpty()) {
                eligible = new ArrayList<>();
                for (int side = 0; side < players.length; side++) if (!folded[side]) eligible.add(side);
            }
            long best = eligible.stream().mapToLong(side -> scores[side]).max().orElse(0L);
            List<Integer> winners = eligible.stream().filter(side -> scores[side] == best).toList();
            winningSides.addAll(winners);
            int share = distributable / winners.size();
            int remainder = distributable % winners.size();
            for (int index = 0; index < winners.size(); index++) {
                int side = winners.get(index);
                int award = share + (index < remainder ? 1 : 0);
                stack[side] += award;
                handAwards[side] += award;
            }
        }
        pot = 0;
        manager.collectRake(lastHandRake);
        return winningSides;
    }

    private void awardUncontested(String reason) {
        int winner = -1;
        for (int side = 0; side < players.length; side++) if (!folded[side]) winner = side;
        if (winner < 0) return;
        lastHandRake = calculateRake(pot);
        int award = pot - lastHandRake;
        stack[winner] += award;
        handAwards[winner] += award;
        pot = 0;
        manager.collectRake(lastHandRake);
        while (board.size() < 5) board.add(draw());
        street = Street.RIVER;
        arena.lightningTable();
        arena.flashLamps();
        syncArena();
        broadcastBoard(Messages.msg("poker.board.final"));
        settleWithWinners(Set.of(winner), reason);
    }

    private void settleWithWinners(Set<Integer> winningSides, String reason) {
        List<UUID> winners = new ArrayList<>();
        for (int side : winningSides) winners.add(players[side]);
        lastWinningSides = Set.copyOf(winningSides);
        actor = -1;
        revealing = false;
        handPaused = true;
        syncArena();
        arena.revealAllHoleCards();
        arena.celebrateWinners(winners);
        manager.recordHand(this, winners, reason);
        String nameSeparator = Messages.msg("poker.common.name-separator");
        String winnerNames = winningSides.stream().map(side -> names[side])
                .reduce((first, second) -> first + nameSeparator + second)
                .orElse(Messages.msg("poker.common.nobody"));
        String moneySeparator = Messages.msg("poker.settle.winner-separator");
        String winnerMoney = winningSides.stream().sorted().map(side -> {
            int net = stack[side] - handInitialStacks[side];
            return Messages.msg("poker.settle.winner-line", "player", names[side],
                    "amount", handAwards[side], "net", (net >= 0 ? "+" : "") + net);
        }).reduce((first, second) -> first + moneySeparator + second)
                .orElse(Messages.msg("poker.settle.no-winner"));
        String stackSeparator = Messages.msg("poker.settle.stack-separator");
        String currentAssets = java.util.stream.IntStream.range(0, players.length)
                .filter(side -> seated[side])
                .mapToObj(side -> Messages.msg("poker.settle.stack-entry", "player", names[side],
                        "stack", stack[side]))
                .reduce((first, second) -> first + stackSeparator + second)
                .orElse(Messages.msg("poker.settle.stack-none"));
        int totalAssets = java.util.Arrays.stream(stack).sum();
        broadcast(Messages.msg("poker.settle.broadcast-result", "hand", handNumber,
                "winners", winnerNames, "reason", reason));
        broadcast(Messages.msg("poker.settle.broadcast-winnings", "winnings", winnerMoney));
        broadcast(Messages.msg("poker.settle.broadcast-rake", "rake", lastHandRake,
                "percent", formatRakePercent()));
        broadcast(Messages.msg("poker.settle.broadcast-stacks", "stacks", currentAssets,
                "total", totalAssets));
        broadcast(Messages.msg("poker.settle.broadcast-next"));
        for (int side = 0; side < players.length; side++) {
            if (seated[side] && stack[side] <= 0 && queuedRebuy[side] <= 0) {
                send(side, Messages.msg("poker.settle.busted"));
            } else if (seated[side] && queuedRebuy[side] > 0) {
                send(side, Messages.msg("poker.settle.rebuy-locked", "amount", queuedRebuy[side]));
            }
        }
        arena.syncCenterMessage(Messages.msg("poker.settle.center-title", "hand", handNumber),
                Messages.msg("poker.settle.center-subtitle", "winners", winnerNames,
                        "winnings", winnerMoney, "rake", lastHandRake,
                        "percent", formatRakePercent(), "stacks", currentAssets,
                        "total", totalAssets), pot);
        long delay = Math.max(1, plugin.getConfig().getInt("poker.next-hand-delay-seconds", 10)) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, this::continueOrFinishSession, delay);
    }

    private void continueOrFinishSession() {
        if (ended || !handPaused) return;
        arena.clearCelebration();
        for (int side = 0; side < players.length; side++) {
            if (!seated[side] || queuedRebuy[side] <= 0) continue;
            stack[side] += queuedRebuy[side];
            send(side, Messages.msg("poker.settle.rebuy-applied", "amount", queuedRebuy[side]));
            queuedRebuy[side] = 0;
        }
        for (int side = 0; side < players.length; side++) {
            if (botSeat[side]) {
                seated[side] = true;
                if (stack[side] < carryLimit) {
                    int refill = carryLimit - stack[side];
                    stack[side] = carryLimit;
                    broadcast(Messages.msg("poker.bot.refill", "player", names[side],
                            "amount", refill));
                }
                continue;
            }
            Player player = Bukkit.getPlayer(players[side]);
            if (seated[side] && leaveAfterHand[side]) {
                seated[side] = false;
                manager.detached(this, players[side]);
                drawVotes.remove(players[side]);
                int result = stack[side] > 0
                        ? cashOutSeat(side, "scheduled leave after the hand") : 0;
                int amount = Math.abs(result);
                if (player != null) {
                    Text.send(player, Messages.msg(result >= 0
                                    ? "poker.leave.auto-paid" : "poker.leave.auto-pending",
                            "amount", plugin.economy().format(amount)));
                    arena.release(player);
                }
                continue;
            }
            if (seated[side] && (player == null || !arena.protects(players[side]))) {
                seated[side] = false;
                manager.detached(this, players[side]);
            }
            if (!seated[side] && stack[side] > 0) cashOutSeat(side, "returning chips after leaving");
            if (seated[side] && stack[side] <= 0) {
                seated[side] = false;
                manager.detached(this, players[side]);
                if (player != null) {
                    Text.send(player, Messages.msg("poker.leave.busted"));
                    arena.release(player);
                }
            }
        }
        if (realSeatedCount() < 1) {
            ended = true;
            handPaused = false;
            manager.finishSession(this, sessionWinnerIds(), Messages.msg("poker.reason.no-humans-left"));
            return;
        }
        startNextHand();
    }

    private void startNextHand() {
        releaseVacantSeats();
        replenishBots();
        handNumber++;
        handPaused = false;
        revealing = false;
        drawVotes.clear();
        deck = PokerCard.shuffledDeck();
        deckIndex = 0;
        board.clear();
        pot = 0;
        currentBet = 0;
        lastRaise = bigBlind;
        acted.clear();
        java.util.Arrays.fill(handAwards, 0);
        lastHandRake = 0;
        handRakeRate = configuredRakeRate();
        for (int side = 0; side < players.length; side++) {
            roundBet[side] = 0;
            contribution[side] = 0;
            pendingBet[side] = 0;
            folded[side] = !seated[side];
            hole[side][0] = null;
            hole[side][1] = null;
        }
        dealHoleCards();
        handInitialStacks = stack.clone();
        handStartedAt = System.currentTimeMillis();
        street = Street.PREFLOP;
        dealer = nextSeatedAfter(dealer);
        smallBlindSide = seatedCount() == 2 ? dealer : nextSeatedAfter(dealer);
        bigBlindSide = nextSeatedAfter(smallBlindSide);
        post(smallBlindSide, smallBlind);
        post(bigBlindSide, bigBlind);
        currentBet = Math.max(roundBet[smallBlindSide], roundBet[bigBlindSide]);
        actor = nextCanActAfter(bigBlindSide);
        arena.prepareNextHand(hole);
        broadcast(Messages.msg("poker.hand.next-started", "hand", handNumber,
                "dealer", names[dealer], "small", smallBlind, "big", bigBlind));
        resetDeadline();
        syncArena();
        if (actor < 0 || (canActCount() <= 1 && roundComplete())) runOutAndShowdown();
        else announceTurn();
    }

    private int seatedCount() {
        int count = 0;
        for (boolean value : seated) if (value) count++;
        return count;
    }

    private int nextSeatedAfter(int after) {
        for (int step = 1; step <= players.length; step++) {
            int side = (after + step) % players.length;
            if (seated[side] && stack[side] > 0) return side;
        }
        return -1;
    }

    private List<UUID> sessionWinnerIds() {
        List<UUID> result = new ArrayList<>();
        for (int side = 0; side < players.length; side++) {
            if (realSeat[side] && seated[side] && stack[side] > 0) result.add(players[side]);
        }
        if (!result.isEmpty()) return result;
        for (int side : lastWinningSides) if (realSeat[side]) result.add(players[side]);
        return result;
    }

    void fold(UUID player, String reason) {
        if (ended) return;
        int side = side(player);
        if (side < 0 || folded[side]) return;
        pendingBet[side] = 0;
        folded[side] = true;
        acted.add(side);
        arena.punishFold(side);
        broadcast(Messages.msg("poker.action.folded-broadcast", "player", names[side],
                "reason", reason));
        if (notFoldedCount() == 1) {
            awardUncontested(Messages.msg("poker.reason.everyone-folded"));
            return;
        }
        if (side == actor) afterAction(side);
        else if (roundComplete()) advanceStreet();
        else refresh();
    }

    void leave(Player player, String reason) {
        leave(player, reason, false);
    }

    void disconnect(Player player) {
        leave(player, Messages.msg("poker.reason.disconnected"), true);
    }

    private void leave(Player player, String reason, boolean forceFold) {
        int side = side(player.getUniqueId());
        if (side < 0 || !seated[side]) return;
        if (!ended && !handPaused && !folded[side] && (forceFold || stack[side] > 0)) {
            fold(player.getUniqueId(), reason);
        } else if (!ended && !handPaused && !folded[side]) {
            broadcast(Messages.msg("poker.leave.all-in-kept", "player", names[side]));
        }
        seated[side] = false;
        if (!ended && (stack[side] > 0 || queuedRebuy[side] > 0)) {
            int result = cashOutSeat(side, "leaving the table mid-session");
            boolean paid = result >= 0;
            int amount = Math.abs(result);
            Text.send(player, Messages.msg(paid ? "poker.leave.paid" : "poker.leave.pending",
                    "amount", plugin.economy().format(amount)));
            refresh();
        }
        manager.detached(this, player.getUniqueId());
        drawVotes.remove(player.getUniqueId());
        arena.release(player);
        if (!ended && realSeatedCount() < 1) {
            ended = true;
            actor = -1;
            manager.finishSession(this, List.of(), Messages.msg("poker.reason.all-left-table"));
        }
    }

    private int cashOutSeat(int side, String reason) {
        int amount = Math.max(0, stack[side] + queuedRebuy[side]);
        stack[side] = 0;
        queuedRebuy[side] = 0;
        cashOuts[side] += amount;
        return manager.cashOut(players[side], amount, reason) ? amount : -amount;
    }

    private void settleMutualDraw() {
        int totalFee = 0;
        for (int side = 0; side < players.length; side++) {
            if (botSeat[side]) {
                stack[side] = 0;
                queuedRebuy[side] = 0;
                pendingBet[side] = 0;
                continue;
            }
            int gross = Math.max(0, stack[side] + contribution[side] + queuedRebuy[side]);
            int payout = (int) Math.floor(gross * 0.95);
            totalFee += gross - payout;
            stack[side] = payout;
            queuedRebuy[side] = 0;
            pendingBet[side] = 0;
        }
        pot = 0;
        actor = -1;
        ended = true;
        syncArena();
        manager.settleDraw(this, totalFee, Messages.msg("poker.reason.mutual-draw"));
    }

    void tick() {
        if (ended || actor < 0) return;
        if (botSeat[actor] && System.currentTimeMillis() >= botActAt) {
            actBot(actor);
            return;
        }
        if (System.currentTimeMillis() >= deadline) {
            fold(players[actor], Messages.msg("poker.reason.timeout", "player", names[actor]));
            return;
        }
        showCountdown();
    }

    private void actBot(int side) {
        if (ended || handPaused || revealing || actor != side || !botSeat[side] || folded[side]) return;
        int samples = Math.max(40, Math.min(600, plugin.getConfig().getInt("casino-bots.poker-samples", 180)));
        int toCall = Math.max(0, currentBet - roundBet[side]);
        PokerBotStrategy.Decision decision = PokerBotStrategy.decide(hole[side], board,
                Math.max(1, notFoldedCount() - 1), stack[side], toCall, pot, samples,
                CasinoBot.profile(archetypeAt(side)), ThreadLocalRandom.current());
        broadcast(Messages.msg("poker.bot.decided", "player", names[side],
                "action", botActionName(decision.action())));
        switch (decision.action()) {
            case CHECK -> confirmPlaced(side);
            case CALL -> betTo(side, Math.min(currentBet, roundBet[side] + stack[side]));
            case RAISE -> {
                int step = Math.max(lastRaise, bigBlind);
                long wanted = (long) currentBet + (long) step * Math.max(1, decision.raiseSteps());
                int maximum = roundBet[side] + stack[side];
                if (wanted <= maximum && hasRaiseRights(side)) betTo(side, (int) wanted);
                else if (maximum > currentBet && decision.equity() > 0.80 && hasRaiseRights(side)) allIn(side);
                else betTo(side, Math.min(currentBet, maximum));
            }
            case ALL_IN -> allIn(side);
            case FOLD -> fold(players[side], Messages.msg("poker.reason.bot-fold"));
        }
    }

    private static String botActionName(PokerBotStrategy.Action action) {
        return Messages.msg(switch (action) {
            case CHECK -> "poker.bot.action.check";
            case CALL -> "poker.bot.action.call";
            case RAISE -> "poker.bot.action.raise";
            case ALL_IN -> "poker.bot.action.all-in";
            case FOLD -> "poker.bot.action.fold";
        });
    }

    private void announceTurn() {
        if (actor < 0) return;
        for (int side = 0; side < players.length; side++) {
            if (folded[side]) continue;
            Player player = Bukkit.getPlayer(players[side]);
            if (player == null || arena == null || !arena.protects(players[side])) continue;
            if (side == actor) {
                Text.send(player, Messages.msg("poker.turn.yours",
                        "amount", Math.max(0, currentBet - roundBet[side]),
                        "seconds", actionTimeoutSeconds));
            } else Text.send(player, Messages.msg("poker.turn.waiting", "player", names[actor]));
        }
        showCountdown();
    }

    private void showCountdown() {
        if (arena == null || actor < 0 || ended) return;
        int seconds = Math.max(1, (int) Math.ceil((deadline - System.currentTimeMillis()) / 1000.0));
        String color = seconds <= 5 ? "<red>" : seconds <= 10 ? "<gold>" : "<green>";
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO);
        for (int side = 0; side < players.length; side++) {
            Player player = Bukkit.getPlayer(players[side]);
            if (player == null || !arena.protects(players[side])) continue;
            String subtitle = side == actor ? Messages.msg("poker.turn.title-yours")
                    : Messages.msg("poker.turn.title-waiting", "player", names[actor]);
            player.showTitle(Title.title(Text.parse(color + "<bold>" + seconds + "</bold>"),
                    Text.parse(subtitle), times));
        }
        syncCenterStatus();
    }

    private String role(int side) {
        List<String> roles = new ArrayList<>();
        if (side == dealer) roles.add(Messages.msg("poker.role.dealer"));
        if (side == smallBlindSide) roles.add(Messages.msg("poker.role.small-blind"));
        if (side == bigBlindSide) roles.add(Messages.msg("poker.role.big-blind"));
        return roles.isEmpty() ? Messages.msg("poker.role.none") : String.join("/", roles);
    }

    private String cards(PokerCard[] pair) { return pair[0].display() + " " + pair[1].display(); }

    /** 全息之外的可靠兜底；结算时所有留桌玩家都能在聊天中核对完整河牌。 */
    private void broadcastBoard(String title) {
        String separator = Messages.msg("poker.board.separator");
        String values = board.stream().map(PokerCard::display)
                .reduce((first, second) -> first + separator + second)
                .orElse(Messages.msg("poker.board.empty"));
        broadcast(Messages.msg("poker.board.broadcast", "title", title, "cards", values));
    }

    private void refresh() {
        syncArena();
    }

    private void post(int side, int amount) {
        int actual = Math.max(0, Math.min(amount, stack[side]));
        stack[side] -= actual;
        roundBet[side] += actual;
        contribution[side] += actual;
        pot += actual;
    }

    private int nextSeat(int after) { return (after + 1) % players.length; }

    private int nextCanActAfter(int after) {
        for (int step = 1; step <= players.length; step++) {
            int side = (after + step) % players.length;
            if (!folded[side] && stack[side] > 0) return side;
        }
        return -1;
    }

    private int nextRequiredAfter(int after) {
        for (int step = 1; step <= players.length; step++) {
            int side = (after + step) % players.length;
            if (!folded[side] && stack[side] > 0
                    && (!acted.contains(side) || roundBet[side] != currentBet)) return side;
        }
        return -1;
    }

    private int canActCount() {
        int count = 0;
        for (int side = 0; side < players.length; side++) if (!folded[side] && stack[side] > 0) count++;
        return count;
    }

    private int notFoldedCount() {
        int count = 0;
        for (boolean value : folded) if (!value) count++;
        return count;
    }

    private void send(int side, String message) {
        Player player = Bukkit.getPlayer(players[side]);
        if (player != null && arena != null && arena.protects(players[side])) Text.send(player, message);
    }

    private void broadcast(String message) {
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && arena != null && arena.protects(id)) Text.send(player, message);
        }
    }

    private void dealHoleCards() {
        for (int side = 0; side < players.length; side++) {
            if (!seated[side]) continue;
            if (botSeat[side]) continue;
            int effect = plugin.luck().roll(GameType.POKER, players[side]);
            if (effect == 0) continue;
            PokerCard[] weighted = effect > 0 ? LuckyDeal.takeTexas(deck) : LuckyDeal.takeBadTexas(deck);
            hole[side][0] = weighted[0];
            hole[side][1] = weighted[1];
        }
        for (int card = 0; card < 2; card++) {
            for (int side = 0; side < players.length; side++) {
                if (seated[side] && hole[side][card] == null) hole[side][card] = draw();
            }
        }
    }

    private PokerCard draw() { return deck.get(deckIndex++); }

    /**
     * 管理员 floppeek 用：返回本手完整五张公牌，含尚未发出的部分。
     * 每手开始时 deck 只洗一次，LuckyDeal 的取牌与重洗全部发生在 dealHoleCards() 的首次 draw() 之前，
     * 此后 deck 不再变动，因此后续公牌就是 deck 中 deckIndex 之后的连续几张。
     */
    List<PokerCard> peekBoard() {
        List<PokerCard> full = new ArrayList<>(board);
        for (int offset = 0; full.size() < 5; offset++) {
            int index = deckIndex + offset;
            if (index < 0 || index >= deck.size()) break;
            full.add(deck.get(index));
        }
        return full;
    }

    private double configuredRakeRate() {
        double rate = plugin.getConfig().getDouble("poker.rake-rate", 0.005);
        return Double.isFinite(rate) ? Math.max(0.0, Math.min(0.20, rate)) : 0.005;
    }

    private int calculateRake(int amount) {
        return Math.max(0, Math.min(amount, (int) Math.floor(amount * handRakeRate)));
    }

    private String formatRakePercent() {
        return java.math.BigDecimal.valueOf(handRakeRate * 100.0)
                .stripTrailingZeros().toPlainString();
    }

    private void resetDeadline() {
        long now = System.currentTimeMillis();
        deadline = now + actionTimeoutSeconds * 1000L;
        if (actor >= 0 && botSeat[actor]) {
            int minimum = Math.max(1, plugin.getConfig().getInt("casino-bots.think-min-seconds", 2));
            int maximum = Math.max(minimum, plugin.getConfig().getInt("casino-bots.think-max-seconds", 5));
            botActAt = now + ThreadLocalRandom.current().nextLong(minimum, (long) maximum + 1L) * 1000L;
        } else botActAt = Long.MAX_VALUE;
    }

    private int side(UUID player) {
        for (int side = 0; side < players.length; side++) if (players[side].equals(player)) return side;
        return -1;
    }

    /** 真人入座时随机顶掉一个 BOT，而不是固定挤走第一个座位。 */
    private int availableSeat() {
        List<Integer> candidates = new ArrayList<>();
        for (int side = 0; side < players.length; side++) if (!realSeat[side]) candidates.add(side);
        if (candidates.isEmpty()) return -1;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private void releaseVacantSeats() {
        for (int side = 0; side < players.length; side++) {
            if (realSeat[side] && !seated[side]) clearSeat(side);
        }
    }

    private void clearSeat(int side) {
        installBot(side);
        if (arena != null) arena.setBotSeat(side, players[side], names[side]);
        folded[side] = true;
        leaveAfterHand[side] = false;
        roundBet[side] = 0;
        contribution[side] = 0;
        pendingBet[side] = 0;
        queuedRebuy[side] = 0;
        initialStacks[side] = 0;
        cashOuts[side] = 0;
        handAwards[side] = 0;
        hole[side][0] = null;
        hole[side][1] = null;
    }

    private void clearIdentity(int side) {
        players[side] = new UUID(0L, side + 1L);
        // 和 PokerArena 共用同一个哨兵：座位名会原样 clone 给竞技场，
        // 两边必须比得上，所以这里绝不能放本地化文本。
        names[side] = PokerArena.EMPTY_SEAT_NAME;
        realSeat[side] = false;
        botSeat[side] = false;
        botArchetype[side] = -1;
    }

    private void installBot(int side) {
        botArchetype[side] = pickArchetype();
        players[side] = CasinoBot.id("poker", hostId, side);
        names[side] = CasinoBot.name(botArchetype[side]);
        realSeat[side] = false;
        botSeat[side] = true;
        seated[side] = true;
        stack[side] = carryLimit;
        initialStacks[side] = carryLimit;
    }

    /** 随机挑一个本桌还没出现过的原型；六种都占满了就随便挑一个。 */
    private int pickArchetype() {
        List<Integer> free = new ArrayList<>();
        for (int index = 0; index < CasinoBot.archetypeCount(); index++) {
            boolean used = false;
            for (int seat = 0; seat < MAX_SEATS; seat++) {
                if (botSeat[seat] && botArchetype[seat] == index) {
                    used = true;
                    break;
                }
            }
            if (!used) free.add(index);
        }
        if (free.isEmpty()) return ThreadLocalRandom.current().nextInt(CasinoBot.archetypeCount());
        return free.get(ThreadLocalRandom.current().nextInt(free.size()));
    }

    private int archetypeAt(int side) {
        return botArchetype[side] < 0 ? side : botArchetype[side];
    }

    private void replenishBots() {
        for (int side = 0; side < players.length; side++) {
            if (!botSeat[side]) continue;
            seated[side] = true;
            if (stack[side] < carryLimit) stack[side] = carryLimit;
            initialStacks[side] = stack[side];
        }
    }

    private List<String> participantsNames() {
        List<String> result = new ArrayList<>();
        for (int side = 0; side < players.length; side++) if (seated[side]) result.add(names[side]);
        return result;
    }

    private int currentPlayerCount() {
        int count = 0;
        for (boolean value : realSeat) if (value) count++;
        return count;
    }

    private int realSeatedCount() {
        int count = 0;
        for (int side = 0; side < players.length; side++) if (realSeat[side] && seated[side]) count++;
        return count;
    }

    private void syncArena() {
        if (arena == null) return;
        arena.syncBoard(board, peekBoard());
        arena.syncHandStrengths(board, hole, folded, seated);
        int[] wins = new int[players.length];
        double[] winRates = new double[players.length];
        for (int side = 0; side < players.length; side++) {
            if (!realSeat[side]) continue;
            PokerRecords.Stats stats = manager.stats(players[side]);
            wins[side] = stats.wins();
            winRates[side] = stats.winRate();
        }
        arena.syncPlayers(stack, roundBet, pendingBet, folded, seated, dealer, smallBlindSide, bigBlindSide,
                actor, wins, winRates);
        syncCenterStatus();
        arena.syncInventories(stack, pendingBet, hole, folded);
    }

    private void syncCenterStatus() {
        if (arena == null) return;
        int seconds = actor < 0 || ended ? 0
                : Math.max(0, (int) Math.ceil((deadline - System.currentTimeMillis()) / 1000.0));
        arena.syncCenter(actor, seconds, stack, roundBet, pendingBet, contribution, queuedRebuy,
                seated, pot, carryLimit, !handPaused);
    }

    PokerCard[][] holeCards() {
        PokerCard[][] result = new PokerCard[hole.length][2];
        for (int side = 0; side < hole.length; side++) result[side] = hole[side].clone();
        return result;
    }
    UUID[] seatPlayers() { return players.clone(); }
    String[] seatNames() { return names.clone(); }

    void attachArena(PokerArena arena) { this.arena = arena; }
    boolean protects(UUID player) { return arena != null && arena.protects(player); }
    boolean contains(org.bukkit.Location location) { return arena != null && arena.contains(location); }
    void closeArena() { if (arena != null) arena.close(); }
    void hidePrivateFrom(Player player) { if (arena != null) arena.hidePrivateFrom(player); }
    void refreshArena() { syncArena(); }

    List<UUID> players() { return List.of(players); }
    List<String> names() { return List.of(names); }
    List<PokerCard> board() { return List.copyOf(board); }
    int[] stacks() { return stack.clone(); }
    int[] initialStacks() { return initialStacks.clone(); }
    int[] cashOuts() { return cashOuts.clone(); }
    int[] abortRefunds() {
        int[] refunds = new int[players.length];
        for (int side = 0; side < players.length; side++) {
            refunds[side] = Math.max(0, stack[side] + contribution[side] + queuedRebuy[side]);
        }
        return refunds;
    }
    int smallBlind() { return smallBlind; }
    int bigBlind() { return bigBlind; }
    int carryLimit() { return carryLimit; }
    long startedAt() { return startedAt; }
    long handStartedAt() { return handStartedAt; }
    int[] handInitialStacks() { return handInitialStacks.clone(); }
    int handNumber() { return handNumber; }
    int playerCount() { return currentPlayerCount(); }
    int seatCapacity() { return players.length; }
    boolean realSeat(int side) { return side >= 0 && side < realSeat.length && realSeat[side]; }
    UUID playerAt(int side) { return players[side]; }
    String nameAt(int side) { return names[side]; }
    UUID hostId() { return hostId; }
    String hostName() { return hostName; }
    boolean joinable() { return !ended && availableSeat() >= 0; }
}
