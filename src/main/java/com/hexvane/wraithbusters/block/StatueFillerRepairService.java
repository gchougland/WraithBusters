package com.hexvane.wraithbusters.block;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.arena.ArenaLayout;
import com.hexvane.wraithbusters.arena.ArenaLayoutStore;
import com.hexvane.wraithbusters.arena.PossessableMarker;
import com.hexvane.wraithbusters.util.BlockSectionQueries;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hexvane.wraithbusters.util.StatueAnchorUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/**
 * Statue_Full blocks are three tall; prefab placement often omits filler metadata on upper cells,
 * which breaks block states (animations) and interactions outside the anchor corner.
 */
public final class StatueFillerRepairService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** 64 = force update path; 4 = skip particles; 512 = skip height update. */
    private static final int REFRESH_FILLERS_SETTINGS = 64 | 4 | 512;

    private StatueFillerRepairService() {}

    public static void repairIfNeeded(@Nonnull World world) {
        if (!world.isInThread()) {
            DeferredWorldTasks.run(world, () -> repairIfNeeded(world));
            return;
        }
        WraithBustersPlugin plugin = WraithBustersPlugin.get();
        if (plugin == null) {
            return;
        }
        ArenaLayout layout = ArenaLayoutStore.loadOrDefault(plugin, WraithBustersConstants.DEFAULT_ARENA_ID);
        repairForLayout(world, layout);
    }

    public static void repairForLayout(@Nonnull World world, @Nonnull ArenaLayout layout) {
        if (!world.isInThread()) {
            DeferredWorldTasks.run(world, () -> repairForLayout(world, layout));
            return;
        }
        Set<Long> repaired = new HashSet<>();
        for (PossessableMarker marker : layout.getPossessables()) {
            if (!"statue".equals(marker.getTypeId())) {
                continue;
            }
            Vector3i anchor = StatueAnchorUtil.resolveStatueAnchor(world, marker.getBlockPos());
            long key = pack(anchor);
            if (!repaired.add(key)) {
                continue;
            }
            repairAt(world, anchor);
        }
    }

    public static void repairAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        if (!world.isInThread()) {
            DeferredWorldTasks.run(world, () -> repairAt(world, blockPos));
            return;
        }
        Vector3i anchor = StatueAnchorUtil.resolveStatueAnchor(world, blockPos);
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(anchor.x, anchor.z));
        if (chunk == null) {
            return;
        }
        BlockType blockType = chunk.getBlockType(anchor);
        if (blockType == null || !WraithBustersConstants.POSSESSABLE_STATUE_BLOCK_ID.equals(blockType.getId())) {
            LOGGER.atWarning().log(
                "Sword statue filler repair skipped: no %s anchor near [%d, %d, %d] (marker [%d, %d, %d])",
                WraithBustersConstants.POSSESSABLE_STATUE_BLOCK_ID,
                anchor.x,
                anchor.y,
                anchor.z,
                blockPos.x,
                blockPos.y,
                blockPos.z
            );
            return;
        }
        int blockId = BlockType.getAssetMap().getIndex(blockType.getId());
        if (blockId == Integer.MIN_VALUE) {
            return;
        }
        int rotation = BlockSectionQueries.getRotationIndex(world, anchor.x, anchor.y, anchor.z);
        chunk.setBlock(anchor.x, anchor.y, anchor.z, blockId, blockType, rotation, 0, REFRESH_FILLERS_SETTINGS);
        LOGGER.atFine().log("Refreshed sword-statue filler blocks at [%d, %d, %d]", anchor.x, anchor.y, anchor.z);
    }

    private static long pack(@Nonnull Vector3i pos) {
        return ((long) pos.x << 40) | ((long) pos.y << 20) | (pos.z & 0xFFFFFL);
    }
}
