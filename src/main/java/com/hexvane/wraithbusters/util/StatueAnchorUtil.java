package com.hexvane.wraithbusters.util;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Locates the bottom anchor of a sword-statue block near arena marker coordinates. */
public final class StatueAnchorUtil {
    private static final int SEARCH_RADIUS_XZ = 4;
    private static final int SEARCH_RADIUS_Y = 4;

    private StatueAnchorUtil() {}

    @Nonnull
    public static Vector3i resolveStatueAnchor(@Nonnull World world, @Nonnull Vector3i markerOrHitPos) {
        Vector3i fillerResolved = FurnitureAnchorUtil.resolveAnchor(world, markerOrHitPos);
        if (isStatueAnchor(world, fillerResolved)) {
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
                    if (!isStatueAnchor(world, anchor)) {
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

    private static boolean isStatueAnchor(@Nonnull World world, @Nonnull Vector3i anchor) {
        if (BlockSectionQueries.getFiller(world, anchor.x, anchor.y, anchor.z) != 0) {
            return false;
        }
        BlockType blockType = BlockSectionQueries.getBlockTypeIfLoaded(world, anchor.x, anchor.y, anchor.z);
        return blockType != null && WraithBustersConstants.POSSESSABLE_STATUE_BLOCK_ID.equals(blockType.getId());
    }
}
