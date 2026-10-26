package dev.leon.discordbot.game;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GameManager {
    private final Map<Long, GameSession> sessions = new ConcurrentHashMap<>();

    public GameSession create(long channelId, Player host) {
        GameSession session = new GameSession(host);
        return sessions.putIfAbsent(channelId, session) == null ? session : null;
    }

    public GameSession get(long channelId) {
        return sessions.get(channelId);
    }

    public GameSession end(long channelId) {
        return sessions.remove(channelId);
    }
}
