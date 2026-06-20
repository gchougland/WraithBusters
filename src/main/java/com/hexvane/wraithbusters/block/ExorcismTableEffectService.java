package com.hexvane.wraithbusters.block;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.door.RoomProgressionService;
import com.hexvane.wraithbusters.game.GameService;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.util.BlockSectionQueries;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hexvane.wraithbusters.util.StatueFacingUtil;
import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Idle blue fire, activation burst, and round-end audio for the attic exorcism table. */
public final class ExorcismTableEffectService {
    private static final double EFFECT_CENTER_Y_OFFSET = 1.2;
    private static final ConcurrentHashMap<UUID, Boolean> IDLE_ACTIVE_BY_SESSION = new ConcurrentHashMap<>();

    private ExorcismTableEffectService() {}

    public static void prepareForRound(@Nonnull GameSession session, @Nonnull World world) {
        IDLE_ACTIVE_BY_SESSION.remove(session.getSessionId());
        runOnWorldThread(world, () -> setTableState(world, session.getArenaLayout().getExorcismTable(), WraithBustersConstants.EXORCISM_TABLE_DORMANT_STATE));
    }

    public static void tick(@Nonnull GameSession session, @Nonnull World world) {
        if (!RoomProgressionService.isDoorUnlocked(session, WraithBustersConstants.ATTIC_ROOM_ID)) {
            return;
        }
        if (!world.isInThread()) {
            world.execute(() -> tick(session, world));
            return;
        }
        Vector3i anchor = session.getArenaLayout().getExorcismTable();
        BlockType blockType = world.getBlockType(anchor.x, anchor.y, anchor.z);
        if (!isExorcismTable(blockType)) {
            return;
        }
        String currentState = BlockAccessor.getCurrentInteractionState(blockType);
        if (WraithBustersConstants.EXORCISM_TABLE_ACTIVATED_STATE.equals(currentState)) {
            return;
        }
        if (!WraithBustersConstants.EXORCISM_TABLE_CHARGING_STATE.equals(currentState)) {
            setTableState(world, anchor, WraithBustersConstants.EXORCISM_TABLE_CHARGING_STATE);
            IDLE_ACTIVE_BY_SESSION.remove(session.getSessionId());
        }
        maybeSpawnIdleFire(session, world, anchor);
    }

    public static void activate(@Nonnull World world, @Nonnull Vector3i targetBlock) {
        if (!world.isInThread()) {
            world.execute(() -> activate(world, targetBlock));
            return;
        }
        Vector3i anchor = ExorcismTableFillerRepairService.resolveAnchor(world, targetBlock);
        setTableState(world, anchor, WraithBustersConstants.EXORCISM_TABLE_ACTIVATED_STATE);
        Vector3d center = resolveEffectCenter(world, anchor);
        if (center == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        ParticleUtil.spawnParticleEffect(
            WraithBustersConstants.EXORCISM_BURST_PARTICLE,
            center,
            0.0f,
            0.0f,
            0.0f,
            WraithBustersConstants.EXORCISM_BURST_SCALE,
            WraithBustersConstants.EXORCISM_BURST_DURATION_SEC,
            store
        );
        WraithBustersSoundUtil.play3dAtPosition(
            world,
            center.x,
            center.y,
            center.z,
            WraithBustersConstants.EXORCISM_ACTIVATE_SOUND_EVENT
        );
    }

    public static void playRoundWinSound(@Nonnull GameSession session, @Nonnull World world, @Nonnull GameService gameService) {
        IDLE_ACTIVE_BY_SESSION.remove(session.getSessionId());
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (UUID playerUuid : session.playerUuidList()) {
            Ref<EntityStore> ref = gameService.findPlayerRef(world, playerUuid);
            if (ref != null) {
                WraithBustersSoundUtil.play2d(ref, store, WraithBustersConstants.ROUND_WIN_SOUND_EVENT);
            }
        }
    }

    private static void maybeSpawnIdleFire(@Nonnull GameSession session, @Nonnull World world, @Nonnull Vector3i anchor) {
        UUID sessionId = session.getSessionId();
        if (Boolean.TRUE.equals(IDLE_ACTIVE_BY_SESSION.get(sessionId))) {
            return;
        }
        if (!DeferredWorldTasks.isStoreOpen(world)) {
            return;
        }
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(anchor.x, anchor.z));
        if (chunk == null) {
            return;
        }
        Vector3d center = resolveEffectCenter(world, anchor);
        if (center == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        ParticleUtil.spawnParticleEffect(
            WraithBustersConstants.EXORCISM_IDLE_PARTICLE,
            center,
            0.0f,
            0.0f,
            0.0f,
            WraithBustersConstants.EXORCISM_IDLE_PARTICLE_SCALE,
            0.0f,
            store
        );
        IDLE_ACTIVE_BY_SESSION.put(sessionId, true);
    }

    private static void setTableState(@Nonnull World world, @Nonnull Vector3i blockPos, @Nonnull String state) {
        Vector3i anchor = ExorcismTableFillerRepairService.resolveAnchor(world, blockPos);
        BlockType blockType = world.getBlockType(anchor.x, anchor.y, anchor.z);
        if (!isExorcismTable(blockType)) {
            return;
        }
        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(anchor.x, anchor.z));
        if (chunk == null) {
            return;
        }
        chunk.setBlockInteractionState(anchor.x, anchor.y, anchor.z, blockType, state, true);
    }

    private static boolean isExorcismTable(@Nullable BlockType blockType) {
        if (blockType == null) {
            return false;
        }
        BlockType root = BlockType.getAssetMap().getAsset(WraithBustersConstants.EXORCISM_TABLE_BLOCK_ID);
        if (root == null) {
            return WraithBustersConstants.EXORCISM_TABLE_BLOCK_ID.equals(blockType.getId());
        }
        if (root.getId().equals(blockType.getId())) {
            return true;
        }
        BlockType charging = root.getBlockForState(WraithBustersConstants.EXORCISM_TABLE_CHARGING_STATE);
        if (charging != null && charging.getId().equals(blockType.getId())) {
            return true;
        }
        BlockType activated = root.getBlockForState(WraithBustersConstants.EXORCISM_TABLE_ACTIVATED_STATE);
        return activated != null && activated.getId().equals(blockType.getId());
    }

    @Nullable
    private static Vector3d resolveEffectCenter(@Nonnull World world, @Nonnull Vector3i anchor) {
        BlockType blockType = world.getBlockType(anchor.x, anchor.y, anchor.z);
        if (blockType == null) {
            return null;
        }
        int rotationIndex = BlockSectionQueries.getRotationIndex(world, anchor.x, anchor.y, anchor.z);
        RotationTuple rotationTuple = RotationTuple.get(rotationIndex);
        if (rotationTuple == null) {
            return null;
        }
        return StatueFacingUtil.blockCenter(anchor, blockType, rotationTuple, EFFECT_CENTER_Y_OFFSET);
    }

    private static void runOnWorldThread(@Nonnull World world, @Nonnull Runnable task) {
        if (!DeferredWorldTasks.isStoreOpen(world)) {
            return;
        }
        DeferredWorldTasks.run(world, task);
    }
}
