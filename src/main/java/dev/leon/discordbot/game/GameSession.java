package dev.leon.discordbot.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class GameSession {
    public static final int MAX_PLAYERS = 9;
    public static final int MIN_PLAYERS = 1;
    public static final int HOLE_CARDS = 2;

    public enum JoinResult { JOINED, ALREADY_JOINED, FULL, ALREADY_STARTED }

    public sealed interface TakeResult {
        record Taken(Integer previousNumber, Player previousHolder) implements TakeResult {}
        record AlreadyYours() implements TakeResult {}
        record OutOfRange() implements TakeResult {}
    }

    public record Advance(RoundRecord completed, long completedOverviewId, boolean finished) {}

    private final Player host;
    private final Map<Long, Player> players = new LinkedHashMap<>();
    private final Map<Long, List<Card>> hands = new HashMap<>();
    private final List<Card> board = new ArrayList<>();
    private final List<RoundRecord> history = new ArrayList<>();
    private Deck deck;

    private Round round;
    private final Map<Long, Integer> numbers = new HashMap<>();
    private final Set<Long> ready = new HashSet<>();
    private long overviewMessageId;
    private String lastAction;
    private boolean finished;

    public GameSession(Player host) {
        this.host = host;
        players.put(host.userId(), host);
    }

    public Player host() {
        return host;
    }

    public boolean isHost(long userId) {
        return host.userId() == userId;
    }

    public synchronized int playerCount() {
        return players.size();
    }

    public synchronized boolean hasPlayer(long userId) {
        return players.containsKey(userId);
    }

    public synchronized boolean isStarted() {
        return round != null;
    }

    public synchronized JoinResult join(Player player) {
        if (isStarted()) {
            return JoinResult.ALREADY_STARTED;
        }
        if (players.containsKey(player.userId())) {
            return JoinResult.ALREADY_JOINED;
        }
        if (players.size() >= MAX_PLAYERS) {
            return JoinResult.FULL;
        }
        players.put(player.userId(), player);
        return JoinResult.JOINED;
    }

    public synchronized Map<Player, List<Card>> dealHands() {
        deck = Deck.shuffled();
        board.clear();
        history.clear();
        finished = false;

        Map<Long, List<Card>> dealing = new HashMap<>();
        for (int i = 0; i < HOLE_CARDS; i++) {
            for (long userId : players.keySet()) {
                dealing.computeIfAbsent(userId, id -> new ArrayList<>(HOLE_CARDS)).add(deck.deal());
            }
        }

        Map<Player, List<Card>> dealt = new LinkedHashMap<>();
        for (Player player : players.values()) {
            List<Card> hand = List.copyOf(dealing.get(player.userId()));
            hands.put(player.userId(), hand);
            dealt.put(player, hand);
        }

        startRound(Round.PRE_FLOP);
        return dealt;
    }

    private void startRound(Round next) {
        round = next;
        numbers.clear();
        ready.clear();
        overviewMessageId = 0;
        lastAction = null;

        if (next.cardsRevealed() > 0) {
            deck.burn();
            for (int i = 0; i < next.cardsRevealed(); i++) {
                board.add(deck.deal());
            }
        }
    }

    public synchronized Optional<Advance> advanceIfComplete() {
        boolean complete = !finished
                && numbers.size() == players.size()
                && ready.containsAll(players.keySet());
        if (!complete) {
            return Optional.empty();
        }

        RoundRecord completed = new RoundRecord(round, board, assignments());
        history.add(completed);
        long completedOverviewId = overviewMessageId;
        if (round.isLast()) {
            finished = true;
            overviewMessageId = 0;
        } else {
            startRound(round.next());
        }
        return Optional.of(new Advance(completed, completedOverviewId, finished));
    }

    public synchronized boolean attachOverview(Round forRound, long messageId) {
        if (finished || round != forRound) {
            return false;
        }
        overviewMessageId = messageId;
        return true;
    }

    public synchronized boolean isOverview(long messageId) {
        return overviewMessageId != 0 && overviewMessageId == messageId;
    }

    public synchronized TakeResult take(long userId, int number) {
        if (number < 1 || number > players.size()) {
            return new TakeResult.OutOfRange();
        }
        Integer previousNumber = numbers.get(userId);
        if (previousNumber != null && previousNumber == number) {
            return new TakeResult.AlreadyYours();
        }

        Player previousHolder = holderOf(number);
        if (previousHolder != null) {
            numbers.remove(previousHolder.userId());
        }
        numbers.put(userId, number);

        String taker = players.get(userId).name();
        lastAction = previousHolder == null
                ? "%s took %d".formatted(taker, number)
                : "%s took %d from %s".formatted(taker, number, previousHolder.name());
        return new TakeResult.Taken(previousNumber, previousHolder);
    }

    public synchronized boolean release(long userId, int number) {
        Integer held = numbers.get(userId);
        if (held == null || held != number) {
            return false;
        }
        numbers.remove(userId);
        lastAction = "%s gave up %d".formatted(players.get(userId).name(), number);
        return true;
    }

    public synchronized void setReady(long userId, boolean isReady) {
        if (isReady) {
            ready.add(userId);
        } else {
            ready.remove(userId);
        }
    }

    public synchronized RoundView view() {
        List<Player> unassigned = players.values().stream()
                .filter(player -> !numbers.containsKey(player.userId()))
                .toList();
        return new RoundView(round, board, assignments(), unassigned, ready, lastAction, history, overviewMessageId);
    }

    public synchronized List<RoundRecord> history() {
        return List.copyOf(history);
    }

    public synchronized Showdown showdown() {
        if (!finished) {
            throw new IllegalStateException("The game isn't finished yet");
        }
        List<Player> byNumber = assignments();
        List<Showdown.Entry> entries = new ArrayList<>();
        for (int i = 0; i < byNumber.size(); i++) {
            Player player = byNumber.get(i);
            List<Card> hand = hands.get(player.userId());
            List<Card> cards = new ArrayList<>(hand);
            cards.addAll(board);
            entries.add(new Showdown.Entry(i + 1, player, hand, HandEvaluator.best(cards)));
        }
        return new Showdown(board, entries);
    }

    private List<Player> assignments() {
        List<Player> byNumber = new ArrayList<>();
        for (int number = 1; number <= players.size(); number++) {
            byNumber.add(holderOf(number));
        }
        return byNumber;
    }

    private Player holderOf(int number) {
        for (Map.Entry<Long, Integer> entry : numbers.entrySet()) {
            if (entry.getValue() == number) {
                return players.get(entry.getKey());
            }
        }
        return null;
    }
}
