package dev.leon.discordbot.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoundTest {

    @Test
    void roundsRunPreFlopFlopTurnRiver() {
        assertEquals(Round.FLOP, Round.PRE_FLOP.next());
        assertEquals(Round.TURN, Round.FLOP.next());
        assertEquals(Round.RIVER, Round.TURN.next());
        assertTrue(Round.RIVER.isLast());
        assertFalse(Round.TURN.isLast());
        assertThrows(IllegalStateException.class, Round.RIVER::next);
    }

    @Test
    void revealsFlopThenTurnThenRiver() {
        assertEquals(0, Round.PRE_FLOP.cardsRevealed());
        assertEquals(3, Round.FLOP.cardsRevealed());
        assertEquals(1, Round.TURN.cardsRevealed());
        assertEquals(1, Round.RIVER.cardsRevealed());
    }

    @Test
    void numbersStartAtOne() {
        assertEquals(1, Round.PRE_FLOP.number());
        assertEquals(4, Round.RIVER.number());
    }
}
