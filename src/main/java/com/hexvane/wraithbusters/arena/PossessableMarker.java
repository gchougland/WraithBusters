package com.hexvane.wraithbusters.arena;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class PossessableMarker {
    @Nonnull
    private Vector3i blockPos = new Vector3i();
    @Nonnull
    private String typeId = "plate";
    /** Chunk rotation index captured at setup; optional fallback when filler cells hold yaw. */
    @Nullable
    private Integer rotationIndex;

    @Nonnull
    public Vector3i getBlockPos() {
        return blockPos;
    }

    public void setBlockPos(@Nonnull Vector3i blockPos) {
        this.blockPos = blockPos;
    }

    @Nonnull
    public String getTypeId() {
        return typeId;
    }

    public void setTypeId(@Nonnull String typeId) {
        this.typeId = typeId;
    }

    @Nullable
    public Integer getRotationIndex() {
        return rotationIndex;
    }

    public void setRotationIndex(@Nullable Integer rotationIndex) {
        this.rotationIndex = rotationIndex;
    }
}
