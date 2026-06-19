package com.hexvane.wraithbusters.util;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Resolves multi-block furniture cells back to their placement anchor. */
public final class FurnitureAnchorUtil {
    private FurnitureAnchorUtil() {}

    @Nonnull
    public static Vector3i resolveAnchor(@Nonnull World world, @Nonnull Vector3i blockPos) {
        int filler = BlockSectionQueries.getFiller(world, blockPos.x, blockPos.y, blockPos.z);
        if (filler == 0) {
            return new Vector3i(blockPos);
        }
        return new Vector3i(
            blockPos.x - FillerBlockUtil.unpackX(filler),
            blockPos.y - FillerBlockUtil.unpackY(filler),
            blockPos.z - FillerBlockUtil.unpackZ(filler)
        );
    }
}
