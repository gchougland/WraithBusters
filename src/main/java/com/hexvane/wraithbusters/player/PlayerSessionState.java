package com.hexvane.wraithbusters.player;

import com.hexvane.wraithbusters.team.Team;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
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
    private boolean alive = true;
    @Nullable
    private Transform savedReturnTransform;
    @Nullable
    private UUID savedReturnWorldUuid;
    private boolean inventoryStashed;
    private boolean roundEndDismissed;
    @Nonnull
    private final List<ItemStack> stashedItems = new ArrayList<>();

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

    @Nullable
    public UUID getSavedReturnWorldUuid() {
        return savedReturnWorldUuid;
    }

    public void setSavedReturnWorldUuid(@Nullable UUID savedReturnWorldUuid) {
        this.savedReturnWorldUuid = savedReturnWorldUuid;
    }

    public boolean isInventoryStashed() {
        return inventoryStashed;
    }

    public void setInventoryStashed(boolean inventoryStashed) {
        this.inventoryStashed = inventoryStashed;
    }

    public boolean isRoundEndDismissed() {
        return roundEndDismissed;
    }

    public void setRoundEndDismissed(boolean roundEndDismissed) {
        this.roundEndDismissed = roundEndDismissed;
    }

    @Nonnull
    public List<ItemStack> getStashedItems() {
        return stashedItems;
    }

    public void resetForLobby() {
        role = PlayerRole.LOBBY;
        team = Team.NONE;
        ready = false;
        ghostMana = 0;
        alive = true;
        roundEndDismissed = false;
    }
}
