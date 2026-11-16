package dev.leon.discordbot.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.leon.discordbot.game.GameSession.Advance;
import dev.leon.discordbot.game.GameSession.JoinResult;
import dev.leon.discordbot.game.GameSession.TakeResult;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GameSessionTest {
    private final Player alice = new Player(1, "Alice");
    private final Player bob = new Player(2, "Bob");
    private final Player carol = new Player(3, "Carol");

    @Nested
    class Lobby {
        private final GameSession session = new GameSession(alice);

        @Test
        void hostIsThePlayerWhoCreatedTheGame() {
            assertTrue(session.isHost(alice.userId()));
            assertFalse(session.isHost(bob.userId()));
            assertTrue(session.hasPlayer(alice.userId()));
            assertEquals(1, session.playerCount());
        }

        @Test
        void playersCanJoinOnce() {
            assertEquals(JoinResult.JOINED, session.join(bob));
            assertEquals(JoinResult.ALREADY_JOINED, session.join(bob));
            assertEquals(JoinResult.ALREADY_JOINED, session.join(alice));
            assertEquals(2, session.playerCount());
        }

        @Test
        void tableIsFullAtNinePlayers() {
            for (int id = 2; id <= GameSession.MAX_PLAYERS; id++) {
                assertEquals(JoinResult.JOINED, session.join(new Player(id, "p" + id)));
            }
            assertEquals(JoinResult.FULL, session.join(new Player(99, "late")));
            assertEquals(GameSession.MAX_PLAYERS, session.playerCount());
        }

        @Test
        void nobodyCanJoinAfterTheDeal() {
            session.dealHands();
            assertEquals(JoinResult.ALREADY_STARTED, session.join(bob));
        }

        @Test
        void isNotStartedUntilDealt() {
            assertFalse(session.isStarted());
            session.dealHands();
            assertTrue(session.isStarted());
        }
    }

    @Nested
    class Dealing {
        private GameSession session;
        private Map<Player, List<Card>> hands;

        @BeforeEach
        void deal() {
            session = threePlayerGame();
            hands = session.dealHands();
        }

        @Test
        void everyPlayerGetsTwoUniqueCards() {
            assertEquals(List.of(alice, bob, carol), List.copyOf(hands.keySet()));
            Set<Card> dealt = new HashSet<>();
            hands.values().forEach(hand -> {
                assertEquals(GameSession.HOLE_CARDS, hand.size());
                dealt.addAll(hand);
            });
            assertEquals(6, dealt.size());
        }

        @Test
        void startsOnThePreFlopWithAnEmptyBoard() {
            RoundView view = session.view();
            assertEquals(Round.PRE_FLOP, view.round());
            assertTrue(view.board().isEmpty());
            assertEquals(List.of(alice, bob, carol), view.unassigned());
            assertEquals(0, view.overviewMessageId());
        }

        @Test
        void boardGrowsThreeThenOneThenOne() {
            assertEquals(3, completeRound(session).board().size(), "flop");
            assertEquals(4, completeRound(session).board().size(), "turn");
            assertEquals(5, completeRound(session).board().size(), "river");
        }

        @Test
        void boardNeverRepeatsAHoleCard() {
            for (int i = 0; i < 3; i++) {
                completeRound(session);
            }
            Set<Card> all = new HashSet<>(session.view().board());
            hands.values().forEach(all::addAll);
            assertEquals(5 + 6, all.size());
        }
    }

    @Nested
    class TakingNumbers {
        private GameSession session;

        @BeforeEach
        void deal() {
            session = threePlayerGame();
            session.dealHands();
        }

        @Test
        void takingAFreeNumber() {
            TakeResult result = session.take(alice.userId(), 2);
            assertEquals(new TakeResult.Taken(null, null), result);
            assertEquals(alice, session.view().byNumber().get(1));
            assertEquals("Alice took 2", session.view().lastAction());
        }

        @Test
        void stealingLeavesThePreviousHolderWithoutANumber() {
            session.take(alice.userId(), 1);
            TakeResult result = session.take(bob.userId(), 1);

            assertEquals(new TakeResult.Taken(null, alice), result);
            RoundView view = session.view();
            assertEquals(bob, view.byNumber().getFirst());
            assertTrue(view.unassigned().contains(alice));
            assertEquals("Bob took 1 from Alice", view.lastAction());
        }

        @Test
        void switchingFreesYourOldNumber() {
            session.take(alice.userId(), 1);
            TakeResult result = session.take(alice.userId(), 3);

            assertEquals(new TakeResult.Taken(1, null), result);
            assertNull(session.view().byNumber().getFirst());
            assertEquals(alice, session.view().byNumber().get(2));
        }

        @Test
        void takingYourOwnNumberChangesNothing() {
            session.take(alice.userId(), 1);
            assertInstanceOf(TakeResult.AlreadyYours.class, session.take(alice.userId(), 1));
        }

        @Test
        void numbersAreLimitedToThePlayerCount() {
            assertInstanceOf(TakeResult.OutOfRange.class, session.take(alice.userId(), 0));
            assertInstanceOf(TakeResult.OutOfRange.class, session.take(alice.userId(), 4));
        }

        @Test
        void releasingOnlyWorksForYourCurrentNumber() {
            session.take(alice.userId(), 1);
            session.take(bob.userId(), 1);

            assertFalse(session.release(alice.userId(), 1), "Alice no longer holds 1");
            assertFalse(session.release(bob.userId(), 2), "Bob doesn't hold 2");
            assertTrue(session.release(bob.userId(), 1));
            assertNull(session.view().byNumber().getFirst());
            assertEquals("Bob gave up 1", session.view().lastAction());
        }

        @Test
        void viewIsASnapshot() {
            RoundView before = session.view();
            session.take(alice.userId(), 1);
            session.setReady(alice.userId(), true);

            assertNull(before.byNumber().getFirst());
            assertFalse(before.isReady(alice));
            assertThrows(UnsupportedOperationException.class, () -> before.byNumber().set(0, alice));
        }
    }

    @Nested
    class Advancing {
        private GameSession session;

        @BeforeEach
        void deal() {
            session = threePlayerGame();
            session.dealHands();
        }

        @Test
        void waitsUntilEveryoneHasANumberAndIsReady() {
            takeInOrder(session);
            readyAll(session);
            session.setReady(carol.userId(), false);
            assertTrue(session.advanceIfComplete().isEmpty(), "Carol isn't ready");

            session.setReady(carol.userId(), true);
            session.release(carol.userId(), 3);
            assertTrue(session.advanceIfComplete().isEmpty(), "Carol has no number");
        }

        @Test
        void earlyReactionsCountOnceTheLastNumberIsTaken() {
            readyAll(session);
            session.take(alice.userId(), 1);
            session.take(bob.userId(), 2);
            assertTrue(session.advanceIfComplete().isEmpty());

            session.take(carol.userId(), 3);
            assertTrue(session.advanceIfComplete().isPresent());
        }

        @Test
        void completingARoundRecordsItAndStartsTheNext() {
            session.attachOverview(Round.PRE_FLOP, 500);
            session.take(alice.userId(), 3);
            session.take(bob.userId(), 1);
            session.take(carol.userId(), 2);
            readyAll(session);

            Advance advance = session.advanceIfComplete().orElseThrow();

            assertEquals(500, advance.completedOverviewId());
            assertFalse(advance.finished());
            assertEquals(Round.PRE_FLOP, advance.completed().round());
            assertEquals(List.of(bob, carol, alice), advance.completed().byNumber());

            RoundView next = session.view();
            assertEquals(Round.FLOP, next.round());
            assertEquals(List.of(advance.completed()), next.history());
            assertEquals(List.of(alice, bob, carol), next.unassigned(), "numbers reset");
            assertFalse(next.isReady(alice), "ready reset");
            assertNull(next.lastAction());
        }

        @Test
        void advancesOnlyOncePerRound() {
            takeInOrder(session);
            readyAll(session);
            assertTrue(session.advanceIfComplete().isPresent());
            assertTrue(session.advanceIfComplete().isEmpty());
        }

        @Test
        void finishesAfterTheRiver() {
            for (int i = 0; i < 3; i++) {
                completeRound(session);
            }
            takeInOrder(session);
            readyAll(session);

            Advance last = session.advanceIfComplete().orElseThrow();

            assertTrue(last.finished());
            assertEquals(Round.RIVER, last.completed().round());
            assertEquals(4, session.history().size());
            assertTrue(session.advanceIfComplete().isEmpty());
        }

        @Test
        void showdownUsesTheRiverNumbersAndFullBoard() {
            for (int i = 0; i < 3; i++) {
                completeRound(session);
            }
            session.take(alice.userId(), 2);
            session.take(bob.userId(), 3);
            session.take(carol.userId(), 1);
            readyAll(session);
            session.advanceIfComplete();

            Showdown showdown = session.showdown();
            assertEquals(5, showdown.board().size());
            assertEquals(List.of(carol, alice, bob),
                    showdown.entries().stream().map(Showdown.Entry::player).toList());
            assertEquals(List.of(1, 2, 3),
                    showdown.entries().stream().map(Showdown.Entry::number).toList());
        }

        @Test
        void showdownIsUnavailableBeforeTheEnd() {
            assertThrows(IllegalStateException.class, session::showdown);
        }
    }

    @Nested
    class Overview {
        private GameSession session;

        @BeforeEach
        void deal() {
            session = threePlayerGame();
            session.dealHands();
        }

        @Test
        void tracksTheCurrentRoundsMessage() {
            assertFalse(session.isOverview(0));
            assertTrue(session.attachOverview(Round.PRE_FLOP, 500));
            assertTrue(session.isOverview(500));
            assertFalse(session.isOverview(501));
        }

        @Test
        void ignoresAMessageSentForAnEarlierRound() {
            completeRound(session);
            assertFalse(session.attachOverview(Round.PRE_FLOP, 500));
            assertFalse(session.isOverview(500));
        }

        @Test
        void completedRoundsOverviewStopsCounting() {
            session.attachOverview(Round.PRE_FLOP, 500);
            completeRound(session);
            assertFalse(session.isOverview(500));
        }

        @Test
        void noOverviewIsActiveAfterTheGameEnds() {
            for (int i = 0; i < 3; i++) {
                completeRound(session);
            }
            session.attachOverview(Round.RIVER, 900);
            completeRound(session);
            assertFalse(session.isOverview(900));
            assertFalse(session.attachOverview(Round.RIVER, 901));
        }
    }

    private GameSession threePlayerGame() {
        GameSession session = new GameSession(alice);
        session.join(bob);
        session.join(carol);
        return session;
    }

    private void takeInOrder(GameSession session) {
        session.take(alice.userId(), 1);
        session.take(bob.userId(), 2);
        session.take(carol.userId(), 3);
    }

    private void readyAll(GameSession session) {
        for (Player player : List.of(alice, bob, carol)) {
            session.setReady(player.userId(), true);
        }
    }

    private RoundView completeRound(GameSession session) {
        takeInOrder(session);
        readyAll(session);
        Advance advance = session.advanceIfComplete().orElseThrow();
        assertSame(Round.values()[session.history().size() - 1], advance.completed().round());
        return session.view();
    }
}
