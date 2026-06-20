package com.hexvane.wraithbusters.util;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** World-space facing vectors for floor-standing NESW furniture. */
public final class StatueFacingUtil {
    private StatueFacingUtil() {}

    @Nonnull
    public static Vector3d blockCenter(
        @Nonnull Vector3i anchor,
        @Nonnull BlockType blockType,
        @Nonnull RotationTuple rotationTuple,
        double yOffset
    ) {
        Vector3d center = new Vector3d();
        blockType.getBlockCenter(rotationTuple.index(), center);
        center.add(anchor.x, anchor.y, anchor.z);
        center.y = anchor.y + yOffset;
        return center;
    }

    /** Unit vector pointing out of the statue face (+Z in model space, rotated by placement yaw). */
    @Nonnull
    public static Vector3d forward(@Nonnull RotationTuple rotationTuple) {
        Vector3d forward = new Vector3d(0.0, 0.0, 1.0);
        rotationTuple.yaw().rotateY(forward, forward);
        return forward.normalize();
    }

    @Nonnull
    public static Vector3d right(@Nonnull RotationTuple rotationTuple) {
        Vector3d right = new Vector3d(1.0, 0.0, 0.0);
        rotationTuple.rotatedVector(right);
        return right.normalize();
    }

    public static float yawRadians(@Nonnull Vector3d forward) {
        return (float) Math.atan2(forward.x, forward.z);
    }

    /** World-space vector for a particle-system local +X offset at the given yaw. */
    @Nonnull
    public static Vector3d localXWorld(float yawRadians, double localX) {
        return new Vector3d(Math.cos(yawRadians), 0.0, -Math.sin(yawRadians)).mul(localX);
    }
}
