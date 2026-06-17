package com.hexvane.wraithbusters.ghost;

import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Resolves collision-safe egress positions for phase-door teleports. */
final class PhasePortalTeleportUtil {
    /** Max distance in front of the destination portal (blocks). */
    private static final double MAX_EGRESS_NUDGE = 1.0;
    private static final double[] NUDGE_STEPS = {0.25, 0.4, 0.55, 0.7, 0.85, 1.0};
    private static final double[] TANGENT_OFFSETS = {0.0, -0.35, 0.35, -0.75, 0.75};
    /** Extra clearance beyond the other portal's trigger radius. */
    private static final double OTHER_SIDE_CLEARANCE = 0.2;

    private PhasePortalTeleportUtil() {}

    @Nonnull
    static Transform buildEgress(
        @Nonnull World world,
        @Nonnull Box playerBox,
        @Nonnull Transform destinationPortal,
        @Nonnull Vector3d midpoint,
        @Nonnull Vector3d otherPortalPos,
        double otherTriggerRadius
    ) {
        Transform egress = new Transform(destinationPortal);
        egress.getRotation().y = MathUtil.wrapAngle(egress.getRotation().y + (float) Math.PI);

        Vector3d portalPos = destinationPortal.getPosition();
        Vector3d outward = outwardFrom(midpoint, portalPos);
        Vector3d tangent = tangentFrom(outward);
        double minOtherDist = otherTriggerRadius + OTHER_SIDE_CLEARANCE;
        double minOtherDistSq = minOtherDist * minOtherDist;

        Vector3d best = null;
        double bestNudge = Double.POSITIVE_INFINITY;
        double bestTangentAbs = Double.POSITIVE_INFINITY;
        CollisionResult collision = new CollisionResult();

        for (double nudge : NUDGE_STEPS) {
            if (nudge > MAX_EGRESS_NUDGE) {
                break;
            }
            for (double tangentOffset : TANGENT_OFFSETS) {
                Vector3d candidate = new Vector3d(portalPos)
                    .fma(nudge, outward)
                    .fma(tangentOffset, tangent);
                if (!isValidSpawn(world, playerBox, candidate, collision)) {
                    continue;
                }
                double otherDistSq = distanceSq(candidate, otherPortalPos);
                if (otherDistSq < minOtherDistSq) {
                    continue;
                }
                double tangentAbs = Math.abs(tangentOffset);
                if (nudge < bestNudge || (nudge == bestNudge && tangentAbs < bestTangentAbs)) {
                    bestNudge = nudge;
                    bestTangentAbs = tangentAbs;
                    best = candidate;
                }
            }
        }

        if (best != null) {
            egress.getPosition().set(best);
            return egress;
        }

        // Last resort: portal position with flipped yaw (better than clipping through a wall).
        return egress;
    }

    private static boolean isValidSpawn(
        @Nonnull World world,
        @Nonnull Box playerBox,
        @Nonnull Vector3d position,
        @Nonnull CollisionResult collision
    ) {
        return CollisionModule.get().validatePosition(world, playerBox, position, collision) != -1;
    }

    @Nonnull
    private static Vector3d outwardFrom(@Nonnull Vector3d midpoint, @Nonnull Vector3d portalPos) {
        Vector3d outward = new Vector3d(portalPos).sub(midpoint);
        double len = outward.length();
        if (len < 1e-6) {
            return new Vector3d(0.0, 0.0, 1.0);
        }
        return outward.div(len);
    }

    @Nonnull
    private static Vector3d tangentFrom(@Nonnull Vector3d outward) {
        return new Vector3d(-outward.z, 0.0, outward.x).normalize();
    }

    private static double distanceSq(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
