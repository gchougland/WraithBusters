package com.hexvane.wraithbusters.arena;

public enum PhaseDoorSize {
    STANDARD_1x2,
    STANDARD_2x2,
    MEDIUM_3x3,
    LARGE_4x4;

    public double triggerRadius() {
        return switch (this) {
            case STANDARD_1x2 -> 1.2;
            case STANDARD_2x2 -> 1.5;
            case MEDIUM_3x3 -> 2.0;
            case LARGE_4x4 -> 2.5;
        };
    }

    public double portalYOffset() {
        return switch (this) {
            case STANDARD_1x2 -> 0.55;
            case STANDARD_2x2 -> 0.55;
            case MEDIUM_3x3 -> 1.0;
            case LARGE_4x4 -> 1.5;
        };
    }
}
