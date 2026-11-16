package dev.leon.discordbot.game;

import static dev.leon.discordbot.game.TestCards.best;
import static dev.leon.discordbot.game.TestCards.cards;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShowdownTest {
    private static final String BOARD = "2s 4d 6h 9c 10d";

    private static final Player EIGHT_HIGH = new Player(1, "eight");
    private static final Player ACE_HIGH = new Player(2, "ace");
    private static final Player STRAIGHT = new Player(3, "straight");

    private static Showdown.Entry entry(int number, Player player, String hole) {
        return new Showdown.Entry(number, player, cards(hole), best(hole + " " + BOARD));
    }

    @Test
    void winsWhenNumbersGoFromWeakestToStrongest() {
        Showdown showdown = new Showdown(cards(BOARD), List.of(
                entry(1, EIGHT_HIGH, "8h 3c"),
                entry(2, ACE_HIGH, "Ah 3d"),
                entry(3, STRAIGHT, "8c 7d")));
        assertTrue(showdown.win());
    }

    @Test
    void losesWhenAnyPairIsOutOfOrder() {
        Showdown showdown = new Showdown(cards(BOARD), List.of(
                entry(1, ACE_HIGH, "Ah 3d"),
                entry(2, EIGHT_HIGH, "8h 3c"),
                entry(3, STRAIGHT, "8c 7d")));
        assertFalse(showdown.win());
    }

    @Test
    void tiedHandsAcceptEitherOrder() {
        Player other = new Player(4, "other ace");
        Showdown showdown = new Showdown(cards(BOARD), List.of(
                entry(1, ACE_HIGH, "Ah 3d"),
                entry(2, other, "As 3h")));
        assertTrue(showdown.win());
    }

    @Test
    void everyonePlayingTheBoardIsAWin() {
        String board = "Ah Kh Qh Jh 10h";
        Showdown showdown = new Showdown(cards(board), List.of(
                new Showdown.Entry(1, ACE_HIGH, cards("Ac Kc"), best("Ac Kc " + board)),
                new Showdown.Entry(2, EIGHT_HIGH, cards("2c 3c"), best("2c 3c " + board))));
        assertTrue(showdown.win());
    }

    @Test
    void singlePlayerAlwaysWins() {
        assertTrue(new Showdown(cards(BOARD), List.of(entry(1, ACE_HIGH, "Ah 3d"))).win());
    }

    @Test
    void ranksEntriesWeakestToStrongest() {
        Showdown showdown = new Showdown(cards(BOARD), List.of(
                entry(1, STRAIGHT, "8c 7d"),
                entry(2, ACE_HIGH, "Ah 3d"),
                entry(3, EIGHT_HIGH, "8h 3c")));
        assertEquals(List.of(EIGHT_HIGH, ACE_HIGH, STRAIGHT),
                showdown.weakestToStrongest().stream().map(Showdown.Entry::player).toList());
    }
}
