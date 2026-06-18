package com.hexvane.wraithbusters.arena;

import com.hypixel.hytale.math.vector.Transform;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class ArenaLayout {
    @Nonnull
    private String arenaId = "mansion_v1";
    @Nonnull
    private Transform lobbySpawn = new Transform(0, 100, 0);
    @Nonnull
    private List<Transform> humanSpawns = new ArrayList<>();
    @Nonnull
    private List<Transform> ghostSpawns = new ArrayList<>();
    @Nonnull
    private List<Vector3i> manaPickups = new ArrayList<>();
    @Nonnull
    private List<PossessableMarker> possessables = new ArrayList<>();
    @Nonnull
    private List<RoomDefinition> rooms = new ArrayList<>();
    @Nonnull
    private List<CandleMarker> candles = new ArrayList<>();
    @Nonnull
    private List<GhostPhaseDoorMarker> ghostPhaseDoors = new ArrayList<>();
    @Nonnull
    private Vector3i exorcismTable = new Vector3i(0, 100, 0);
    @Nonnull
    private List<Transform> cheeseChaseSmallMice = new ArrayList<>();
    @Nullable
    private Transform cheeseChaseChumbo;

    @Nonnull
    public String getArenaId() {
        return arenaId;
    }

    public void setArenaId(@Nonnull String arenaId) {
        this.arenaId = arenaId;
    }

    @Nonnull
    public Transform getLobbySpawn() {
        return lobbySpawn;
    }

    public void setLobbySpawn(@Nonnull Transform lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
    }

    @Nonnull
    public List<Transform> getHumanSpawns() {
        return humanSpawns;
    }

    public void setHumanSpawns(@Nonnull List<Transform> humanSpawns) {
        this.humanSpawns = humanSpawns;
    }

    @Nonnull
    public List<Transform> getGhostSpawns() {
        return ghostSpawns;
    }

    public void setGhostSpawns(@Nonnull List<Transform> ghostSpawns) {
        this.ghostSpawns = ghostSpawns;
    }

    @Nonnull
    public List<Vector3i> getManaPickups() {
        return manaPickups;
    }

    public void setManaPickups(@Nonnull List<Vector3i> manaPickups) {
        this.manaPickups = manaPickups;
    }

    @Nonnull
    public List<PossessableMarker> getPossessables() {
        return possessables;
    }

    public void setPossessables(@Nonnull List<PossessableMarker> possessables) {
        this.possessables = possessables;
    }

    @Nonnull
    public List<RoomDefinition> getRooms() {
        return rooms;
    }

    public void setRooms(@Nonnull List<RoomDefinition> rooms) {
        this.rooms = rooms;
    }

    @Nonnull
    public List<CandleMarker> getCandles() {
        return candles;
    }

    public void setCandles(@Nonnull List<CandleMarker> candles) {
        this.candles = candles;
    }

    @Nonnull
    public List<GhostPhaseDoorMarker> getGhostPhaseDoors() {
        return ghostPhaseDoors;
    }

    public void setGhostPhaseDoors(@Nonnull List<GhostPhaseDoorMarker> ghostPhaseDoors) {
        this.ghostPhaseDoors = ghostPhaseDoors;
    }

    @Nonnull
    public Vector3i getExorcismTable() {
        return exorcismTable;
    }

    public void setExorcismTable(@Nonnull Vector3i exorcismTable) {
        this.exorcismTable = exorcismTable;
    }

    @Nonnull
    public List<Transform> getCheeseChaseSmallMice() {
        return cheeseChaseSmallMice;
    }

    public void setCheeseChaseSmallMice(@Nonnull List<Transform> cheeseChaseSmallMice) {
        this.cheeseChaseSmallMice = cheeseChaseSmallMice;
    }

    @Nullable
    public Transform getCheeseChaseChumbo() {
        return cheeseChaseChumbo;
    }

    public void setCheeseChaseChumbo(@Nullable Transform cheeseChaseChumbo) {
        this.cheeseChaseChumbo = cheeseChaseChumbo;
    }

    public void ensureDefaultSpawns() {
        if (humanSpawns.isEmpty()) {
            humanSpawns.add(new Transform(2, 100, 0));
        }
        if (ghostSpawns.isEmpty()) {
            ghostSpawns.add(new Transform(-2, 110, 0));
        }
    }
}
