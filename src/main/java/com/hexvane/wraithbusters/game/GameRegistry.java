package com.hexvane.wraithbusters.game;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
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
        if (session.getWorldUuid() != null) {
            worldToSession.remove(session.getWorldUuid());
        }
        session.setWorldUuid(worldUuid);
        worldToSession.put(worldUuid, session.getSessionId());
    }

    public void unlinkWorld(@Nonnull UUID worldUuid) {
        worldToSession.remove(worldUuid);
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

    @Nullable
    public GameSession resolveSessionForWorld(@Nonnull World world) {
        UUID worldUuid = world.getWorldConfig().getUuid();
        GameSession session = getSessionForWorld(worldUuid);
        if (session != null) {
            return session;
        }
        for (GameSession candidate : allSessions()) {
            if (worldUuid.equals(candidate.getWorldUuid())) {
                return candidate;
            }
        }
        for (PlayerRef player : world.getPlayerRefs()) {
            GameSession byPlayer = getSessionForPlayer(player.getUuid());
            if (byPlayer != null) {
                return byPlayer;
            }
        }
        return null;
    }

    @Nonnull
    public Collection<GameSession> allSessions() {
        return bySessionId.values();
    }

    @Nullable
    public GameSession findOpenLobby() {
        return findJoinableLobby(null);
    }

    /** Returns a random joinable lobby, optionally excluding one session (e.g. the round just ended). */
    @Nullable
    public GameSession findJoinableLobby(@Nullable UUID excludeSessionId) {
        List<GameSession> candidates = new ArrayList<>();
        for (GameSession session : bySessionId.values()) {
            if (excludeSessionId != null && excludeSessionId.equals(session.getSessionId())) {
                continue;
            }
            GamePhase phase = session.getPhase();
            if (phase != GamePhase.LOBBY && phase != GamePhase.COUNTDOWN) {
                continue;
            }
            if (GameService.isInstanceJoinable(Universe.get().getWorld(session.getWorldUuid()))) {
                candidates.add(session);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
