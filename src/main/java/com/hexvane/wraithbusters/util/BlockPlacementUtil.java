package com.hexvane.wraithbusters.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class BlockPlacementUtil {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** 64 = force update path; 4 = skip particles; 512 = skip height update. */
    private static final int PLACE_SETTINGS = 64 | 4 | 512;

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
        int rotation = BlockSectionQueries.getRotationIndex(world, blockPos.x, blockPos.y, blockPos.z);
        chunk.setBlock(blockPos.x, blockPos.y, blockPos.z, assetIndex, blockType, rotation, 0, PLACE_SETTINGS);
        return true;
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
        BlockType blockType = world.getBlockType(blockPos.x, blockPos.y, blockPos.z);
        if (blockType == null) {
            return false;
        }
        if (blockType.getBlockForState(stateName) == null && !"default".equals(stateName)) {
            return false;
        }
        world.setBlockInteractionState(blockPos, blockType, stateName);
        return true;
    }

    @Nullable
    public static String blockIdAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        BlockType blockType = world.getBlockType(blockPos.x, blockPos.y, blockPos.z);
        return blockType == null ? null : blockType.getId();
    }
}
