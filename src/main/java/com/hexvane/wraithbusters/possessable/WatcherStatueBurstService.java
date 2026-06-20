package com.hexvane.wraithbusters.possessable;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.arena.PossessableMarker;
import com.hexvane.wraithbusters.block.StatueFillerRepairService;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hexvane.wraithbusters.util.StatueFacingUtil;
import com.hexvane.wraithbusters.util.StatueRotationUtil;
import com.hexvane.wraithbusters.util.WatcherStatueAnchorUtil;
import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Fires a burst of forward-facing feather projectiles from a watcher statue face. */
public final class WatcherStatueBurstService {
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<Long, Long>> BUSY_BY_WORLD = new ConcurrentHashMap<>();
    /** Statue_Small spans two blocks tall; owl face sits on the upper block. */
    private static final double FACE_Y_OFFSET = 1.5;
    /** Offset from block center to the front face of the 1x1 hitbox. */
    private static final double FACE_FORWARD_OFFSET = 0.5;

    private WatcherStatueBurstService() {}

    public static boolean isBusy(@Nonnull World world, @Nonnull Vector3i blockPos) {
        Vector3i anchor = WatcherStatueAnchorUtil.resolveWatcherAnchor(world, blockPos);
        ConcurrentHashMap<Long, Long> busy = BUSY_BY_WORLD.get(world.getWorldConfig().getUuid());
        if (busy == null) {
            return false;
        }
        Long expiry = busy.get(packBlockPos(anchor));
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiry) {
            busy.remove(packBlockPos(anchor));
            return false;
        }
        return true;
    }

    public static void triggerBurst(
        @Nonnull World world,
        @Nonnull Vector3i blockPos,
        @Nullable PossessableMarker marker,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull WraithBustersPluginConfig config
    ) {
        Vector3i anchor = WatcherStatueAnchorUtil.resolveWatcherAnchor(world, blockPos);
        StatueFillerRepairService.repairWatcherAt(world, anchor);

        BlockType blockType = world.getBlockType(anchor.x, anchor.y, anchor.z);
        if (blockType == null || !isWatcherStatue(blockType)) {
            return;
        }

        long cooldownMs = config.getWatcherBurstCooldownTicks() * 50L;
        registerBusy(world, anchor, System.currentTimeMillis() + cooldownMs);

        int shotCount = config.getWatcherFeatherCount();
        long shotDelayMs = config.getWatcherFeatherShotDelayTicks() * 50L;
        for (int shot = 0; shot < shotCount; shot++) {
            long delayMs = shot * shotDelayMs;
            HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> DeferredWorldTasks.run(
                    world,
                    () -> fireShot(world, blockPos, anchor, marker, ghostRef)
                ),
                delayMs,
                TimeUnit.MILLISECONDS
            );
        }
    }

    public static void clearWorld(@Nonnull UUID worldUuid) {
        BUSY_BY_WORLD.remove(worldUuid);
    }

    private static void fireShot(
        @Nonnull World world,
        @Nonnull Vector3i columnRef,
        @Nonnull Vector3i anchor,
        @Nullable PossessableMarker marker,
        @Nonnull Ref<EntityStore> ghostRef
    ) {
        if (!DeferredWorldTasks.isStoreOpen(world) || !ghostRef.isValid()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }

        BlockType blockType = world.getBlockType(anchor.x, anchor.y, anchor.z);
        if (blockType == null) {
            return;
        }

        RotationTuple rotationTuple = resolveWatcherRotation(world, columnRef, anchor, blockType, marker);
        BurstAxes axes = resolveBurstAxes(anchor, blockType, rotationTuple);
        spawnFeatherFromStore(store, ghostRef, axes.origin, axes.forward);
        WraithBustersSoundUtil.play3dAtPosition(
            world,
            axes.origin.x,
            axes.origin.y,
            axes.origin.z,
            WraithBustersConstants.WATCHER_FEATHER_LAUNCH_SOUND_EVENT
        );
    }

    private static void spawnFeatherFromStore(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull Vector3d position,
        @Nonnull Vector3d direction
    ) {
        ProjectileConfig config = ProjectileConfig.getAssetMap().getAsset(WraithBustersConstants.WATCHER_FEATHER_PROJECTILE_CONFIG_ID);
        if (config == null) {
            return;
        }

        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        Vector3d launchDir = new Vector3d(direction);
        if (launchDir.lengthSquared() <= 0.0001) {
            launchDir.set(0.0, 0.0, 1.0);
        } else {
            launchDir.normalize();
        }
        Rotation3f rotation = new Rotation3f();
        Direction rotationOffset = config.getSpawnRotationOffset();
        rotation.setYaw(PhysicsMath.normalizeTurnAngle(PhysicsMath.headingFromDirection(launchDir.x, launchDir.z)));
        rotation.setPitch(PhysicsMath.pitchFromDirection(launchDir.x, launchDir.y, launchDir.z));
        rotation.add(rotationOffset.pitch, rotationOffset.yaw, rotationOffset.roll);
        Vector3d spawnPos = new Vector3d(position);
        spawnPos.add(config.getCalculatedOffset(rotation.pitch(), rotation.yaw()));

        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(spawnPos, rotation));
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
        holder.addComponent(Interactions.getComponentType(), new Interactions(config.getInteractions()));
        Model model = config.getModel();
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
        holder.addComponent(
            NetworkId.getComponentType(),
            new NetworkId(store.getExternalData().takeNextNetworkId())
        );
        holder.ensureComponent(ProjectileModule.get().getProjectileComponentType());
        holder.addComponent(Velocity.getComponentType(), new Velocity());
        config.getPhysicsConfig().apply(
            holder,
            ghostRef,
            new Vector3d(launchDir).mul(config.getLaunchForce()),
            store,
            false
        );
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        holder.addComponent(
            DespawnComponent.getComponentType(),
            new DespawnComponent(store.getResource(TimeResource.getResourceType()).getNow().plus(Duration.ofSeconds(300L)))
        );
        store.addEntity(holder, AddReason.SPAWN);
    }

    @Nonnull
    private static BurstAxes resolveBurstAxes(
        @Nonnull Vector3i anchor,
        @Nonnull BlockType blockType,
        @Nonnull RotationTuple rotationTuple
    ) {
        Vector3d forward = StatueFacingUtil.forward(rotationTuple);
        Vector3d origin = StatueFacingUtil.blockCenter(anchor, blockType, rotationTuple, FACE_Y_OFFSET);
        origin.add(new Vector3d(forward).mul(FACE_FORWARD_OFFSET));
        return new BurstAxes(origin, forward);
    }

    @Nonnull
    private static RotationTuple resolveWatcherRotation(
        @Nonnull World world,
        @Nonnull Vector3i columnRef,
        @Nonnull Vector3i anchor,
        @Nonnull BlockType blockType,
        @Nullable PossessableMarker marker
    ) {
        Vector3i rotationRef = marker != null ? marker.getBlockPos() : columnRef;
        RotationTuple rotationTuple = StatueRotationUtil.resolve(world, rotationRef, anchor, blockType);
        if (marker == null) {
            return rotationTuple;
        }
        Integer saved = marker.getRotationIndex();
        if (saved != null && rotationTuple.index() == 0 && saved != 0) {
            return RotationTuple.get(saved);
        }
        return rotationTuple;
    }

    private static boolean isWatcherStatue(@Nonnull BlockType blockType) {
        String id = blockType.getId();
        String base = WraithBustersConstants.POSSESSABLE_WATCHER_STATUE_BLOCK_ID;
        return id.equals(base) || id.startsWith(base + "|");
    }

    private static void registerBusy(@Nonnull World world, @Nonnull Vector3i anchor, long expiryMs) {
        BUSY_BY_WORLD
            .computeIfAbsent(world.getWorldConfig().getUuid(), ignored -> new ConcurrentHashMap<>())
            .put(packBlockPos(anchor), expiryMs);
    }

    private static long packBlockPos(@Nonnull Vector3i blockPos) {
        return ((long) blockPos.x << 40) | ((long) blockPos.y << 20) | (blockPos.z & 0xFFFFFL);
    }

    private record BurstAxes(@Nonnull Vector3d origin, @Nonnull Vector3d forward) {}
}
