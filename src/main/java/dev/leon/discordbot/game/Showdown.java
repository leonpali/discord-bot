package dev.leon.discordbot.game;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public record Showdown(List<Card> board, List<Entry> entries) {

    public record Entry(int number, Player player, List<Card> hand, HandValue value) {}

    public Showdown {
        board = List.copyOf(board);
        entries = List.copyOf(entries);
    }

    public boolean win() {
        return IntStream.range(1, entries.size())
                .allMatch(i -> entries.get(i - 1).value().compareTo(entries.get(i).value()) <= 0);
    }

    public List<Entry> weakestToStrongest() {
        return entries.stream().sorted(Comparator.comparing(Entry::value)).toList();
    }
}
