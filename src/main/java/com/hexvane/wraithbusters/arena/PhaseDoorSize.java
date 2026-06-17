package com.hexvane.wraithbusters.arena;

public enum PhaseDoorSize {
    STANDARD_1x2,
    STANDARD_2x2,
    MEDIUM_3x3,
    LARGE_4x4;

    public double triggerRadius() {
        return 0.75;
    }

    public int widthBlocks() {
        return switch (this) {
            case STANDARD_1x2 -> 1;
            case STANDARD_2x2 -> 2;
            case MEDIUM_3x3 -> 3;
            case LARGE_4x4 -> 4;
        };
    }

    public int heightBlocks() {
        return switch (this) {
            case STANDARD_1x2, STANDARD_2x2 -> 2;
            case MEDIUM_3x3 -> 3;
            case LARGE_4x4 -> 4;
        };
    }

    /**
     * Rendered height of this tier's portal particle effect in world blocks.
     * Must stay in sync with {@code portal_entity_height_blocks()} in generate_portal_particles.py.
     * Vertical lift is applied via particle PositionOffset.Y = this / 2, not Java spawn Y.
     */
    public double portalEntityHeightBlocks() {
        return heightBlocks();
    }
}
