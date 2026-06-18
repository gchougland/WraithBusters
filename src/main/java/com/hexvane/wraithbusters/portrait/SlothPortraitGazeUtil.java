package com.hexvane.wraithbusters.portrait;

import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Maps nearest-player position to a discrete eye pose for wall-mounted portraits. */
public final class SlothPortraitGazeUtil {
    private SlothPortraitGazeUtil() {}

    public static int centerPoseIndex(int poseCount) {
        return Math.max(0, (poseCount - 1) / 2);
    }

    @Nonnull
    public static String poseName(int poseIndex) {
        return String.format("EyePose_%02d", poseIndex);
    }

    /**
     * @param portraitCenter world-space center of the painting
     * @param forward        unit vector pointing out from the wall (painting view direction)
     * @param right          unit vector pointing to the painting's right
     * @param playerFeet     player position at feet
     * @param playerEyeHeight eye height above feet
     * @param halfFovWidth   lateral offset at which gaze reaches full left/right
     * @param poseCount      number of discrete poses (e.g. 11)
     * @return pose index in {@code [0, poseCount - 1]}
     */
    public static int poseIndexForPlayer(
        @Nonnull Vector3d portraitCenter,
        @Nonnull Vector3d forward,
        @Nonnull Vector3d right,
        @Nonnull Vector3d playerFeet,
        double playerEyeHeight,
        double halfFovWidth,
        int poseCount
    ) {
        if (poseCount <= 1) {
            return 0;
        }
        Vector3d playerEye = new Vector3d(playerFeet.x, playerFeet.y + playerEyeHeight, playerFeet.z);
        Vector3d delta = playerEye.sub(portraitCenter, new Vector3d());
        double depth = delta.dot(forward);
        Vector3d projected = new Vector3d(
            delta.x - forward.x * depth,
            delta.y - forward.y * depth,
            delta.z - forward.z * depth
        );
        double lateral = projected.dot(right);
        double safeHalf = halfFovWidth > 0.01 ? halfFovWidth : 0.01;
        double t = lateral / safeHalf + 0.5;
        t = Math.max(0.0, Math.min(1.0, t));
        return (int) Math.round(t * (poseCount - 1));
    }

    @Nonnull
    public static Vector3d outwardNormal(@Nonnull RotationTuple rotationTuple) {
        Vector3d forward = new Vector3d(0.0, 0.0, 1.0);
        rotationTuple.rotatedVector(forward);
        return forward.normalize();
    }

    @Nonnull
    public static Vector3d paintingRight(@Nonnull RotationTuple rotationTuple) {
        Vector3d right = new Vector3d(1.0, 0.0, 0.0);
        rotationTuple.rotatedVector(right);
        return right.normalize();
    }

    public static double eyeHeightForPlayer(@Nonnull ModelComponent modelComponent) {
        return modelComponent.getModel().getEyeHeight();
    }

    @Nonnull
    public static Rotation3f npcRotation(@Nonnull RotationTuple rotationTuple) {
        return new Rotation3f(0.0F, (float) rotationTuple.yaw().getRadians(), 0.0F);
    }
}
