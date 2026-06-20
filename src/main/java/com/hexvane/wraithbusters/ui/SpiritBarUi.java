package com.hexvane.wraithbusters.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import javax.annotation.Nonnull;

/** Layered spirit power bar: cyan fill and border frame. */
public final class SpiritBarUi {
    private static final String STACK = "#SpiritHudPanel #SpiritBarStack";
    private static final int FLEX_SCALE = 1000;

    private SpiritBarUi() {}

    public static void apply(@Nonnull UICommandBuilder cmd, int current, int max) {
        int cap = Math.max(1, max);
        int value = Math.max(0, Math.min(current, cap));
        applyFill(cmd, value, cap);
    }

    private static void applyFill(@Nonnull UICommandBuilder cmd, int value, int cap) {
        String track = STACK + " #SpiritBarFillTrack";
        float progress = (float) value / (float) cap;
        int fillWeight = Math.max(0, Math.round(progress * FLEX_SCALE));
        int remainWeight = Math.max(1, FLEX_SCALE - fillWeight);
        if (fillWeight == 0) {
            remainWeight = FLEX_SCALE;
        }
        cmd.set(track + " #SpiritBarFill.FlexWeight", fillWeight);
        cmd.set(track + " #SpiritBarFillRemainder.FlexWeight", remainWeight);
        cmd.set(track + " #SpiritBarFill.Visible", fillWeight > 0);
    }
}
