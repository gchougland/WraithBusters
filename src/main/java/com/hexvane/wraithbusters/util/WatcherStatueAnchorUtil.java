package com.hexvane.wraithbusters.util;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Locates the bottom anchor of a two-block-tall watcher statue near arena marker coordinates. */
public final class WatcherStatueAnchorUtil {
    private static final int SEARCH_RADIUS_XZ = 4;
    private static final int SEARCH_RADIUS_Y = 2;

    private WatcherStatueAnchorUtil() {}

    @Nonnull
    public static Vector3i resolveWatcherAnchor(@Nonnull World world, @Nonnull Vector3i markerOrHitPos) {
        Vector3i columnAnchor = findWatcherAnchorInColumn(world, markerOrHitPos);
        if (columnAnchor != null) {
            return columnAnchor;
        }

        Vector3i fillerResolved = FurnitureAnchorUtil.resolveAnchor(world, markerOrHitPos);
        if (isWatcherAnchor(world, fillerResolved)) {
            return fillerResolved;
        }

        Vector3i best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int dy = -SEARCH_RADIUS_Y; dy <= SEARCH_RADIUS_Y; dy++) {
            for (int dx = -SEARCH_RADIUS_XZ; dx <= SEARCH_RADIUS_XZ; dx++) {
                for (int dz = -SEARCH_RADIUS_XZ; dz <= SEARCH_RADIUS_XZ; dz++) {
                    Vector3i probe = new Vector3i(
                        markerOrHitPos.x + dx,
                        markerOrHitPos.y + dy,
                        markerOrHitPos.z + dz
                    );
                    Vector3i anchor = FurnitureAnchorUtil.resolveAnchor(world, probe);
                    if (!isWatcherAnchor(world, anchor)) {
                        continue;
                    }
                    int score = Math.abs(anchor.x - markerOrHitPos.x)
                        + Math.abs(anchor.y - markerOrHitPos.y)
                        + Math.abs(anchor.z - markerOrHitPos.z);
                    if (score < bestScore) {
                        bestScore = score;
                        best = anchor;
                    }
                }
            }
        }
        return best != null ? best : fillerResolved;
    }

    /** Prefer the bottom anchor when the marker sits on an upper column cell or nearby air. */
    @Nullable
    private static Vector3i findWatcherAnchorInColumn(@Nonnull World world, @Nonnull Vector3i ref) {
        Vector3i best = null;
        for (int dy = -SEARCH_RADIUS_Y; dy <= SEARCH_RADIUS_Y; dy++) {
            Vector3i probe = new Vector3i(ref.x, ref.y + dy, ref.z);
            Vector3i anchor = FurnitureAnchorUtil.resolveAnchor(world, probe);
            if (!isWatcherAnchor(world, anchor)) {
                continue;
            }
            if (best == null || anchor.y < best.y) {
                best = anchor;
            }
        }
        return best;
    }

    private static boolean isWatcherAnchor(@Nonnull World world, @Nonnull Vector3i anchor) {
        if (BlockSectionQueries.getFiller(world, anchor.x, anchor.y, anchor.z) != 0) {
            return false;
        }
        BlockType blockType = BlockSectionQueries.getBlockTypeIfLoaded(world, anchor.x, anchor.y, anchor.z);
        return blockType != null && isWatcherStatueBlock(blockType);
    }

    private static boolean isWatcherStatueBlock(@Nonnull BlockType blockType) {
        String id = blockType.getId();
        String base = WraithBustersConstants.POSSESSABLE_WATCHER_STATUE_BLOCK_ID;
        return id.equals(base) || id.startsWith(base + "|");
    }
}
