package dev.leon.discordbot.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public record RoundView(
        Round round,
        List<Card> board,
        List<Player> byNumber,
        List<Player> unassigned,
        Set<Long> ready,
        String lastAction,
        List<RoundRecord> history,
        long overviewMessageId) {

    public RoundView {
        board = List.copyOf(board);
        byNumber = Collections.unmodifiableList(new ArrayList<>(byNumber));
        unassigned = List.copyOf(unassigned);
        ready = Set.copyOf(ready);
        history = List.copyOf(history);
    }

    public boolean isReady(Player player) {
        return ready.contains(player.userId());
    }
}
