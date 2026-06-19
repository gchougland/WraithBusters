package com.hexvane.wraithbusters.block;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.arena.ArenaLayout;
import com.hexvane.wraithbusters.arena.ArenaLayoutStore;
import com.hexvane.wraithbusters.util.BlockSectionQueries;
import com.hexvane.wraithbusters.util.FurnitureAnchorUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/**
 * Multi-block furniture needs filler blocks in every footprint cell so Use interactions
 * register outside the origin corner. Prefab/instance placement often skips that step.
 */
public final class ExorcismTableFillerRepairService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** 64 = force update path; 4 = skip particles; 512 = skip height update. */
    private static final int REFRESH_FILLERS_SETTINGS = 64 | 4 | 512;

    private ExorcismTableFillerRepairService() {}

    public static void repairIfNeeded(@Nonnull World world) {
        WraithBustersPlugin plugin = WraithBustersPlugin.get();
        if (plugin == null) {
            return;
        }
        ArenaLayout layout = ArenaLayoutStore.loadOrDefault(plugin, WraithBustersConstants.DEFAULT_ARENA_ID);
        repairAt(world, layout.getExorcismTable());
    }

    public static void repairAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        if (!world.isInThread()) {
            world.execute(() -> repairAt(world, blockPos));
            return;
        }
        Vector3i anchor = resolveAnchor(world, blockPos);
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(anchor.x, anchor.z));
        if (chunk == null) {
            return;
        }
        BlockType blockType = chunk.getBlockType(anchor);
        if (blockType == null || !WraithBustersConstants.EXORCISM_TABLE_BLOCK_ID.equals(blockType.getId())) {
            return;
        }
        int blockId = BlockType.getAssetMap().getIndex(blockType.getId());
        if (blockId == Integer.MIN_VALUE) {
            return;
        }
        int rotation = BlockSectionQueries.getRotationIndex(world, anchor.x, anchor.y, anchor.z);
        chunk.setBlock(anchor.x, anchor.y, anchor.z, blockId, blockType, rotation, 0, REFRESH_FILLERS_SETTINGS);
        LOGGER.atFine().log("Refreshed exorcism table filler blocks at [%d, %d, %d]", anchor.x, anchor.y, anchor.z);
    }

    @Nonnull
    public static Vector3i resolveAnchor(@Nonnull World world, @Nonnull Vector3i blockPos) {
        return FurnitureAnchorUtil.resolveAnchor(world, blockPos);
    }
}
