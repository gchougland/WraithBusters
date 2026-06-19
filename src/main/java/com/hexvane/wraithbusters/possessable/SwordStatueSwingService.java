package com.hexvane.wraithbusters.possessable;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.block.StatueFillerRepairService;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.util.BlockSectionQueries;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hexvane.wraithbusters.util.StatueAnchorUtil;
import com.hexvane.wraithbusters.util.StatueFacingUtil;
import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Drives sword-statue block swing animation, timed damage, and cooldown tracking. */
public final class SwordStatueSwingService {
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<Long, Long>> SWINGING_BY_WORLD = new ConcurrentHashMap<>();
    private static final double STATUE_HEIGHT = 3.0;
    private static final double PLAYER_HEIGHT = 1.75;
    private static final double SWING_ORIGIN_Y_OFFSET = 1.2;
    private static final double MIN_FORWARD_REACH = -0.5;
    private static final int ARC_SWING_STEPS = 5;
    /** Half depth of Statue_Full hitbox — moves slash origin from block center to the front face. */
    private static final double SLASH_FACE_FORWARD_OFFSET = 0.5;
    /** Constant forward reach from the front face toward targets. */
    private static final double SLASH_REACH_FROM_FACE = 1.1;
    /** Half-width of the lateral slash arc in blocks. */
    private static final double SLASH_LATERAL_HALF = 0.25;
    /** Sword_Charged_Trail_Blade spawner PositionOffset.X is -0.3 along local +X. */
    private static final double SLASH_TRAIL_ASSET_X_OFFSET = 0.3;

    private SwordStatueSwingService() {}

