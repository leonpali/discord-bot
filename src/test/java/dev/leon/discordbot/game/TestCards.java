package dev.leon.discordbot.game;

import java.util.Arrays;
import java.util.List;

public final class TestCards {

    private TestCards() {}

    public static List<Card> cards(String notation) {
        return Arrays.stream(notation.trim().split("\\s+")).map(TestCards::card).toList();
    }

    public static Card card(String notation) {
        String rankLabel = notation.substring(0, notation.length() - 1);
        Rank rank = Arrays.stream(Rank.values())
                .filter(r -> r.label().equals(rankLabel))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown rank in " + notation));
        Suit suit = switch (notation.charAt(notation.length() - 1)) {
            case 'c' -> Suit.CLUBS;
            case 'd' -> Suit.DIAMONDS;
            case 'h' -> Suit.HEARTS;
            case 's' -> Suit.SPADES;
            default -> throw new IllegalArgumentException("Unknown suit in " + notation);
        };
        return new Card(rank, suit);
    }

    public static HandValue best(String notation) {
        return HandEvaluator.best(cards(notation));
    }
}
