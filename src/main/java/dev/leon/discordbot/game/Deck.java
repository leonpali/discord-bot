package dev.leon.discordbot.game;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public final class Deck {
    private static final Random RANDOM = new SecureRandom();

    private final Deque<Card> cards;

    private Deck(List<Card> shuffled) {
        this.cards = new ArrayDeque<>(shuffled);
    }

    public static Deck shuffled() {
        List<Card> all = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                all.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(all, RANDOM);
        return new Deck(all);
    }

    public Card deal() {
        Card card = cards.pollFirst();
        if (card == null) {
            throw new IllegalStateException("Deck is empty");
        }
        return card;
    }

    public int remaining() {
        return cards.size();
    }
}
