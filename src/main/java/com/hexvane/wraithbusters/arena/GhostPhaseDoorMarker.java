package com.hexvane.wraithbusters.arena;

import com.hypixel.hytale.math.vector.Transform;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Bidirectional ghost portal pair anchored to a locked door ({@code entry} / {@code exit} are symmetric sides). */
public final class GhostPhaseDoorMarker {
    @Nonnull
    private String id = "phase";
    @Nonnull
    private Transform entry = new Transform(0, 100, 0);
    @Nonnull
    private Transform exit = new Transform(0, 100, 1);
    @Nonnull
    private PhaseDoorSize doorSize = PhaseDoorSize.STANDARD_1x2;
    @Nonnull
    private List<Vector3i> doorBlocks = new ArrayList<>();

    @Nonnull
    public String getId() {
        return id;
    }

    public void setId(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    public Transform getEntry() {
        return entry;
    }

    public void setEntry(@Nonnull Transform entry) {
        this.entry = entry;
    }

    @Nonnull
    public Transform getExit() {
        return exit;
    }

    public void setExit(@Nonnull Transform exit) {
        this.exit = exit;
    }

    @Nonnull
    public PhaseDoorSize getDoorSize() {
        return doorSize;
    }

    public void setDoorSize(@Nonnull PhaseDoorSize doorSize) {
        this.doorSize = doorSize;
    }

    @Nonnull
    public List<Vector3i> getDoorBlocks() {
        return doorBlocks;
    }

    public void setDoorBlocks(@Nonnull List<Vector3i> doorBlocks) {
        this.doorBlocks = doorBlocks;
    }

    @Nonnull
    public static GhostPhaseDoorMarker copyOf(@Nonnull GhostPhaseDoorMarker source) {
        GhostPhaseDoorMarker copy = new GhostPhaseDoorMarker();
        copy.setId(source.getId());
        copy.setDoorSize(source.getDoorSize());
        copy.setEntry(new Transform(source.getEntry()));
        copy.setExit(new Transform(source.getExit()));
        List<Vector3i> blocks = new ArrayList<>(source.getDoorBlocks().size());
        for (Vector3i block : source.getDoorBlocks()) {
            blocks.add(new Vector3i(block));
        }
        copy.setDoorBlocks(blocks);
        return copy;
    }

    @Nonnull
    public static List<GhostPhaseDoorMarker> copyAll(@Nonnull List<GhostPhaseDoorMarker> source) {
        List<GhostPhaseDoorMarker> copy = new ArrayList<>(source.size());
        for (GhostPhaseDoorMarker door : source) {
            copy.add(copyOf(door));
        }
        return copy;
    }
}
