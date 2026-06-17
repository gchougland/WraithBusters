package com.hexvane.wraithbusters.util;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class PlayerTeleportUtil {
    private PlayerTeleportUtil() {}

    public static void teleport(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull World world,
        @Nonnull Transform transform
    ) {
        accessor.addComponent(playerRef, Teleport.getComponentType(), Teleport.createForPlayer(world, transform));
    }

    public static void teleport(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull World world,
        @Nonnull Transform fallback,
        int index,
        @Nonnull Transform[] pool
    ) {
        Transform chosen = pool.length == 0 ? fallback : pool[Math.floorMod(index, pool.length)];
        teleport(playerRef, accessor, world, chosen);
    }
}
