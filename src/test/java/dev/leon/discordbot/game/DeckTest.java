package dev.leon.discordbot.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeckTest {

    @Test
    void dealsAll52CardsExactlyOnce() {
        Deck deck = Deck.shuffled();
        Set<Card> dealt = new HashSet<>();
        for (int i = 0; i < 52; i++) {
            dealt.add(deck.deal());
        }
        assertEquals(52, dealt.size());
    }

    @Test
    void throwsWhenEmpty() {
        Deck deck = Deck.shuffled();
        for (int i = 0; i < 52; i++) {
            deck.deal();
        }
        assertThrows(IllegalStateException.class, deck::deal);
    }

    @Test
    void burnRemovesACard() {
        Deck deck = Deck.shuffled();
        deck.burn();
        for (int i = 0; i < 51; i++) {
            deck.deal();
        }
        assertThrows(IllegalStateException.class, deck::deal);
    }

    @Test
    void shufflesIndependently() {
        List<Card> first = dealAll(Deck.shuffled());
        List<Card> second = dealAll(Deck.shuffled());
        assertNotEquals(first, second, "two shuffles produced the same order (1 in 52! chance)");
    }

    private static List<Card> dealAll(Deck deck) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 52; i++) {
            cards.add(deck.deal());
        }
        return cards;
    }
}
