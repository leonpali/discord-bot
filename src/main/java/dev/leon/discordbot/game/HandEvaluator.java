package dev.leon.discordbot.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class HandEvaluator {

    private HandEvaluator() {}

    public static HandValue best(List<Card> cards) {
        if (cards.size() < 5) {
            throw new IllegalArgumentException("Need at least 5 cards, got " + cards.size());
        }
        HandValue best = null;
        int n = cards.size();
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                for (int c = b + 1; c < n; c++) {
                    for (int d = c + 1; d < n; d++) {
                        for (int e = d + 1; e < n; e++) {
                            HandValue value = evaluate(List.of(
                                    cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e)));
                            if (best == null || value.compareTo(best) > 0) {
                                best = value;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    static HandValue evaluate(List<Card> five) {
        Map<Rank, Integer> counts = new EnumMap<>(Rank.class);
        for (Card card : five) {
            counts.merge(card.rank(), 1, Integer::sum);
        }

        List<Rank> groups = new ArrayList<>(counts.keySet());
        groups.sort(Comparator.<Rank>comparingInt(counts::get).reversed().thenComparing(Comparator.reverseOrder()));

        List<Card> sorted = new ArrayList<>(five);
        sorted.sort(Comparator.<Card>comparingInt(card -> counts.get(card.rank())).reversed()
                .thenComparing(Card::rank, Comparator.reverseOrder()));

        boolean flush = five.stream().map(Card::suit).distinct().count() == 1;
        Rank straightHigh = straightHigh(groups);

        if (straightHigh != null) {
            if (straightHigh == Rank.FIVE) {
                sorted.add(sorted.removeFirst());
            }
            HandCategory category;
            if (!flush) {
                category = HandCategory.STRAIGHT;
            } else if (straightHigh == Rank.ACE) {
                category = HandCategory.ROYAL_FLUSH;
            } else {
                category = HandCategory.STRAIGHT_FLUSH;
            }
            return new HandValue(category, List.of(straightHigh), List.copyOf(sorted));
        }

        int top = counts.get(groups.getFirst());
        HandCategory category;
        if (top == 4) {
            category = HandCategory.FOUR_OF_A_KIND;
        } else if (top == 3 && groups.size() == 2) {
            category = HandCategory.FULL_HOUSE;
        } else if (flush) {
            category = HandCategory.FLUSH;
        } else if (top == 3) {
            category = HandCategory.THREE_OF_A_KIND;
        } else if (top == 2 && groups.size() == 3) {
            category = HandCategory.TWO_PAIR;
        } else if (top == 2) {
            category = HandCategory.PAIR;
        } else {
            category = HandCategory.HIGH_CARD;
        }
        return new HandValue(category, List.copyOf(groups), List.copyOf(sorted));
    }

    private static Rank straightHigh(List<Rank> distinctDescending) {
        if (distinctDescending.size() != 5) {
            return null;
        }
        Rank high = distinctDescending.getFirst();
        Rank low = distinctDescending.getLast();
        if (high.ordinal() - low.ordinal() == 4) {
            return high;
        }
        if (high == Rank.ACE && distinctDescending.get(1) == Rank.FIVE) {
            return Rank.FIVE;
        }
        return null;
    }
}
