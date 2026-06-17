package com.hexvane.wraithbusters.arena;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class RoomDefinition {
    @Nonnull
    private String roomId = "room";
    /** Legacy single-door field; migrated into {@link #doorBlocks} on first access. */
    @Nullable
    private Vector3i doorBlock;
    @Nonnull
    private List<Vector3i> doorBlocks = new ArrayList<>();
    @Nonnull
    private String symbolId = "circle";
    @Nonnull
    private String puzzleId = "candles";
    @Nonnull
    private Vector3i keySpawn = new Vector3i();

    @Nonnull
    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(@Nonnull String roomId) {
        this.roomId = roomId;
    }

    @Nonnull
    public List<Vector3i> getDoorBlocks() {
        migrateLegacyDoor();
        return doorBlocks;
    }

    public void setDoorBlocks(@Nonnull List<Vector3i> doorBlocks) {
        this.doorBlocks = new ArrayList<>(doorBlocks);
        this.doorBlock = null;
    }

    /** @deprecated use {@link #getDoorBlocks()} */
    @Deprecated
    @Nonnull
    public Vector3i getDoorBlock() {
        List<Vector3i> doors = getDoorBlocks();
        return doors.isEmpty() ? new Vector3i() : new Vector3i(doors.getFirst());
    }

    /** @deprecated use {@link #getDoorBlocks()} */
    @Deprecated
    public void setDoorBlock(@Nonnull Vector3i doorBlock) {
        this.doorBlock = new Vector3i(doorBlock);
        this.doorBlocks = new ArrayList<>();
    }

    public void addDoorBlock(@Nonnull Vector3i block) {
        migrateLegacyDoor();
        if (!hasDoorBlock(block)) {
            doorBlocks.add(new Vector3i(block));
        }
    }

    public void addDoorBlocks(@Nonnull List<Vector3i> blocks) {
        migrateLegacyDoor();
        for (Vector3i block : blocks) {
            addDoorBlock(block);
        }
    }

    public boolean hasDoorBlock(@Nonnull Vector3i block) {
        migrateLegacyDoor();
        for (Vector3i existing : doorBlocks) {
            if (existing.x == block.x && existing.y == block.y && existing.z == block.z) {
                return true;
            }
        }
        return false;
    }

    private void migrateLegacyDoor() {
        if (doorBlocks.isEmpty() && doorBlock != null) {
            doorBlocks.add(new Vector3i(doorBlock));
            doorBlock = null;
        }
    }

    @Nonnull
    public String getSymbolId() {
        return symbolId;
    }

    public void setSymbolId(@Nonnull String symbolId) {
        this.symbolId = symbolId;
    }

    @Nonnull
    public String getPuzzleId() {
        return puzzleId;
    }

    public void setPuzzleId(@Nonnull String puzzleId) {
        this.puzzleId = puzzleId;
    }

    @Nonnull
    public Vector3i getKeySpawn() {
        return keySpawn;
    }

    public void setKeySpawn(@Nonnull Vector3i keySpawn) {
        this.keySpawn = keySpawn;
    }
}
