package com.hexvane.wraithbusters.possessable;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.util.BlockSectionQueries;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hexvane.wraithbusters.util.StatueFacingUtil;
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
import com.hypixel.hytale.server.core.entity.UUIDComponent;
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
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
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
    private static final double FACE_Y_OFFSET = 0.85;
    private static final double FACE_FORWARD_OFFSET = 0.35;

    private WatcherStatueBurstService() {}

    public static boolean isBusy(@Nonnull World world, @Nonnull Vector3i blockPos) {
        ConcurrentHashMap<Long, Long> busy = BUSY_BY_WORLD.get(world.getWorldConfig().getUuid());
        if (busy == null) {
            return false;
        }
        Long expiry = busy.get(packBlockPos(blockPos));
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiry) {
            busy.remove(packBlockPos(blockPos));
            return false;
        }
        return true;
    }

    public static void triggerBurst(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull WraithBustersPluginConfig config
    ) {
        BlockType blockType = world.getBlockType(anchor.x, anchor.y, anchor.z);
        if (blockType == null || !WraithBustersConstants.POSSESSABLE_WATCHER_STATUE_BLOCK_ID.equals(blockType.getId())) {
            return;
        }
        RotationTuple rotationTuple = resolveRotation(world, anchor);
        if (rotationTuple == null) {
            return;
        }
        BurstAxes axes = resolveBurstAxes(anchor, blockType, rotationTuple);

        long cooldownMs = config.getWatcherBurstCooldownTicks() * 50L;
        registerBusy(world, anchor, System.currentTimeMillis() + cooldownMs);

        int shotCount = config.getWatcherFeatherCount();
        long shotDelayMs = config.getWatcherFeatherShotDelayTicks() * 50L;
        for (int shot = 0; shot < shotCount; shot++) {
            long delayMs = shot * shotDelayMs;
            HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> DeferredWorldTasks.run(world, () -> fireShot(world, ghostRef, axes)),
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
        @Nonnull Ref<EntityStore> ghostRef,
        @Nonnull BurstAxes axes
    ) {
        if (!DeferredWorldTasks.isStoreOpen(world) || !ghostRef.isValid()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
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
        Vector3d dir = new Vector3d(direction);
        Rotation3f rotation = new Rotation3f();
        Direction rotationOffset = config.getSpawnRotationOffset();
        rotation.setYaw(PhysicsMath.normalizeTurnAngle(PhysicsMath.headingFromDirection(dir.x, dir.z)));
        rotation.setPitch(PhysicsMath.pitchFromDirection(dir.x, dir.y, dir.z));
        rotation.add(rotationOffset.pitch, rotationOffset.yaw, rotationOffset.roll);
        PhysicsMath.vectorFromAngles(rotation.yaw(), rotation.pitch(), dir);
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
        config.getPhysicsConfig().apply(holder, ghostRef, new Vector3d(dir).mul(config.getLaunchForce()), store, false);
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

    @Nullable
    private static RotationTuple resolveRotation(@Nonnull World world, @Nonnull Vector3i anchor) {
        int rotationIndex = BlockSectionQueries.getRotationIndex(world, anchor.x, anchor.y, anchor.z);
        return RotationTuple.get(rotationIndex);
    }

    private static void registerBusy(@Nonnull World world, @Nonnull Vector3i anchor, long expiryMs) {
        BUSY_BY_WORLD
            .computeIfAbsent(world.getWorldConfig().getUuid(), ignored -> new ConcurrentHashMap<>())
            .put(packBlockPos(anchor), expiryMs);
    }

    private static long packBlockPos(@Nonnull Vector3i pos) {
        return ((long) pos.x << 42) | ((long) pos.y << 21) | (pos.z & 0x1FFFFFL);
    }

    private record BurstAxes(@Nonnull Vector3d origin, @Nonnull Vector3d forward) {}
}
