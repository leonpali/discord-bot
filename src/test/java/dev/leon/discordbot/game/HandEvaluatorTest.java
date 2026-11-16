package dev.leon.discordbot.game;

import static dev.leon.discordbot.game.TestCards.best;
import static dev.leon.discordbot.game.TestCards.cards;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HandEvaluatorTest {

    @Test
    void categorisesEveryFiveCardHandWithTheKnownFrequencies() {
        List<Card> deck = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(rank, suit));
            }
        }

        Map<HandCategory, Integer> counts = new EnumMap<>(HandCategory.class);
        for (int a = 0; a < 52; a++) {
            for (int b = a + 1; b < 52; b++) {
                for (int c = b + 1; c < 52; c++) {
                    for (int d = c + 1; d < 52; d++) {
                        for (int e = d + 1; e < 52; e++) {
                            List<Card> five = List.of(deck.get(a), deck.get(b), deck.get(c), deck.get(d), deck.get(e));
                            counts.merge(HandEvaluator.evaluate(five).category(), 1, Integer::sum);
                        }
                    }
                }
            }
        }

        Map<HandCategory, Integer> expected = new EnumMap<>(HandCategory.class);
        expected.put(HandCategory.ROYAL_FLUSH, 4);
        expected.put(HandCategory.STRAIGHT_FLUSH, 36);
        expected.put(HandCategory.FOUR_OF_A_KIND, 624);
        expected.put(HandCategory.FULL_HOUSE, 3_744);
        expected.put(HandCategory.FLUSH, 5_108);
        expected.put(HandCategory.STRAIGHT, 10_200);
        expected.put(HandCategory.THREE_OF_A_KIND, 54_912);
        expected.put(HandCategory.TWO_PAIR, 123_552);
        expected.put(HandCategory.PAIR, 1_098_240);
        expected.put(HandCategory.HIGH_CARD, 1_302_540);
        assertEquals(expected, counts);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "Ah Kh Qh Jh 10h 2c 3d,  ROYAL_FLUSH,     Royal flush",
            "9s 8s 7s 6s 5s Ah Ad,   STRAIGHT_FLUSH,  Straight flush to 9",
            "5h 4h 3h 2h Ah Kd Ks,   STRAIGHT_FLUSH,  Straight flush to 5",
            "8h 8c 8d 8s Kc Qd 2s,   FOUR_OF_A_KIND,  Four 8s",
            "9h 9c 9d 4c 4d 4s Ac,   FULL_HOUSE,      'Full house, 9s over 4s'",
            "Kh 3h 7h 8h Qh 2c 2d,   FLUSH,           'Flush, King high'",
            "Ac Kd Qh Js 10c 2d 3s,  STRAIGHT,        Straight to Ace",
            "Ah 3s 2c 5d 9h Js 4h,   STRAIGHT,        Straight to 5",
            "7c 7d 7h Ks 2d 4c 9s,   THREE_OF_A_KIND, Three 7s",
            "Jh 9c 2c 5d 9h Js 4h,   TWO_PAIR,        'Two pair, Jacks and 9s'",
            "Kh Ks 2c 5d 9h Js 4h,   PAIR,            Pair of Kings",
            "8h 4c 2s 3d 5h 7c Kd,   HIGH_CARD,       High card King",
    })
    void findsTheBestHandInSevenCards(String hand, HandCategory category, String description) {
        HandValue value = best(hand);
        assertEquals(category, value.category());
        assertEquals(description, value.describe());
        assertEquals(5, value.cards().size());
    }

    @Test
    void wheelShowsTheAceLast() {
        HandValue wheel = best("Ah 3s 2c 5d 9h Js 4h");
        assertEquals(cards("5d 4h 3s 2c Ah").stream().map(Card::rank).toList(),
                wheel.cards().stream().map(Card::rank).toList());
    }

    @Test
    void groupsMatchingCardsFirstForDisplay() {
        HandValue fullHouse = best("7h 7s 7d Qc Qh 2c 3d");
        assertEquals(List.of(Rank.SEVEN, Rank.SEVEN, Rank.SEVEN, Rank.QUEEN, Rank.QUEEN),
                fullHouse.cards().stream().map(Card::rank).toList());
    }

    @Test
    void sixHighStraightBeatsTheWheel() {
        String board = "2c 5d 9h Js 4h";
        assertTrue(best("6c 3s " + board).compareTo(best("Ah 3s " + board)) > 0);
    }

    @Test
    void royalFlushBeatsKingHighStraightFlush() {
        assertTrue(best("Ah Kh Qh Jh 10h 2c 3d").compareTo(best("9h Kh Qh Jh 10h 2c 3d")) > 0);
    }

    @Test
    void kickerBreaksTiesBetweenEqualPairs() {
        String board = "Kh Ks 2c 5d 9h";
        assertTrue(best("Ad 3c " + board).compareTo(best("Qd 3h " + board)) > 0);
    }

    @Test
    void fullHouseIsRankedByTheTripsBeforeThePair() {
        assertTrue(best("7h 7s 7d Qc Qh 2c 3d").compareTo(best("6h 6s 6d Ac Ah 2c 3d")) > 0);
    }

    @Test
    void identicalBestHandsTie() {
        assertEquals(0, best("Ah 7d 2s 3s 9c Jd 10h").compareTo(best("As 7c 2d 3d 9h Jc 10s")));
    }

    @Test
    void handsPlayingTheBoardTie() {
        String board = "Ah Kh Qh Jh 10h";
        assertEquals(0, best("2c 3d " + board).compareTo(best("4s 5s " + board)));
    }

    @Test
    void rejectsFewerThanFiveCards() {
        assertThrows(IllegalArgumentException.class, () -> HandEvaluator.best(cards("Ah Kh Qh Jh")));
    }
}
