package dev.leon.discordbot.game;

import java.util.List;

public record HandValue(HandCategory category, List<Rank> tiebreak, List<Card> cards) implements Comparable<HandValue> {

    @Override
    public int compareTo(HandValue other) {
        int byCategory = category.compareTo(other.category);
        if (byCategory != 0) {
            return byCategory;
        }
        for (int i = 0; i < tiebreak.size(); i++) {
            int byRank = tiebreak.get(i).compareTo(other.tiebreak.get(i));
            if (byRank != 0) {
                return byRank;
            }
        }
        return 0;
    }

    public String describe() {
        Rank first = tiebreak.getFirst();
        return switch (category) {
            case HIGH_CARD -> "High card " + first.word();
            case PAIR -> "Pair of " + first.plural();
            case TWO_PAIR -> "Two pair, %s and %s".formatted(first.plural(), tiebreak.get(1).plural());
            case THREE_OF_A_KIND -> "Three " + first.plural();
            case STRAIGHT -> "Straight to " + first.word();
            case FLUSH -> "Flush, %s high".formatted(first.word());
            case FULL_HOUSE -> "Full house, %s over %s".formatted(first.plural(), tiebreak.get(1).plural());
            case FOUR_OF_A_KIND -> "Four " + first.plural();
            case STRAIGHT_FLUSH -> "Straight flush to " + first.word();
            case ROYAL_FLUSH -> "Royal flush";
        };
    }
}
