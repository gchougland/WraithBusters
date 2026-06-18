package com.hexvane.wraithbusters.setup;

import com.hexvane.wraithbusters.arena.GhostPhaseDoorMarker;
import com.hexvane.wraithbusters.arena.PhaseDoorSize;
import com.hexvane.wraithbusters.door.DoorStateHelper;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class PhaseDoorAnalyzer {
    private static final String LOCKED_DOOR_PREFIX = "WraithBusters_Locked_Door";
    private static final int NEARBY_SEARCH_RADIUS = 3;

    /*
     * ═══════════════════════════════════════════════════════════════════════════
     *  PORTAL PLACEMENT TUNING — edit the constants below
     * ═══════════════════════════════════════════════════════════════════════════
     *
     *  Height (Y):
     *    NPC spawn Y = door floor block (doorFloorY) — entity feet at the opening floor.
     *    Particle PositionOffset.Y = portalEntityHeight/2 (generate_portal_particles.py) so the
     *    effect bottom sits on the entity origin. Do NOT also lift spawn Y in Java.
     *
     *  Side position (X/Z):
     *    Portals sit at door center ± SIDE_OFFSET along the thin footprint axis (through-axis).
     *    Wide-in-X doors use ±Z; wide-in-Z doors use ±X. 1x2 uses block facing (square footprint).
     *
     *  Portal effect rotation (particle/NPC yaw):
     *    Same through-axis yaw as side offset (never block facing on non-square footprints).
     *
     *  Particle scale (visual size, not position):
     *    scripts/generate_portal_particles.py — spawner mults per tier; model Scale = MODEL_WORLD_SCALE.
     *
     *  Re-place doors with the Phase Door Tool after changing values (saved arena JSON
     *  keeps old transforms until you re-place or edit ghostPhaseDoors in the layout file).
     * ═══════════════════════════════════════════════════════════════════════════
     */

    /** Distance from door center to each portal on the ground plane. */
    private static final double SIDE_OFFSET = 0.6;

    /** Bottom block Y of the door opening. */
    private static int doorFloorY(@Nonnull DoorTier tier, @Nonnull Bounds bounds) {
        int height = tierHeightBlocks(tier);
        int ySpan = bounds.maxY() - bounds.minY() + 1;
        if (ySpan >= height) {
            return bounds.minY();
        }
        return switch (tier) {
            case STANDARD -> bounds.minY();
            case MEDIUM, LARGE -> bounds.maxY() - height + 1;
        };
    }

    private static double portalCenterY(@Nonnull DoorTier tier, @Nonnull Bounds bounds) {
        return doorFloorY(tier, bounds);
    }

    private PhaseDoorAnalyzer() {}

    public enum DoorTier {
        STANDARD,
        MEDIUM,
        LARGE
    }

    public record AnalysisResult(
        @Nonnull PhaseDoorSize doorSize,
        @Nonnull List<Vector3i> doorBlocks,
        @Nonnull Transform sideA,
        @Nonnull Transform sideB
    ) {}

    /**
     * Resolves a locked-door block near the raycast / target hint. The direct hit cell is often
     * empty or a wall when a door is rotated, so we search nearby for the actual door block.
     */
    @Nullable
    public static Vector3i resolveSeedBlock(@Nonnull World world, @Nonnull Vector3i hint) {
        if (isLockedDoor(world.getBlockType(hint.x, hint.y, hint.z))) {
            return new Vector3i(hint);
        }
        Vector3i best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int dx = -NEARBY_SEARCH_RADIUS; dx <= NEARBY_SEARCH_RADIUS; dx++) {
            for (int dy = -NEARBY_SEARCH_RADIUS; dy <= NEARBY_SEARCH_RADIUS; dy++) {
                for (int dz = -NEARBY_SEARCH_RADIUS; dz <= NEARBY_SEARCH_RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Vector3i candidate = new Vector3i(hint.x + dx, hint.y + dy, hint.z + dz);
                    if (!isLockedDoor(world.getBlockType(candidate.x, candidate.y, candidate.z))) {
                        continue;
                    }
                    double distSq = dx * (double) dx + dy * (double) dy + dz * (double) dz;
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = candidate;
                    }
                }
            }
        }
        return best == null ? null : new Vector3i(best);
    }

    /** All locked-door blocks in the connected assembly (both halves of 2x2 / 4x4 doors). */
    @Nullable
    public static List<Vector3i> collectLockedDoorAssembly(
        @Nonnull World world,
        @Nonnull Vector3i clickedBlock
    ) {
        Vector3i seed = resolveSeedBlock(world, clickedBlock);
        if (seed == null) {
            return null;
        }
        BlockType seedType = world.getBlockType(seed.x, seed.y, seed.z);
        if (seedType == null) {
            return null;
        }
        DoorTier tier = tierFor(seedType);
        return copyBlocks(collectAssembly(world, seed, tier));
    }

    @Nullable
    public static AnalysisResult analyze(@Nonnull World world, @Nonnull Vector3i clickedBlock) {
        Vector3i seed = resolveSeedBlock(world, clickedBlock);
        if (seed == null) {
            return null;
        }
        BlockType seedType = world.getBlockType(seed.x, seed.y, seed.z);
        if (seedType == null) {
            return null;
        }
        DoorTier tier = tierFor(seedType);
        List<Vector3i> doorBlocks = copyBlocks(collectAssembly(world, seed, tier));
        if (doorBlocks.isEmpty()) {
            return null;
        }
        return buildAnalysis(world, doorBlocks, tier);
    }

    /**
     * Computes portal transforms from a fixed door-block list (no BFS re-expansion).
     * Use this when door interaction state may differ from setup (e.g. open vs blocked).
     */
    @Nullable
    public static AnalysisResult analyzeFromDoorBlocks(
        @Nonnull World world,
        @Nonnull List<Vector3i> doorBlocks
    ) {
        if (doorBlocks.isEmpty()) {
            return null;
        }
        return buildAnalysis(world, doorBlocks, null);
    }

    @Nullable
    private static AnalysisResult buildAnalysis(
        @Nonnull World world,
        @Nonnull List<Vector3i> doorBlocks,
        @Nullable DoorTier knownTier
    ) {
        List<Vector3i> sorted = copyBlocks(doorBlocks);
        sorted.sort(Comparator.comparingInt((Vector3i block) -> block.y)
            .thenComparingInt(block -> block.x)
            .thenComparingInt(block -> block.z));

        DoorTier tier = knownTier;
        if (tier == null) {
            for (Vector3i block : sorted) {
                Vector3i anchor = DoorStateHelper.resolveDoorAnchor(world, block);
                BlockType type = world.getBlockType(anchor.x, anchor.y, anchor.z);
                if (isLockedDoor(type)) {
                    tier = tierFor(type);
                    break;
                }
            }
        }
        if (tier == null) {
            return null;
        }

        Bounds bounds = Bounds.boundsOf(sorted);
        PhaseDoorSize size = classifySize(tier, bounds);
        Vector3i rotationBlock = DoorStateHelper.resolveDoorAnchor(world, sorted.getFirst());
        float throughYaw = resolveThroughYaw(world, rotationBlock, bounds);
        Transform sideA = sideTransform(bounds, tier, throughYaw, true);
        Transform sideB = sideTransform(bounds, tier, throughYaw, false);
        return new AnalysisResult(size, sorted, sideA, sideB);
    }

    public static void applyToMarker(
        @Nonnull GhostPhaseDoorMarker marker,
        @Nonnull AnalysisResult analysis
    ) {
        marker.setDoorSize(analysis.doorSize());
        marker.setDoorBlocks(copyBlocks(analysis.doorBlocks()));
        marker.setEntry(new Transform(analysis.sideA()));
        marker.setExit(new Transform(analysis.sideB()));
    }

    @Nonnull
    private static List<Vector3i> copyBlocks(@Nonnull List<Vector3i> blocks) {
        List<Vector3i> copy = new ArrayList<>(blocks.size());
        for (Vector3i block : blocks) {
            copy.add(new Vector3i(block));
        }
        return copy;
    }

    private static boolean isLockedDoor(@Nullable BlockType blockType) {
        if (blockType == null) {
            return false;
        }
        String id = blockType.getId();
        return id != null && id.startsWith(LOCKED_DOOR_PREFIX);
    }

    public static boolean isLockedDoorAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        return isLockedDoor(world.getBlockType(blockPos.x, blockPos.y, blockPos.z));
    }

    /**
     * Recomputes entry/exit transforms from live door blocks (in-memory only).
     *
     * @return false when door blocks are missing or no locked door remains at the saved positions
     */
    public static boolean refreshMarkerFromWorld(@Nonnull World world, @Nonnull GhostPhaseDoorMarker marker) {
        List<Vector3i> blocks = marker.getDoorBlocks();
        if (blocks.isEmpty()) {
            return false;
        }
        boolean anyDoor = false;
        for (Vector3i block : blocks) {
            Vector3i anchor = DoorStateHelper.resolveDoorAnchor(world, block);
            if (isLockedDoorAt(world, anchor)) {
                anyDoor = true;
                break;
            }
        }
        if (!anyDoor) {
            return false;
        }
        AnalysisResult analysis = analyzeFromDoorBlocks(world, blocks);
        if (analysis == null) {
            return false;
        }
        applyToMarker(marker, analysis);
        return true;
    }

    @Nonnull
    private static DoorTier tierFor(@Nonnull BlockType blockType) {
        String id = blockType.getId();
        if (id.contains("_Large")) {
            return DoorTier.LARGE;
        }
        if (id.contains("_Medium")) {
            return DoorTier.MEDIUM;
        }
        return DoorTier.STANDARD;
    }

    private static boolean sameTier(@Nonnull BlockType a, @Nonnull DoorTier tier) {
        return tierFor(a) == tier;
    }

    private static int maxAssemblyBlocks(@Nonnull DoorTier tier) {
        return switch (tier) {
            case STANDARD -> 4;
            case MEDIUM -> 9;
            case LARGE -> 16;
        };
    }

    @Nonnull
    private static List<Vector3i> collectAssembly(
        @Nonnull World world,
        @Nonnull Vector3i start,
        @Nonnull DoorTier tier
    ) {
        int maxBlocks = maxAssemblyBlocks(tier);
        Set<Vector3i> visited = new HashSet<>();
        ArrayDeque<Vector3i> queue = new ArrayDeque<>();
        queue.add(new Vector3i(start));
        visited.add(new Vector3i(start));

        while (!queue.isEmpty() && visited.size() < maxBlocks) {
            Vector3i current = queue.removeFirst();
            for (Vector3i neighbor : neighbors(current)) {
                if (visited.size() >= maxBlocks) {
                    break;
                }
                if (visited.contains(neighbor)) {
                    continue;
                }
                BlockType type = world.getBlockType(neighbor.x, neighbor.y, neighbor.z);
                if (!isLockedDoor(type) || !sameTier(type, tier)) {
                    continue;
                }
                visited.add(neighbor);
                queue.add(neighbor);
            }
        }

        expandVerticalColumns(world, tier, visited);

        // Horizontal halves (2x2 / 4x4) may only connect after vertical cells are merged.
        queue.addAll(visited);
        while (!queue.isEmpty() && visited.size() < maxBlocks) {
            Vector3i current = queue.removeFirst();
            for (Vector3i neighbor : neighbors(current)) {
                if (visited.size() >= maxBlocks) {
                    break;
                }
                if (visited.contains(neighbor)) {
                    continue;
                }
                BlockType type = world.getBlockType(neighbor.x, neighbor.y, neighbor.z);
                if (!isLockedDoor(type) || !sameTier(type, tier)) {
                    continue;
                }
                visited.add(neighbor);
                queue.add(neighbor);
            }
        }

        return new ArrayList<>(visited);
    }

    /** Ensures click position does not change which vertical cells belong to the assembly. */
    private static void expandVerticalColumns(
        @Nonnull World world,
        @Nonnull DoorTier tier,
        @Nonnull Set<Vector3i> blocks
    ) {
        if (blocks.isEmpty()) {
            return;
        }
        int height = tierHeightBlocks(tier);
        List<Vector3i> columns = uniqueColumns(blocks);
        for (Vector3i column : columns) {
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (Vector3i block : blocks) {
                if (block.x != column.x || block.z != column.z) {
                    continue;
                }
                minY = Math.min(minY, block.y);
                maxY = Math.max(maxY, block.y);
            }
            int scanMin = minY - height + 1;
            int scanMax = maxY + height - 1;
            for (int y = scanMin; y <= scanMax; y++) {
                Vector3i pos = new Vector3i(column.x, y, column.z);
                if (blocks.contains(pos)) {
                    continue;
                }
                BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
                if (isLockedDoor(type) && sameTier(type, tier)) {
                    blocks.add(pos);
                }
            }
        }
    }

    @Nonnull
    private static List<Vector3i> uniqueColumns(@Nonnull Set<Vector3i> blocks) {
        Set<Long> seen = new HashSet<>();
        List<Vector3i> columns = new ArrayList<>();
        for (Vector3i block : blocks) {
            long key = (((long) block.x) << 32) | (block.z & 0xffffffffL);
            if (seen.add(key)) {
                columns.add(new Vector3i(block.x, 0, block.z));
            }
        }
        return columns;
    }

    @Nonnull
    private static List<Vector3i> neighbors(@Nonnull Vector3i pos) {
        return List.of(
            new Vector3i(pos.x + 1, pos.y, pos.z),
            new Vector3i(pos.x - 1, pos.y, pos.z),
            new Vector3i(pos.x, pos.y + 1, pos.z),
            new Vector3i(pos.x, pos.y - 1, pos.z),
            new Vector3i(pos.x, pos.y, pos.z + 1),
            new Vector3i(pos.x, pos.y, pos.z - 1)
        );
    }

    @Nonnull
    private static PhaseDoorSize classifySize(@Nonnull DoorTier tier, @Nonnull Bounds bounds) {
        int width = Math.max(bounds.xSpan(), bounds.zSpan());
        return switch (tier) {
            case STANDARD -> width >= 2 ? PhaseDoorSize.STANDARD_2x2 : PhaseDoorSize.STANDARD_1x2;
            case MEDIUM -> PhaseDoorSize.MEDIUM_3x3;
            case LARGE -> PhaseDoorSize.LARGE_4x4;
        };
    }

    private static float blockFacingYaw(@Nonnull World world, @Nonnull Vector3i rotationBlock) {
        int rotationIndex = world.getBlockRotationIndex(
            rotationBlock.x,
            rotationBlock.y,
            rotationBlock.z
        );
        return (float) RotationTuple.get(rotationIndex).yaw().getRadians();
    }

    /**
     * Yaw of the axis ghosts walk through when phasing. Single-column (1x2) doors use block facing
     * directly. Wider assemblies always pass through the thin footprint axis (±Z when wide in X,
     * ±X when wide in Z); block rotation only picks the sign on that axis.
     */
    private static float resolveThroughYaw(
        @Nonnull World world,
        @Nonnull Vector3i rotationBlock,
        @Nonnull Bounds bounds
    ) {
        float blockYaw = blockFacingYaw(world, rotationBlock);
        if (bounds.xSpan() == bounds.zSpan()) {
            return blockYaw;
        }
        if (bounds.xSpan() > bounds.zSpan()) {
            return throughYawOnZAxis(blockYaw);
        }
        return throughYawOnXAxis(blockYaw);
    }

    /** Through-axis ±Z (portal normals face along Z). */
    private static float throughYawOnZAxis(float blockYaw) {
        float cos = (float) Math.cos(blockYaw);
        if (Math.abs(cos) >= Math.abs((float) Math.sin(blockYaw))) {
            return cos >= 0.0f ? 0.0f : (float) Math.PI;
        }
        return 0.0f;
    }

    /** Through-axis ±X (portal normals face along X). */
    private static float throughYawOnXAxis(float blockYaw) {
        float sin = (float) Math.sin(blockYaw);
        if (Math.abs(sin) >= Math.abs((float) Math.cos(blockYaw))) {
            return sin >= 0.0f ? (float) (Math.PI / 2.0) : (float) (-Math.PI / 2.0);
        }
        return (float) (Math.PI / 2.0);
    }

    private static int tierHeightBlocks(@Nonnull DoorTier tier) {
        return switch (tier) {
            case STANDARD -> 2;
            case MEDIUM -> 3;
            case LARGE -> 4;
        };
    }

    @Nonnull
    private static Transform sideTransform(
        @Nonnull Bounds bounds,
        @Nonnull DoorTier tier,
        float throughYaw,
        boolean sideA
    ) {
        double centerX = bounds.centerX();
        double centerZ = bounds.centerZ();
        double portalY = portalCenterY(tier, bounds);
        double normalX = Math.sin(throughYaw);
        double normalZ = Math.cos(throughYaw);
        double sign = sideA ? 1.0 : -1.0;
        double x = centerX + normalX * SIDE_OFFSET * sign;
        double z = centerZ + normalZ * SIDE_OFFSET * sign;
        float yaw = MathUtil.wrapAngle(throughYaw + (sideA ? 0.0F : (float) Math.PI));
        return new Transform(x, portalY, z, 0.0F, yaw, 0.0F);
    }

    private static final class Bounds {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        @Nonnull
        static Bounds boundsOf(@Nonnull List<Vector3i> blocks) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (Vector3i block : blocks) {
                minX = Math.min(minX, block.x);
                minY = Math.min(minY, block.y);
                minZ = Math.min(minZ, block.z);
                maxX = Math.max(maxX, block.x);
                maxY = Math.max(maxY, block.y);
                maxZ = Math.max(maxZ, block.z);
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        int xSpan() {
            return maxX - minX + 1;
        }

        int zSpan() {
            return maxZ - minZ + 1;
        }

        int maxY() {
            return maxY;
        }

        int minY() {
            return minY;
        }

        double centerX() {
            return minX + xSpan() / 2.0;
        }

        double centerZ() {
            return minZ + zSpan() / 2.0;
        }
    }
}
