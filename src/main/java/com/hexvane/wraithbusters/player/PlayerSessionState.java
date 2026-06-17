package com.hexvane.wraithbusters.player;

import com.hexvane.wraithbusters.team.Team;
import com.hypixel.hytale.math.vector.Transform;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlayerSessionState {
    @Nonnull
    private final UUID playerUuid;
    private PlayerRole role = PlayerRole.LOBBY;
    private Team team = Team.NONE;
    private boolean ready;
    private int ghostMana;
    private boolean hasAtticKey;
    private boolean alive = true;
    @Nullable
    private Transform savedReturnTransform;

    public PlayerSessionState(@Nonnull UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    @Nonnull
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @Nonnull
    public PlayerRole getRole() {
        return role;
    }

    public void setRole(@Nonnull PlayerRole role) {
        this.role = role;
    }

    @Nonnull
    public Team getTeam() {
        return team;
    }

    public void setTeam(@Nonnull Team team) {
        this.team = team;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public int getGhostMana() {
        return ghostMana;
    }

    public void setGhostMana(int ghostMana) {
        this.ghostMana = ghostMana;
    }

    public boolean hasAtticKey() {
        return hasAtticKey;
    }

    public void setHasAtticKey(boolean hasAtticKey) {
        this.hasAtticKey = hasAtticKey;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    @Nullable
    public Transform getSavedReturnTransform() {
        return savedReturnTransform;
    }

    public void setSavedReturnTransform(@Nullable Transform savedReturnTransform) {
        this.savedReturnTransform = savedReturnTransform;
    }

    public void resetForLobby() {
        role = PlayerRole.LOBBY;
        team = Team.NONE;
        ready = false;
        ghostMana = 0;
        hasAtticKey = false;
        alive = true;
    }
}
