package com.hexvane.wraithbusters.arena;

import javax.annotation.Nonnull;
import org.joml.Vector3i;

public final class PossessableMarker {
    @Nonnull
    private Vector3i blockPos = new Vector3i();
    @Nonnull
    private String typeId = "plate";

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
}
