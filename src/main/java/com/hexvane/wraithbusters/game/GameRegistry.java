package com.hexvane.wraithbusters.game;

import com.hypixel.hytale.server.core.universe.Universe;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GameRegistry {
    private static final GameRegistry INSTANCE = new GameRegistry();

    private final ConcurrentHashMap<UUID, GameSession> bySessionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> playerToSession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> worldToSession = new ConcurrentHashMap<>();

    private GameRegistry() {}

    @Nonnull
    public static GameRegistry get() {
        return INSTANCE;
    }

    public void register(@Nonnull GameSession session) {
        bySessionId.put(session.getSessionId(), session);
        if (session.getWorldUuid() != null) {
            worldToSession.put(session.getWorldUuid(), session.getSessionId());
        }
    }

    public void unregister(@Nonnull GameSession session) {
        bySessionId.remove(session.getSessionId());
        if (session.getWorldUuid() != null) {
            worldToSession.remove(session.getWorldUuid());
        }
        for (UUID playerUuid : session.playerUuidList()) {
            playerToSession.remove(playerUuid);
        }
    }

    public void linkPlayer(@Nonnull UUID playerUuid, @Nonnull GameSession session) {
        playerToSession.put(playerUuid, session.getSessionId());
    }

    public void unlinkPlayer(@Nonnull UUID playerUuid) {
        playerToSession.remove(playerUuid);
    }

    public void linkWorld(@Nonnull UUID worldUuid, @Nonnull GameSession session) {
        session.setWorldUuid(worldUuid);
        worldToSession.put(worldUuid, session.getSessionId());
    }

    @Nullable
    public GameSession getSession(@Nonnull UUID sessionId) {
        return bySessionId.get(sessionId);
    }

    @Nullable
    public GameSession getSessionForPlayer(@Nonnull UUID playerUuid) {
        UUID sessionId = playerToSession.get(playerUuid);
        return sessionId == null ? null : bySessionId.get(sessionId);
    }

    @Nullable
    public GameSession getSessionForWorld(@Nonnull UUID worldUuid) {
        UUID sessionId = worldToSession.get(worldUuid);
        return sessionId == null ? null : bySessionId.get(sessionId);
    }

    @Nonnull
    public Collection<GameSession> allSessions() {
        return bySessionId.values();
    }

    @Nullable
    public GameSession findOpenLobby() {
        for (GameSession session : bySessionId.values()) {
            if (session.getPhase() != GamePhase.LOBBY && session.getPhase() != GamePhase.COUNTDOWN) {
                continue;
            }
            if (GameService.isInstanceJoinable(Universe.get().getWorld(session.getWorldUuid()))) {
                return session;
            }
        }
        return null;
    }
}
