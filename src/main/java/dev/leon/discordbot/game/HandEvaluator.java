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

        List<Card> displayOrder = new ArrayList<>(five);
        displayOrder.sort(Comparator.<Card>comparingInt(card -> counts.get(card.rank())).reversed()
                .thenComparing(Card::rank, Comparator.reverseOrder()));

        boolean flush = five.stream().map(Card::suit).distinct().count() == 1;
        Rank straightHigh = straightHigh(groups);

        if (straightHigh != null) {
            if (straightHigh == Rank.FIVE) {
                displayOrder.add(displayOrder.removeFirst());
            }
            return new HandValue(straightCategory(straightHigh, flush), List.of(straightHigh), displayOrder);
        }
        return new HandValue(groupedCategory(counts.get(groups.getFirst()), groups.size(), flush), groups, displayOrder);
    }

    private static HandCategory straightCategory(Rank high, boolean flush) {
        if (!flush) {
            return HandCategory.STRAIGHT;
        }
        return high == Rank.ACE ? HandCategory.ROYAL_FLUSH : HandCategory.STRAIGHT_FLUSH;
    }

    private static HandCategory groupedCategory(int largestGroup, int distinctRanks, boolean flush) {
        if (largestGroup == 4) {
            return HandCategory.FOUR_OF_A_KIND;
        }
        if (largestGroup == 3 && distinctRanks == 2) {
            return HandCategory.FULL_HOUSE;
        }
        if (flush) {
            return HandCategory.FLUSH;
        }
        if (largestGroup == 3) {
            return HandCategory.THREE_OF_A_KIND;
        }
        if (largestGroup == 2) {
            return distinctRanks == 3 ? HandCategory.TWO_PAIR : HandCategory.PAIR;
        }
        return HandCategory.HIGH_CARD;
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
