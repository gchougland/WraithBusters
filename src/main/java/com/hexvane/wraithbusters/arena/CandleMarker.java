package com.hexvane.wraithbusters.arena;

import javax.annotation.Nonnull;
import org.joml.Vector3i;

public final class CandleMarker {
    @Nonnull
    private String puzzleId = "candles";
    private int index;
    private boolean requiredOn = true;
    @Nonnull
    private Vector3i blockPos = new Vector3i();

    @Nonnull
    public String getPuzzleId() {
        return puzzleId;
    }

    public void setPuzzleId(@Nonnull String puzzleId) {
        this.puzzleId = puzzleId;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public boolean isRequiredOn() {
        return requiredOn;
    }

    public void setRequiredOn(boolean requiredOn) {
        this.requiredOn = requiredOn;
    }

    @Nonnull
    public Vector3i getBlockPos() {
        return blockPos;
    }

    public void setBlockPos(@Nonnull Vector3i blockPos) {
        this.blockPos = blockPos;
    }
}
