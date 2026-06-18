package com.hexvane.wraithbusters.instance;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.instances.config.InstanceEntityConfig;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.builtin.instances.config.WorldReturnPoint;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
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

    /**
     * Ensures the entity's instance return point targets a persistent overworld, not a temporary instance world.
     * Hytale's instance teleport API stores the departed world's UUID in the return point even when the transform
     * is an overworld location, which breaks {@code exitInstance} once that instance is removed.
     */
    public static void repairReturnPoint(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull UUID returnWorldUuid,
        @Nonnull Transform returnTransform
    ) {
        InstanceEntityConfig config = accessor.ensureAndGetComponent(entityRef, InstanceEntityConfig.getComponentType());
        config.setReturnPoint(new WorldReturnPoint(returnWorldUuid, new Transform(returnTransform), false));
        config.setReturnPointOverride(null);
    }

    @Nonnull
    public static UUID resolveOverworldReturnWorldUuid(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nullable UUID preferredWorldUuid
    ) {
        if (preferredWorldUuid != null && isPersistentWorld(preferredWorldUuid)) {
            return preferredWorldUuid;
        }
        if (isInManagedInstance(entityRef, accessor)) {
            InstanceEntityConfig config = accessor.getComponent(entityRef, InstanceEntityConfig.getComponentType());
            if (config != null && config.getReturnPoint() != null) {
                UUID configuredWorld = config.getReturnPoint().getWorld();
                if (isPersistentWorld(configuredWorld)) {
                    return configuredWorld;
                }
            }
        }
        World currentWorld = accessor.getExternalData().getWorld();
        UUID currentUuid = currentWorld.getWorldConfig().getUuid();
        if (isPersistentWorld(currentUuid)) {
            return currentUuid;
        }
        World defaultWorld = Universe.get().getDefaultWorld();
        if (defaultWorld != null) {
            return defaultWorld.getWorldConfig().getUuid();
        }
        return currentUuid;
    }

    public static void exitToOriginSafe(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nullable UUID fallbackWorldUuid,
        @Nullable Transform fallbackTransform
    ) {
        if (!isInManagedInstance(entityRef, accessor)) {
            return;
        }
        if (fallbackWorldUuid != null && fallbackTransform != null) {
            InstanceEntityConfig config = accessor.getComponent(entityRef, InstanceEntityConfig.getComponentType());
            WorldReturnPoint returnPoint = config != null ? config.getReturnPoint() : null;
            if (returnPoint == null || Universe.get().getWorld(returnPoint.getWorld()) == null) {
                repairReturnPoint(entityRef, accessor, fallbackWorldUuid, fallbackTransform);
            }
        }
        try {
            exitToOrigin(entityRef, accessor);
        } catch (IllegalArgumentException ex) {
            World targetWorld = fallbackWorldUuid != null ? Universe.get().getWorld(fallbackWorldUuid) : null;
            if (targetWorld == null) {
                targetWorld = Universe.get().getDefaultWorld();
            }
            if (targetWorld == null) {
                throw ex;
            }
            Transform spawn = fallbackTransform != null ? new Transform(fallbackTransform) : new Transform();
            accessor.addComponent(entityRef, Teleport.getComponentType(), Teleport.createForPlayer(targetWorld, spawn));
        }
    }

    public static void safeRemoveWorld(@Nullable World world) {
        if (world != null) {
            InstancesPlugin.safeRemoveInstance(world);
        }
    }

    public static boolean isInManagedInstance(@Nonnull Ref<EntityStore> entityRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        return accessor.getComponent(entityRef, InstanceEntityConfig.getComponentType()) != null;
    }

    private static boolean isPersistentWorld(@Nullable UUID worldUuid) {
        if (worldUuid == null) {
            return false;
        }
        World world = Universe.get().getWorld(worldUuid);
        if (world == null || !world.isAlive()) {
            return false;
        }
        return InstanceWorldConfig.get(world.getWorldConfig()) == null;
    }
}
