package com.hexvane.wraithbusters.portrait;

import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class SlothPortraitService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double DEFAULT_EYE_HEIGHT = 1.62;
    private static final int PORTRAIT_ASSEMBLY_MAX_BLOCKS = 4;
    private static final long BLINK_MIN_MS = 5000L;
    private static final long BLINK_MAX_EXTRA_MS = 2000L;
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<Long, PortraitEntry>> BY_WORLD = new ConcurrentHashMap<>();

    private SlothPortraitService() {}

    public static boolean hasPortraits(@Nonnull World world) {
        Map<Long, PortraitEntry> portraits = BY_WORLD.get(world.getWorldConfig().getUuid());
        return portraits != null && !portraits.isEmpty();
    }

    public static void onBlockPlaced(
        @Nonnull World world,
        @Nonnull Vector3i blockPos,
        @Nonnull RotationTuple rotationTuple,
        @Nonnull PortraitVariant variant
    ) {
        defer(world, () -> registerBlock(world, blockPos, rotationTuple, variant, false));
    }

    public static void onBlockBroken(
        @Nonnull World world,
        @Nonnull Vector3i blockPos,
        @Nonnull PortraitVariant variant
    ) {
        defer(world, () -> unregisterBlock(world, canonicalPortraitAnchor(world, blockPos, variant)));
    }

    public static void onBlockBroken(@Nonnull World world, @Nonnull Vector3i blockPos) {
        defer(world, () -> unregisterBlock(world, resolvePortraitAnchor(world, blockPos)));
    }

    public static void scanWorld(@Nonnull World world) {
        defer(world, () -> scanWorldNow(world));
    }

    public static void shutdownWorld(@Nonnull World world) {
        UUID worldUuid = world.getWorldConfig().getUuid();
        ConcurrentHashMap<Long, PortraitEntry> portraits = BY_WORLD.remove(worldUuid);
        if (portraits == null || portraits.isEmpty()) {
            return;
        }
        if (!DeferredWorldTasks.isStoreOpen(world)) {
            portraits.clear();
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (PortraitEntry entry : portraits.values()) {
            despawnNpc(store, entry);
        }
        portraits.clear();
    }

    public static void tickEyes(@Nonnull World world, @Nonnull WraithBustersPluginConfig config) {
        if (!DeferredWorldTasks.isStoreOpen(world)) {
            return;
        }
        Map<Long, PortraitEntry> portraits = BY_WORLD.get(world.getWorldConfig().getUuid());
        if (portraits == null || portraits.isEmpty()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        List<PlayerSample> players = collectPlayers(store);
        double trackRangeSq = config.getSlothPortraitTrackRange() * config.getSlothPortraitTrackRange();
        int poseCount = config.getSlothPortraitPoseCount();
        int centerPose = SlothPortraitGazeUtil.centerPoseIndex(poseCount);
        double halfFov = config.getSlothPortraitHalfFovWidth();
        long now = System.currentTimeMillis();

        for (PortraitEntry entry : portraits.values()) {
            if (entry.npcRef == null || !entry.npcRef.isValid()) {
                continue;
            }
            NPCEntity npc = store.getComponent(entry.npcRef, NPCEntity.getComponentType());
            if (npc == null) {
                continue;
            }

            PlayerSample nearest = findNearest(players, entry.center, trackRangeSq);
            int poseIndex = centerPose;
            if (nearest != null) {
                poseIndex = SlothPortraitGazeUtil.poseIndexForPlayer(
                    entry.center,
                    entry.forward,
                    entry.right,
                    nearest.position,
                    nearest.eyeHeight,
                    halfFov,
                    poseCount
                );
            }

            if (poseIndex != entry.lastPoseIndex) {
                npc.playAnimation(
                    entry.npcRef,
                    AnimationSlot.Status,
                    SlothPortraitGazeUtil.poseName(poseIndex),
                    store
                );
                entry.lastPoseIndex = poseIndex;
            }

            if (entry.nextBlinkAtMs <= 0L) {
                entry.nextBlinkAtMs = now + BLINK_MIN_MS + ThreadLocalRandom.current().nextLong(BLINK_MAX_EXTRA_MS);
            } else if (now >= entry.nextBlinkAtMs) {
                npc.playAnimation(entry.npcRef, AnimationSlot.Face, "IdlePassive", store);
                entry.nextBlinkAtMs = now + BLINK_MIN_MS + ThreadLocalRandom.current().nextLong(BLINK_MAX_EXTRA_MS);
            }
        }
    }

    private static void defer(@Nonnull World world, @Nonnull Runnable action) {
        DeferredWorldTasks.run(world, action);
    }

    private static void registerBlock(
        @Nonnull World world,
        @Nonnull Vector3i blockPos,
        @Nonnull RotationTuple rotationTuple,
        @Nonnull PortraitVariant variant,
        boolean refreshExisting
    ) {
        if (!DeferredWorldTasks.isStoreOpen(world)) {
            return;
        }
        Vector3i anchor = canonicalPortraitAnchor(world, blockPos, variant);
        com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk chunk = world.getChunk(
            ChunkUtil.indexChunkFromBlock(anchor.x, anchor.z)
        );
        int rotationIndex = chunk == null ? 0 : chunk.getRotationIndex(anchor.x, anchor.y, anchor.z);
        RotationTuple anchorRotation = RotationTuple.get(rotationIndex);
        long key = packBlockPos(anchor);
        ConcurrentHashMap<Long, PortraitEntry> portraits = BY_WORLD.computeIfAbsent(
            world.getWorldConfig().getUuid(),
            ignored -> new ConcurrentHashMap<>()
        );
        Store<EntityStore> store = world.getEntityStore().getStore();
        PortraitEntry existing = portraits.get(key);
        if (existing != null) {
            if (!refreshExisting) {
                return;
            }
            despawnNpc(store, existing);
            portraits.remove(key);
        }
        PortraitEntry entry = spawnPortrait(world, store, anchor, anchorRotation, variant);
        if (entry != null) {
            portraits.put(key, entry);
        }
    }

    private static void unregisterBlock(@Nonnull World world, @Nonnull Vector3i blockPos) {
        long key = packBlockPos(blockPos);
        ConcurrentHashMap<Long, PortraitEntry> portraits = BY_WORLD.get(world.getWorldConfig().getUuid());
        if (portraits == null) {
            return;
        }
        PortraitEntry entry = portraits.remove(key);
        if (entry == null) {
            return;
        }
        if (DeferredWorldTasks.isStoreOpen(world)) {
            despawnNpc(world.getEntityStore().getStore(), entry);
        }
    }

    @SuppressWarnings("deprecation")
    private static void scanWorldNow(@Nonnull World world) {
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
        if (chunkComponentStore == null || chunkComponentStore.isShutdown()) {
            return;
        }
        for (PortraitVariant variant : PortraitVariant.values()) {
            scanVariantBlocks(world, chunkStore, chunkComponentStore, variant);
        }
    }

    @SuppressWarnings("deprecation")
    private static void scanVariantBlocks(
        @Nonnull World world,
        @Nonnull ChunkStore chunkStore,
        @Nonnull Store<ChunkStore> chunkComponentStore,
        @Nonnull PortraitVariant variant
    ) {
        BlockType portraitType = BlockType.getAssetMap().getAsset(variant.blockId());
        if (portraitType == null) {
            return;
        }
        int portraitIndex = BlockType.getAssetMap().getIndex(variant.blockId());
        if (portraitIndex <= 0) {
            return;
        }
        Set<Long> registeredAnchors = new HashSet<>();
        LongSet chunkIndexes = chunkStore.getChunkIndexes();
        for (long chunkIndex : chunkIndexes) {
            Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
            if (chunkRef == null || !chunkRef.isValid()) {
                continue;
            }
            BlockChunk blockChunk = chunkComponentStore.getComponent(chunkRef, BlockChunk.getComponentType());
            if (blockChunk == null || !blockChunk.contains(portraitIndex)) {
                continue;
            }
            int chunkX = ChunkUtil.xOfChunkIndex(chunkIndex);
            int chunkZ = ChunkUtil.zOfChunkIndex(chunkIndex);
            for (int localX = 0; localX < 32; localX++) {
                int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkX, localX);
                for (int localZ = 0; localZ < 32; localZ++) {
                    int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkZ, localZ);
                    for (int y = 0; y < 320; y++) {
                        if (blockChunk.getBlock(worldX, y, worldZ) != portraitIndex) {
                            continue;
                        }
                        BlockSection section = blockChunk.getSectionAtBlockY(y);
                        if (section.getFiller(worldX, y, worldZ) != 0) {
                            continue;
                        }
                        Vector3i pos = new Vector3i(worldX, y, worldZ);
                        Vector3i canonical = canonicalPortraitAnchor(world, pos, variant);
                        if (!pos.equals(canonical)) {
                            continue;
                        }
                        long anchorKey = packBlockPos(canonical);
                        if (!registeredAnchors.add(anchorKey)) {
                            continue;
                        }
                        int rotationIndex = section.getRotationIndex(worldX, y, worldZ);
                        RotationTuple rotationTuple = RotationTuple.get(rotationIndex);
                        registerBlock(world, canonical, rotationTuple, variant, true);
                    }
                }
            }
        }
    }

    @Nullable
    private static PortraitEntry spawnPortrait(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3i blockPos,
        @Nonnull RotationTuple rotationTuple,
        @Nonnull PortraitVariant variant
    ) {
        BlockType blockType = BlockType.getAssetMap().getAsset(variant.blockId());
        if (blockType == null) {
            return null;
        }
        Vector3d center = new Vector3d();
        blockType.getBlockCenter(rotationTuple.index(), center);
        center.add(blockPos.x, blockPos.y, blockPos.z);

        Rotation3f rotation = SlothPortraitGazeUtil.npcRotation(rotationTuple);
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.atWarning().log("NPCPlugin unavailable; cannot spawn %s portrait at %s", variant, blockPos);
            return null;
        }
        var pair = npcPlugin.spawnNPC(
            store,
            variant.npcRoleId(),
            null,
            center,
            rotation
        );
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn %s portrait NPC at %s", variant, blockPos);
            return null;
        }
        PortraitEntry entry = new PortraitEntry();
        entry.center = center;
        entry.forward = SlothPortraitGazeUtil.outwardNormal(rotationTuple);
        entry.right = SlothPortraitGazeUtil.paintingRight(rotationTuple);
        entry.npcRef = pair.first();
        PortraitHitboxUtil.applyBlockHitbox(store, entry.npcRef, blockType, rotationTuple);
        int centerPose = SlothPortraitGazeUtil.centerPoseIndex(
            WraithBustersPlugin.get().getPluginConfig().getSlothPortraitPoseCount()
        );
        entry.lastPoseIndex = -1;
        entry.nextBlinkAtMs = 0L;
        NPCEntity npc = store.getComponent(entry.npcRef, NPCEntity.getComponentType());
        if (npc != null) {
            npc.playAnimation(entry.npcRef, AnimationSlot.Status, SlothPortraitGazeUtil.poseName(centerPose), store);
            entry.lastPoseIndex = centerPose;
        }
        return entry;
    }

    private static void despawnNpc(@Nonnull Store<EntityStore> store, @Nonnull PortraitEntry entry) {
        if (entry.npcRef != null && entry.npcRef.isValid()) {
            store.removeEntity(entry.npcRef, RemoveReason.REMOVE);
        }
        entry.npcRef = null;
    }

    @Nonnull
    private static List<PlayerSample> collectPlayers(@Nonnull Store<EntityStore> store) {
        List<PlayerSample> players = new ArrayList<>();
        Query<EntityStore> query = Query.and(Player.getComponentType(), TransformComponent.getComponentType());
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                if (transform == null) {
                    continue;
                }
                double eyeHeight = DEFAULT_EYE_HEIGHT;
                ModelComponent model = chunk.getComponent(i, ModelComponent.getComponentType());
                if (model != null) {
                    eyeHeight = SlothPortraitGazeUtil.eyeHeightForPlayer(model);
                }
                players.add(new PlayerSample(new Vector3d(transform.getPosition()), eyeHeight));
            }
        });
        return players;
    }

    @Nullable
    private static PlayerSample findNearest(
        @Nonnull List<PlayerSample> players,
        @Nonnull Vector3d center,
        double maxRangeSq
    ) {
        PlayerSample nearest = null;
        double bestSq = maxRangeSq;
        for (PlayerSample sample : players) {
            double dx = sample.position.x - center.x;
            double dy = sample.position.y - center.y;
            double dz = sample.position.z - center.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq <= bestSq) {
                bestSq = distSq;
                nearest = sample;
            }
        }
        return nearest;
    }

    @Nonnull
    private static Vector3i canonicalPortraitAnchor(
        @Nonnull World world,
        @Nonnull Vector3i blockPos,
        @Nonnull PortraitVariant variant
    ) {
        Vector3i seed = resolvePortraitAnchor(world, blockPos);
        List<Vector3i> assembly = collectPortraitAssembly(world, seed, variant);
        assembly.sort(
            Comparator.comparingInt((Vector3i block) -> block.y)
                .thenComparingInt(block -> block.x)
                .thenComparingInt(block -> block.z)
        );
        return assembly.isEmpty() ? seed : assembly.getFirst();
    }

    @Nonnull
    private static List<Vector3i> collectPortraitAssembly(
        @Nonnull World world,
        @Nonnull Vector3i start,
        @Nonnull PortraitVariant variant
    ) {
        Set<Vector3i> visited = new HashSet<>();
        ArrayDeque<Vector3i> queue = new ArrayDeque<>();
        queue.add(new Vector3i(start));
        visited.add(new Vector3i(start));
        while (!queue.isEmpty() && visited.size() < PORTRAIT_ASSEMBLY_MAX_BLOCKS) {
            Vector3i current = queue.removeFirst();
            for (Vector3i neighbor : portraitNeighbors(current)) {
                if (visited.size() >= PORTRAIT_ASSEMBLY_MAX_BLOCKS || visited.contains(neighbor)) {
                    continue;
                }
                if (!isPortraitBlock(world, neighbor, variant)) {
                    continue;
                }
                visited.add(neighbor);
                queue.add(neighbor);
            }
        }
        return new ArrayList<>(visited);
    }

    private static boolean isPortraitBlock(
        @Nonnull World world,
        @Nonnull Vector3i pos,
        @Nonnull PortraitVariant variant
    ) {
        BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
        return blockType != null && variant.blockId().equals(blockType.getId());
    }

    @Nonnull
    private static List<Vector3i> portraitNeighbors(@Nonnull Vector3i pos) {
        return List.of(
            new Vector3i(pos.x + 1, pos.y, pos.z),
            new Vector3i(pos.x - 1, pos.y, pos.z),
            new Vector3i(pos.x, pos.y + 1, pos.z),
            new Vector3i(pos.x, pos.y - 1, pos.z),
            new Vector3i(pos.x, pos.y, pos.z + 1),
            new Vector3i(pos.x, pos.y, pos.z - 1)
        );
    }

    @Nonnull
    private static Vector3i resolvePortraitAnchor(@Nonnull World world, @Nonnull Vector3i blockPos) {
        com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk chunk = world.getChunk(
            ChunkUtil.indexChunkFromBlock(blockPos.x, blockPos.z)
        );
        if (chunk == null) {
            return new Vector3i(blockPos);
        }
        int filler = chunk.getFiller(blockPos.x, blockPos.y, blockPos.z);
        if (filler == 0) {
            return new Vector3i(blockPos);
        }
        return new Vector3i(
            blockPos.x - FillerBlockUtil.unpackX(filler),
            blockPos.y - FillerBlockUtil.unpackY(filler),
            blockPos.z - FillerBlockUtil.unpackZ(filler)
        );
    }

    private static long packBlockPos(@Nonnull Vector3i pos) {
        return (long) pos.x << 40 | (long) (pos.y & 0xFFFFF) << 20 | pos.z & 0xFFFFF;
    }

    private static final class PortraitEntry {
        private Vector3d center;
        private Vector3d forward;
        private Vector3d right;
        @Nullable
        private Ref<EntityStore> npcRef;
        private int lastPoseIndex = -1;
        private long nextBlinkAtMs;
    }

    private record PlayerSample(@Nonnull Vector3d position, double eyeHeight) {}
}
