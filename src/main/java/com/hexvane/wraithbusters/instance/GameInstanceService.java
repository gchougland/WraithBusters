package com.hexvane.wraithbusters.instance;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.instances.config.InstanceEntityConfig;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GameInstanceService {
    private GameInstanceService() {}

    @Nonnull
    public static CompletableFuture<World> createGameWorld(
        @Nonnull World originWorld,
        @Nonnull Transform returnPoint,
        @Nullable String worldName
    ) {
        InstancesPlugin plugin = InstancesPlugin.get();
        if (worldName != null && !worldName.isBlank()) {
            return plugin.spawnInstance(WraithBustersConstants.INSTANCE_NAME, worldName, originWorld, returnPoint);
        }
        return plugin.spawnInstance(WraithBustersConstants.INSTANCE_NAME, originWorld, returnPoint);
    }

    public static void teleportToLoadingInstance(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull CompletableFuture<World> worldFuture,
        @Nullable Transform overrideReturn
    ) {
        InstancesPlugin.teleportPlayerToLoadingInstance(entityRef, accessor, worldFuture, overrideReturn);
    }

    public static void teleportWithinInstance(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull World targetWorld,
        @Nullable Transform overrideReturn
    ) {
        InstancesPlugin.teleportPlayerToInstance(entityRef, accessor, targetWorld, overrideReturn);
    }

    @Nonnull
    public static CompletableFuture<Void> exitToOrigin(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        return InstancesPlugin.exitInstance(entityRef, accessor);
    }

    public static void safeRemoveWorld(@Nullable World world) {
        if (world != null) {
            InstancesPlugin.safeRemoveInstance(world);
        }
    }

    public static boolean isInManagedInstance(@Nonnull Ref<EntityStore> entityRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        return accessor.getComponent(entityRef, InstanceEntityConfig.getComponentType()) != null;
    }
}
