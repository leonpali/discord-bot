package dev.leon.discordbot.game;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class GameManagerTest {
    private final GameManager games = new GameManager();
    private final Player alice = new Player(1, "Alice");

    @Test
    void createsOneGamePerChannel() {
        GameSession session = games.create(100, alice);
        assertNotNull(session);
        assertSame(session, games.get(100));
        assertNull(games.create(100, new Player(2, "Bob")));
    }

    @Test
    void channelsAreIndependent() {
        assertNotNull(games.create(100, alice));
        assertNotNull(games.create(200, alice));
    }

    @Test
    void endingFreesTheChannel() {
        games.create(100, alice);
        games.end(100);
        assertNull(games.get(100));
        assertNotNull(games.create(100, alice));
    }
}
