package com.hexvane.wraithbusters.game;

import com.hexvane.wraithbusters.arena.ArenaLayout;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.team.Team;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GameSession {
    @Nonnull
    private final UUID sessionId;
    @Nonnull
    private final UUID hostUuid;
    @Nonnull
    private final String arenaId;
    @Nonnull
    private ArenaLayout arenaLayout;
    @Nullable
    private UUID worldUuid;
    @Nullable
    private String worldName;
    @Nonnull
    private GamePhase phase = GamePhase.LOBBY;
    private final ConcurrentHashMap<UUID, PlayerSessionState> players = new ConcurrentHashMap<>();
    private int countdownTicksRemaining;
    private int lastCountdownSecondAnnounced = -1;
    private long roundEndEpochMs;
    @Nullable
    private Team winningTeam;
    @Nonnull
    private List<String> activeRoomChain = new ArrayList<>();
    private int currentRoomIndex;
    @Nullable
    private String startingRoomId;

    private long postRoundEndEpochMs;
    @Nonnull
    private final Set<String> disabledPhaseDoorIds = ConcurrentHashMap.newKeySet();
    private int lastPostRoundSecondAnnounced = -1;

    private boolean awaitingFirstJoin;
    private boolean hadPlayers;
    @Nonnull
    private final Set<UUID> pendingLobbyArrivals = ConcurrentHashMap.newKeySet();

    public GameSession(
        @Nonnull UUID sessionId,
        @Nonnull UUID hostUuid,
        @Nonnull String arenaId,
        @Nonnull ArenaLayout arenaLayout
    ) {
        this.sessionId = sessionId;
        this.hostUuid = hostUuid;
        this.arenaId = arenaId;
        this.arenaLayout = arenaLayout;
    }

    @Nonnull
    public UUID getSessionId() {
        return sessionId;
    }

    @Nonnull
    public UUID getHostUuid() {
        return hostUuid;
    }

    @Nonnull
    public String getArenaId() {
        return arenaId;
    }

    @Nonnull
    public ArenaLayout getArenaLayout() {
        return arenaLayout;
    }

    public void setArenaLayout(@Nonnull ArenaLayout arenaLayout) {
        this.arenaLayout = arenaLayout;
    }

    @Nullable
    public UUID getWorldUuid() {
        return worldUuid;
    }

    public void setWorldUuid(@Nullable UUID worldUuid) {
        this.worldUuid = worldUuid;
    }

    @Nullable
    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(@Nullable String worldName) {
        this.worldName = worldName;
    }

    @Nonnull
    public GamePhase getPhase() {
        return phase;
    }

    public void setPhase(@Nonnull GamePhase phase) {
        this.phase = phase;
    }

    @Nonnull
    public ConcurrentHashMap<UUID, PlayerSessionState> getPlayers() {
        return players;
    }

    @Nonnull
    public PlayerSessionState getOrCreatePlayer(@Nonnull UUID uuid) {
        return players.computeIfAbsent(uuid, PlayerSessionState::new);
    }

    public int getCountdownTicksRemaining() {
        return countdownTicksRemaining;
    }

    public void setCountdownTicksRemaining(int countdownTicksRemaining) {
        this.countdownTicksRemaining = countdownTicksRemaining;
    }

    public int getLastCountdownSecondAnnounced() {
        return lastCountdownSecondAnnounced;
    }

    public void setLastCountdownSecondAnnounced(int lastCountdownSecondAnnounced) {
        this.lastCountdownSecondAnnounced = lastCountdownSecondAnnounced;
    }

    public long getRoundEndEpochMs() {
        return roundEndEpochMs;
    }

    public void setRoundEndEpochMs(long roundEndEpochMs) {
        this.roundEndEpochMs = roundEndEpochMs;
    }

    @Nullable
    public Team getWinningTeam() {
        return winningTeam;
    }

    public void setWinningTeam(@Nullable Team winningTeam) {
        this.winningTeam = winningTeam;
    }

    @Nonnull
    public List<String> getActiveRoomChain() {
        return activeRoomChain;
    }

    public void setActiveRoomChain(@Nonnull List<String> activeRoomChain) {
        this.activeRoomChain = activeRoomChain;
    }

    public int getCurrentRoomIndex() {
        return currentRoomIndex;
    }

    public void setCurrentRoomIndex(int currentRoomIndex) {
        this.currentRoomIndex = currentRoomIndex;
    }

    @Nullable
    public String getStartingRoomId() {
        return startingRoomId;
    }

    public void setStartingRoomId(@Nullable String startingRoomId) {
        this.startingRoomId = startingRoomId;
    }

    public long getPostRoundEndEpochMs() {
        return postRoundEndEpochMs;
    }

    public void setPostRoundEndEpochMs(long postRoundEndEpochMs) {
        this.postRoundEndEpochMs = postRoundEndEpochMs;
    }

    public void disablePhaseDoor(@Nonnull String doorId) {
        disabledPhaseDoorIds.add(doorId);
    }

    public boolean isPhaseDoorDisabled(@Nonnull String doorId) {
        return disabledPhaseDoorIds.contains(doorId);
    }

    public void clearDisabledPhaseDoors() {
        disabledPhaseDoorIds.clear();
    }

    public int getLastPostRoundSecondAnnounced() {
        return lastPostRoundSecondAnnounced;
    }

    public void setLastPostRoundSecondAnnounced(int lastPostRoundSecondAnnounced) {
        this.lastPostRoundSecondAnnounced = lastPostRoundSecondAnnounced;
    }

    public boolean isAwaitingFirstJoin() {
        return awaitingFirstJoin;
    }

    public void setAwaitingFirstJoin(boolean awaitingFirstJoin) {
        this.awaitingFirstJoin = awaitingFirstJoin;
    }

    public boolean hadPlayers() {
        return hadPlayers;
    }

    public void markHadPlayers() {
        this.hadPlayers = true;
    }

    public void markPendingLobbyArrival(@Nonnull UUID playerUuid) {
        pendingLobbyArrivals.add(playerUuid);
    }

    public boolean isPendingLobbyArrival(@Nonnull UUID playerUuid) {
        return pendingLobbyArrivals.contains(playerUuid);
    }

    public void clearPendingLobbyArrival(@Nonnull UUID playerUuid) {
        pendingLobbyArrivals.remove(playerUuid);
    }

    public boolean hasPendingLobbyArrivals() {
        return !pendingLobbyArrivals.isEmpty();
    }

    public int readyCount() {
        int count = 0;
        for (PlayerSessionState state : players.values()) {
            if (state.isReady()) {
                count++;
            }
        }
        return count;
    }

    public boolean allReady() {
        if (players.isEmpty()) {
            return false;
        }
        for (PlayerSessionState state : players.values()) {
            if (!state.isReady()) {
                return false;
            }
        }
        return true;
    }

    public int livingHumanCount() {
        int count = 0;
        for (PlayerSessionState state : players.values()) {
            if (state.getTeam() == Team.HUMAN && state.isAlive()) {
                count++;
            }
        }
        return count;
    }

    public int connectedGhostCount() {
        int count = 0;
        for (PlayerSessionState state : players.values()) {
            if (state.getTeam() == Team.GHOST) {
                count++;
            }
        }
        return count;
    }

    public void resetForLobby() {
        countdownTicksRemaining = 0;
        lastCountdownSecondAnnounced = -1;
        roundEndEpochMs = 0L;
        winningTeam = null;
        currentRoomIndex = 0;
        startingRoomId = null;
        activeRoomChain = new ArrayList<>();
        postRoundEndEpochMs = 0L;
        disabledPhaseDoorIds.clear();
        lastPostRoundSecondAnnounced = -1;
        for (PlayerSessionState state : players.values()) {
            state.resetForLobby();
        }
    }

    @Nonnull
    public List<UUID> playerUuidList() {
        return new ArrayList<>(players.keySet());
    }
}
