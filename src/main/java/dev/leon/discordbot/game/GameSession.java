package dev.leon.discordbot.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameSession {
    public static final int MAX_PLAYERS = 9;
    public static final int MIN_PLAYERS = 2;
    public static final int HOLE_CARDS = 2;

    private final Player host;
    private final Map<Long, Player> players = new LinkedHashMap<>();
    private final Map<Long, List<Card>> hands = new LinkedHashMap<>();
    private Deck deck;

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

    public synchronized boolean join(Player player) {
        if (isFull() || players.containsKey(player.userId())) {
            return false;
        }
        players.put(player.userId(), player);
        return true;
    }

    public synchronized Map<Player, List<Card>> dealRound() {
        deck = Deck.shuffled();
        hands.clear();
        for (int i = 0; i < HOLE_CARDS; i++) {
            for (Player player : players.values()) {
                hands.computeIfAbsent(player.userId(), id -> new ArrayList<>(HOLE_CARDS)).add(deck.deal());
            }
        }

        Map<Player, List<Card>> dealt = new LinkedHashMap<>();
        for (Player player : players.values()) {
            dealt.put(player, List.copyOf(hands.get(player.userId())));
        }
        return dealt;
    }

    public synchronized int cardsRemaining() {
        return deck == null ? 52 : deck.remaining();
    }
}
