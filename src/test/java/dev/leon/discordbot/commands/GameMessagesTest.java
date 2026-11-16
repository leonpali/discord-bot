package dev.leon.discordbot.commands;

import static dev.leon.discordbot.game.TestCards.best;
import static dev.leon.discordbot.game.TestCards.cards;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.leon.discordbot.game.GameSession;
import dev.leon.discordbot.game.Player;
import dev.leon.discordbot.game.Round;
import dev.leon.discordbot.game.RoundRecord;
import dev.leon.discordbot.game.Showdown;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameMessagesTest {
    private final Player alice = new Player(1, "Alice");
    private final Player bob = new Player(2, "Bob");
    private final Player carol = new Player(3, "Carol");

    @Test
    void handListsTheCards() {
        assertEquals("Your cards: **A♠  10♥**", GameMessages.hand(cards("As 10h")));
    }

    @Test
    void overviewShowsNumbersReadinessAndWhoIsMissing() {
        GameSession session = threePlayerGame();
        session.dealHands();
        session.take(carol.userId(), 1);
        session.take(bob.userId(), 3);
        session.setReady(carol.userId(), true);

        assertEquals("""
                **Round 1 · Pre-flop**: react with a number to claim it, then react ✅ when everyone has one.
                1️⃣ **Carol** ✅
                2️⃣ -
                3️⃣ **Bob**
                No number yet: Alice
                -# Bob took 3
                """, GameMessages.overview(session.view()));
    }

    @Test
    void overviewShowsTheBoardAndEarlierRounds() {
        GameSession session = threePlayerGame();
        session.dealHands();
        session.take(alice.userId(), 2);
        session.take(bob.userId(), 1);
        session.take(carol.userId(), 3);
        for (Player player : List.of(alice, bob, carol)) {
            session.setReady(player.userId(), true);
        }
        session.advanceIfComplete();

        String overview = GameMessages.overview(session.view());

        assertTrue(overview.startsWith("**Round 2 · Flop**"), overview);
        assertTrue(overview.lines().anyMatch(line -> line.startsWith("Board: **") && line.split("  ").length == 3), overview);
        assertTrue(overview.endsWith("""

                **Earlier rounds**
                Pre-flop: `1` Bob · `2` Alice · `3` Carol
                """), overview);
    }

    @Test
    void lockedInShowsFinalNumbersWithoutInstructionsOrReadyMarks() {
        RoundRecord record = new RoundRecord(Round.TURN, cards("2c 3d 4h 5s"), List.of(bob, alice));
        assertEquals("""
                **Round 3 · Turn: locked in**
                Board: **2♣  3♦  4♥  5♠**
                1️⃣ **Bob**
                2️⃣ **Alice**
                """, GameMessages.lockedIn(record));
    }

    @Test
    void showdownAnnouncesAWin() {
        String board = "2s 4d 6h 9c 10d";
        Showdown showdown = new Showdown(cards(board), List.of(
                new Showdown.Entry(1, alice, cards("8h 3c"), best("8h 3c " + board)),
                new Showdown.Entry(2, bob, cards("8c 7d"), best("8c 7d " + board))));
        List<RoundRecord> history = List.of(new RoundRecord(Round.PRE_FLOP, List.of(), List.of(bob, alice)));

        assertEquals("""
                **Showdown**
                Board: **2♠  4♦  6♥  9♣  10♦**

                `1` **Alice**: 8♥  3♣ → **High card 10** (10♦  9♣  8♥  6♥  4♦)
                `2` **Bob**: 8♣  7♦ → **Straight to 10** (10♦  9♣  8♣  7♦  6♥)

                ## 🎉 Win! Your numbers match the hand ranking.

                **Rounds**
                Pre-flop: `1` Bob · `2` Alice
                -# The host can `!gang-end` to close the game.""", GameMessages.showdown(showdown, history));
    }

    @Test
    void showdownExplainsALossWithTies() {
        String board = "2s 4d 6h 9c Kd";
        Showdown showdown = new Showdown(cards(board), List.of(
                new Showdown.Entry(1, alice, cards("Ah 3c"), best("Ah 3c " + board)),
                new Showdown.Entry(2, bob, cards("As 3d"), best("As 3d " + board)),
                new Showdown.Entry(3, carol, cards("8h 7c"), best("8h 7c " + board))));

        String message = GameMessages.showdown(showdown, List.of());

        assertTrue(message.contains("## ❌ Loss.\nCorrect order, weakest to strongest: Carol < Alice = Bob\n"), message);
        assertFalse(message.contains("Win!"));
    }

    @Test
    void showdownOrdersALossWeakestFirst() {
        String board = "2s 4d 6h 9c 10d";
        Showdown showdown = new Showdown(cards(board), List.of(
                new Showdown.Entry(1, bob, cards("8c 7d"), best("8c 7d " + board)),
                new Showdown.Entry(2, alice, cards("8h 3c"), best("8h 3c " + board))));

        assertTrue(GameMessages.showdown(showdown, List.of())
                .contains("Correct order, weakest to strongest: Alice < Bob"));
    }

    private GameSession threePlayerGame() {
        GameSession session = new GameSession(alice);
        session.join(bob);
        session.join(carol);
        return session;
    }
}
