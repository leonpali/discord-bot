package dev.leon.discordbot.game;

import java.util.List;

public record RoundRecord(Round round, List<Card> board, List<Player> byNumber) {

    public RoundRecord {
        board = List.copyOf(board);
        byNumber = List.copyOf(byNumber);
    }
}
