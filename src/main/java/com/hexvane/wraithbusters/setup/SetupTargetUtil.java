package com.hexvane.wraithbusters.setup;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class SetupTargetUtil {
    private static final double TARGET_DISTANCE = 24.0;

    private SetupTargetUtil() {}

    @Nullable
    public static Vector3i resolveLookedAtBlock(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        Vector3d hit = TargetUtil.getTargetLocation(ref, TARGET_DISTANCE, store);
        if (hit == null) {
            return null;
        }
        return blockFromHit(hit);
    }

    /** Locked-door block nearest what the player is looking at (handles rotated door raycasts). */
    @Nullable
    public static Vector3i resolveLookedAtLockedDoor(
        @Nonnull World world,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        Vector3i hint = resolveLookedAtBlock(ref, store);
        if (hint == null) {
            return null;
        }
        return PhaseDoorAnalyzer.resolveSeedBlock(world, hint);
    }

    @Nonnull
    public static Vector3i blockFromHit(@Nonnull Vector3d hit) {
        return new Vector3i(
            (int) Math.floor(hit.x),
            (int) Math.floor(hit.y),
            (int) Math.floor(hit.z)
        );
    }
}
