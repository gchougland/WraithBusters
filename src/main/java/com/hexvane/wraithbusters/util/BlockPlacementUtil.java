package com.hexvane.wraithbusters.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.HashUtil;
import com.hypixel.hytale.protocol.RandomRotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class BlockPlacementUtil {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** 64 = force update path; 4 = skip particles; 512 = skip height update. */
    private static final int PLACE_SETTINGS = SetBlockSettings.FORCE_CHANGED | SetBlockSettings.NO_SEND_PARTICLES | SetBlockSettings.NO_UPDATE_HEIGHTMAP;
    /** Matches {@code ChangeStateInteraction} / candle toggles: refresh block + model state on clients. */
    private static final int STATE_CHANGE_SETTINGS =
        SetBlockSettings.PERFORM_BLOCK_UPDATE | SetBlockSettings.NO_SEND_PARTICLES | SetBlockSettings.FORCE_CHANGED;

    private BlockPlacementUtil() {}

    public static boolean placeBlock(@Nonnull World world, @Nonnull Vector3i blockPos, @Nonnull String blockId) {
        if (!world.isInThread()) {
            world.execute(() -> placeBlock(world, blockPos, blockId));
            return true;
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) {
            LOGGER.atWarning().log("Unknown block id for placement: %s", blockId);
            return false;
        }
        int assetIndex = BlockType.getAssetMap().getIndex(blockId);
        if (assetIndex == Integer.MIN_VALUE) {
            return false;
        }
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(blockPos.x, blockPos.z));
        if (chunk == null) {
            LOGGER.atWarning().log("Chunk not loaded for block placement at [%d, %d, %d]", blockPos.x, blockPos.y, blockPos.z);
            return false;
        }
        int rotation = resolvePlacementRotation(blockType, blockPos);
        chunk.setBlock(blockPos.x, blockPos.y, blockPos.z, assetIndex, blockType, rotation, 0, PLACE_SETTINGS);
        return true;
    }

    /**
     * Blocks with {@code RandomRotation} in item JSON (e.g. rubble) get a position-hash yaw when placed
     * programmatically, matching {@code PrefabFarmingStageData} / vanilla scatter props.
     */
    private static int resolvePlacementRotation(@Nonnull BlockType blockType, @Nonnull Vector3i blockPos) {
        RandomRotation mode = blockType.getRandomRotation();
        if (mode == null || mode == RandomRotation.None) {
            return 0;
        }
        int yawIndex = HashUtil.randomInt(blockPos.x, blockPos.y, blockPos.z, Rotation.VALUES.length);
        return RotationTuple.of(Rotation.VALUES[yawIndex], Rotation.None).index();
    }

    public static void removeBlock(@Nonnull World world, @Nonnull Vector3i blockPos) {
        if (!world.isInThread()) {
            world.execute(() -> removeBlock(world, blockPos));
            return;
        }
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(blockPos.x, blockPos.z));
        if (chunk == null) {
            return;
        }
        chunk.breakBlock(blockPos.x, blockPos.y, blockPos.z, PLACE_SETTINGS);
    }

    public static boolean setBlockState(
        @Nonnull World world,
        @Nonnull Vector3i blockPos,
        @Nonnull String stateName
    ) {
        if (!world.isInThread()) {
            world.execute(() -> setBlockState(world, blockPos, stateName));
            return true;
        }
        Vector3i anchor = FurnitureAnchorUtil.resolveAnchor(world, blockPos);
        BlockType current = world.getBlockType(anchor.x, anchor.y, anchor.z);
        if (current == null) {
            return false;
        }
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(anchor.x, anchor.z));
        if (chunk == null) {
            LOGGER.atWarning().log(
                "Chunk not loaded for block state at [%d, %d, %d]",
                anchor.x,
                anchor.y,
                anchor.z
            );
            return false;
        }

        BlockType targetType = current.getBlockForState(stateName);
        if (targetType == null) {
            LOGGER.atWarning().log("No block state %s on %s", stateName, current.getId());
            return false;
        }
        int targetId = BlockType.getAssetMap().getIndex(targetType.getId());
        if (targetId == Integer.MIN_VALUE) {
            return false;
        }

        int rotation = BlockSectionQueries.getRotationIndex(world, anchor.x, anchor.y, anchor.z);
        chunk.setBlock(anchor.x, anchor.y, anchor.z, targetId, targetType, rotation, 0, STATE_CHANGE_SETTINGS);
        refreshBlockHitbox(world, anchor, targetType, rotation);
        return true;
    }

    private static void refreshBlockHitbox(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        @Nonnull BlockType blockType,
        int rotationIndex
    ) {
        BlockBoundingBoxes hitbox = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        if (hitbox == null) {
            world.performBlockUpdate(anchor.x, anchor.y, anchor.z, false);
            return;
        }
        FillerBlockUtil.forEachFillerBlock(
            hitbox.get(rotationIndex),
            (x, y, z) -> world.performBlockUpdate(anchor.x + x, anchor.y + y, anchor.z + z, false)
        );
    }

    @Nullable
    public static String blockIdAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        BlockType blockType = world.getBlockType(blockPos.x, blockPos.y, blockPos.z);
        return blockType == null ? null : blockType.getId();
    }
}
