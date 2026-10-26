package dev.leon.discordbot.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GameSession {
    public static final int MAX_PLAYERS = 9;
    public static final int MIN_PLAYERS = 1;
    public static final int HOLE_CARDS = 2;

    private final Player host;
    private final Map<Long, Player> players = new LinkedHashMap<>();
    private final Map<Long, List<Card>> hands = new LinkedHashMap<>();
    private Deck deck;

    private int round;
    private final Map<Long, Integer> numbers = new LinkedHashMap<>();
    private final Set<Long> ready = new HashSet<>();
    private long overviewMessageId;
    private String lastAction;

    public GameSession(Player host) {
        this.host = host;
        players.put(host.userId(), host);
    }

    public Player host() {
        return host;
    }

    public synchronized List<Player> players() {
        return List.copyOf(players.values());
    }

    public synchronized boolean hasPlayer(long userId) {
        return players.containsKey(userId);
    }

    public synchronized boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public synchronized boolean isStarted() {
        return round > 0;
    }

    public synchronized boolean join(Player player) {
        if (isFull() || players.containsKey(player.userId())) {
            return false;
        }
        players.put(player.userId(), player);
        return true;
    }

    public synchronized Map<Player, List<Card>> dealHands() {
        deck = Deck.shuffled();
        hands.clear();
        for (int i = 0; i < HOLE_CARDS; i++) {
            for (Player player : players.values()) {
                hands.computeIfAbsent(player.userId(), id -> new ArrayList<>(HOLE_CARDS)).add(deck.deal());
            }
        }

        round = 0;
        nextRound();

        Map<Player, List<Card>> dealt = new LinkedHashMap<>();
        for (Player player : players.values()) {
            dealt.put(player, List.copyOf(hands.get(player.userId())));
        }
        return dealt;
    }

    public synchronized int cardsRemaining() {
        return deck == null ? 52 : deck.remaining();
    }

    public synchronized int round() {
        return round;
    }

    public synchronized void nextRound() {
        round++;
        numbers.clear();
        ready.clear();
        overviewMessageId = 0;
        lastAction = null;
    }

    public sealed interface TakeResult {
        record Taken() implements TakeResult {}
        record AlreadyYours() implements TakeResult {}
        record OutOfRange(int max) implements TakeResult {}
    }

    public synchronized TakeResult take(long userId, int number) {
        int max = players.size();
        if (number < 1 || number > max) {
            return new TakeResult.OutOfRange(max);
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

        Player taker = players.get(userId);
        lastAction = previousHolder == null
                ? "%s took %d".formatted(taker.name(), number)
                : "%s took %d from %s".formatted(taker.name(), number, previousHolder.name());
        return new TakeResult.Taken();
    }

    public synchronized Player holderOf(int number) {
        for (Map.Entry<Long, Integer> entry : numbers.entrySet()) {
            if (entry.getValue() == number) {
                return players.get(entry.getKey());
            }
        }
        return null;
    }

    public synchronized boolean hasNumber(long userId) {
        return numbers.containsKey(userId);
    }

    public synchronized boolean allNumbersAssigned() {
        return numbers.size() == players.size();
    }

    public synchronized void setReady(long userId, boolean isReady) {
        if (isReady) {
            ready.add(userId);
        } else {
            ready.remove(userId);
        }
    }

    public synchronized boolean isReady(long userId) {
        return ready.contains(userId);
    }

    public synchronized boolean isRoundComplete() {
        return allNumbersAssigned() && ready.containsAll(players.keySet());
    }

    public synchronized long overviewMessageId() {
        return overviewMessageId;
    }

    public synchronized void setOverviewMessageId(long overviewMessageId) {
        this.overviewMessageId = overviewMessageId;
    }

    public synchronized String lastAction() {
        return lastAction;
    }
}