    public static boolean isSwinging(@Nonnull World world, @Nonnull Vector3i blockPos) {
        Vector3i anchor = StatueAnchorUtil.resolveStatueAnchor(world, blockPos);
        ConcurrentHashMap<Long, Long> swings = SWINGING_BY_WORLD.get(world.getWorldConfig().getUuid());
        if (swings == null) {
            return false;
        }
        Long expiry = swings.get(packBlockPos(anchor));
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiry) {
            swings.remove(packBlockPos(anchor));
            return false;
        }
        return true;
    }

    public static void triggerSwing(
        @Nonnull World world,
        @Nonnull Vector3i blockPos,
        @Nonnull WraithBustersPluginConfig config
    ) {
        Vector3i anchor = StatueAnchorUtil.resolveStatueAnchor(world, blockPos);
        StatueFillerRepairService.repairAt(world, anchor);

        BlockType blockType = world.getBlockType(anchor.x, anchor.y, anchor.z);
        if (blockType == null || !WraithBustersConstants.POSSESSABLE_STATUE_BLOCK_ID.equals(blockType.getId())) {
            return;
        }

        RotationTuple rotationTuple = resolveRotation(world, anchor);
        if (rotationTuple == null) {
            return;
        }
        SwingAxes axes = resolveSwingAxes(anchor, blockType, rotationTuple);

        long durationMs = config.getStatueSwingDurationTicks() * 50L;
        registerSwinging(world, anchor, System.currentTimeMillis() + durationMs);

        DeferredWorldTasks.run(world, () -> {
            if (!DeferredWorldTasks.isStoreOpen(world)) {
                return;
            }
            applyInteractionState(world, anchor, WraithBustersConstants.STATUE_SWING_STATE);
            BlockType current = world.getBlockType(anchor.x, anchor.y, anchor.z);
            WraithBustersSoundUtil.playBlockStateSound(world, anchor, current, WraithBustersConstants.STATUE_SWING_STATE);
            WraithBustersSoundUtil.play3dAtPosition(
                world,
                axes.origin.x,
                axes.origin.y,
                axes.origin.z,
                WraithBustersConstants.STATUE_SWING_SOUND_EVENT
            );
        });

        long damageDelayMs = config.getStatueSwingDamageDelayTicks() * 50L;
        HytaleServer.SCHEDULED_EXECUTOR.schedule(
            () -> DeferredWorldTasks.run(world, () -> damageHumansInFront(world, anchor, config)),
            damageDelayMs,
            TimeUnit.MILLISECONDS
        );
        HytaleServer.SCHEDULED_EXECUTOR.schedule(
            () -> DeferredWorldTasks.run(world, () -> resetSwingState(world, anchor)),
            durationMs,
            TimeUnit.MILLISECONDS
        );
    }

    public static void clearWorld(@Nonnull UUID worldUuid) {
        SWINGING_BY_WORLD.remove(worldUuid);
    }

    private static void resetSwingState(@Nonnull World world, @Nonnull Vector3i anchor) {
        if (!DeferredWorldTasks.isStoreOpen(world)) {
            clearSwinging(world, anchor);
            return;
        }
        applyInteractionState(world, anchor, "default");
        clearSwinging(world, anchor);
    }

    private static void applyInteractionState(@Nonnull World world, @Nonnull Vector3i anchor, @Nonnull String state) {
        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(anchor.x, anchor.z));
        if (chunk == null) {
            return;
        }
        BlockType blockType = world.getBlockType(anchor.x, anchor.y, anchor.z);
        if (blockType == null) {
            return;
        }
        chunk.setBlockInteractionState(anchor.x, anchor.y, anchor.z, blockType, state, true);
    }

    private static void damageHumansInFront(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        @Nonnull WraithBustersPluginConfig config
    ) {
        if (!DeferredWorldTasks.isStoreOpen(world)) {
            return;
        }
        GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
        if (session == null || session.getPhase() != GamePhase.ACTIVE) {
            return;
        }

        DamageCause cause = resolveSlashingCause();
        if (cause == null) {
            return;
        }

        RotationTuple rotationTuple = resolveRotation(world, anchor);
        if (rotationTuple == null) {
            return;
        }

        BlockType blockType = world.getBlockType(anchor.x, anchor.y, anchor.z);
        if (blockType == null) {
            return;
        }

        SwingAxes axes = resolveSwingAxes(anchor, blockType, rotationTuple);
        float hitRadius = config.getStatueSwingHitRadius();

        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        spawnArcParticles(store, axes);

        float damageAmount = config.getStatueDamage();

        for (var entry : session.getPlayers().entrySet()) {
            PlayerSessionState state = entry.getValue();
            if (state.getRole() != PlayerRole.HUMAN || !state.isAlive()) {
                continue;
            }
            PlayerRef playerRef = com.hypixel.hytale.server.core.universe.Universe.get().getPlayer(entry.getKey());
            if (playerRef == null) {
                continue;
            }
            var humanRef = playerRef.getReference();
            if (humanRef == null || !humanRef.isValid()) {
                continue;
            }
            var transform = store.getComponent(
                humanRef,
                com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType()
            );
            if (transform == null) {
                continue;
            }
            Vector3d playerFeet = transform.getPosition();
            if (!isInSwingRadius(anchor, playerFeet, axes, hitRadius)) {
                continue;
            }
            DamageSystems.executeDamage(humanRef, store, new Damage(Damage.NULL_SOURCE, cause, damageAmount));
            Vector3d hitPos = new Vector3d(playerFeet.x, playerFeet.y + 0.9, playerFeet.z);
            ParticleUtil.spawnParticleEffect(
                WraithBustersConstants.STATUE_SWING_HIT_PARTICLE,
                hitPos,
                axes.yaw,
                0.0f,
                0.0f,
                1.0f,
                0.0f,
                store
            );
        }
    }

    private static void spawnArcParticles(
        @Nonnull Store<EntityStore> store,
        @Nonnull SwingAxes axes
    ) {
        // Trail blade extends along local X; statue swing sweeps laterally (right), not forward.
        float slashYaw = StatueFacingUtil.yawRadians(axes.right);
        Vector3d slashOrigin = new Vector3d(axes.origin).fma(SLASH_FACE_FORWARD_OFFSET, axes.forward);
        Vector3d trailBias = StatueFacingUtil.localXWorld(slashYaw, SLASH_TRAIL_ASSET_X_OFFSET);
        for (int step = 0; step < ARC_SWING_STEPS; step++) {
            float sweepT = ARC_SWING_STEPS <= 1 ? 0.5f : step / (float) (ARC_SWING_STEPS - 1);
            double lateral = (sweepT - 0.5) * 2.0 * SLASH_LATERAL_HALF;
            Vector3d slashPos = new Vector3d(slashOrigin)
                .fma(SLASH_REACH_FROM_FACE, axes.forward)
                .fma(lateral, axes.right)
                .sub(trailBias);
            ParticleUtil.spawnParticleEffect(
                WraithBustersConstants.STATUE_SWING_ARC_PARTICLE,
                slashPos,
                slashYaw,
                -0.2f,
                0.0f,
                1.2f,
                0.0f,
                store
            );
        }
    }

    @Nonnull
    private static SwingAxes resolveSwingAxes(
        @Nonnull Vector3i anchor,
        @Nonnull BlockType blockType,
        @Nonnull RotationTuple rotationTuple
    ) {
        Vector3d forward = StatueFacingUtil.forward(rotationTuple);
        Vector3d right = StatueFacingUtil.right(rotationTuple);
        Vector3d origin = StatueFacingUtil.blockCenter(anchor, blockType, rotationTuple, SWING_ORIGIN_Y_OFFSET);
        return new SwingAxes(origin, forward, right, StatueFacingUtil.yawRadians(forward));
    }

    private static boolean isInSwingRadius(
        @Nonnull Vector3i anchor,
        @Nonnull Vector3d playerFeet,
        @Nonnull SwingAxes axes,
        float radius
    ) {
        double playerHead = playerFeet.y + PLAYER_HEIGHT;
        if (playerHead < anchor.y || playerFeet.y > anchor.y + STATUE_HEIGHT) {
            return false;
        }
        Vector3d playerTorso = new Vector3d(playerFeet.x, playerFeet.y + 0.9, playerFeet.z);
        Vector3d delta = playerTorso.sub(axes.origin, new Vector3d());
        double forwardDist = delta.dot(axes.forward);
        if (forwardDist < MIN_FORWARD_REACH || forwardDist > radius) {
            return false;
        }
        double lateral = Math.abs(delta.dot(axes.right));
        return lateral <= radius;
    }

    @Nullable
    private static RotationTuple resolveRotation(@Nonnull World world, @Nonnull Vector3i anchor) {
        int rotationIndex = BlockSectionQueries.getRotationIndex(world, anchor.x, anchor.y, anchor.z);
        return RotationTuple.get(rotationIndex);
    }

    @Nullable
    private static DamageCause resolveSlashingCause() {
        IndexedLookupTableAssetMap<String, DamageCause> map = DamageCause.getAssetMap();
        DamageCause slashing = map.getAsset("Slashing");
        if (slashing != null) {
            return slashing;
        }
        DamageCause physical = map.getAsset("Physical");
        if (physical != null) {
            return physical;
        }
        return map.getAsset("Environment");
    }

    private static void registerSwinging(@Nonnull World world, @Nonnull Vector3i anchor, long expiryMs) {
        SWINGING_BY_WORLD
            .computeIfAbsent(world.getWorldConfig().getUuid(), ignored -> new ConcurrentHashMap<>())
            .put(packBlockPos(anchor), expiryMs);
    }

    private static void clearSwinging(@Nonnull World world, @Nonnull Vector3i anchor) {
        ConcurrentHashMap<Long, Long> swings = SWINGING_BY_WORLD.get(world.getWorldConfig().getUuid());
        if (swings != null) {
            swings.remove(packBlockPos(anchor));
        }
    }

    private static long packBlockPos(@Nonnull Vector3i blockPos) {
        return ((long) blockPos.x << 40) | ((long) blockPos.y << 20) | (blockPos.z & 0xFFFFFL);
    }

    private record SwingAxes(@Nonnull Vector3d origin, @Nonnull Vector3d forward, @Nonnull Vector3d right, float yaw) {}
}
