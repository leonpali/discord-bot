package dev.leon.discordbot.game;

import java.util.List;

public record Showdown(List<Entry> entries, boolean win) {

    public record Entry(int number, Player player, List<Card> hand, HandValue value) {}

    public static Showdown of(List<Entry> byNumber) {
        boolean win = true;
        for (int i = 1; i < byNumber.size(); i++) {
            if (byNumber.get(i - 1).value().compareTo(byNumber.get(i).value()) > 0) {
                win = false;
                break;
            }
        }
        return new Showdown(List.copyOf(byNumber), win);
    }
}
