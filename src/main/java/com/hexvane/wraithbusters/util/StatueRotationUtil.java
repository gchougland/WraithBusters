package com.hexvane.wraithbusters.util;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Resolves NESW furniture yaw from block-type variants, chunk data, or hitbox cells. */
public final class StatueRotationUtil {
    private StatueRotationUtil() {}

    /**
     * @param blockPos block the player interacted with (may be a filler cell)
     * @param anchor   bottom non-filler anchor of the statue
     */
    @Nonnull
    public static RotationTuple resolve(
        @Nonnull World world,
        @Nonnull Vector3i blockPos,
        @Nonnull Vector3i anchor,
        @Nonnull BlockType blockType
    ) {
        RotationTuple fromBlockId = rotationTupleFromBlockId(blockType.getId());
        if (fromBlockId != null) {
            return fromBlockId;
        }

        RotationTuple fromColumn = rotationTupleFromColumnBlockIds(world, blockPos, anchor, blockType.getId());
        if (fromColumn != null) {
            return fromColumn;
        }

        int rotationIndex = readRotationIndex(world, anchor);
        if (rotationIndex == 0) {
            rotationIndex = readRotationInColumn(world, blockPos.x, blockPos.z, blockPos.y, 2);
        }
        if (rotationIndex == 0 && !blockPos.equals(anchor)) {
            rotationIndex = readRotationInColumn(world, anchor.x, anchor.z, anchor.y, 2);
        }
        if (rotationIndex == 0 && !blockPos.equals(anchor)) {
            rotationIndex = readRotationIndex(world, blockPos);
        }
        if (rotationIndex == 0) {
            rotationIndex = scanHitboxForRotation(world, anchor, blockType);
        }
        return RotationTuple.get(rotationIndex);
    }

    /** Same as {@link #resolve} but only needs the anchor when blockPos is unknown. */
    @Nonnull
    public static RotationTuple resolve(@Nonnull World world, @Nonnull Vector3i anchor, @Nonnull BlockType blockType) {
        return resolve(world, anchor, anchor, blockType);
    }

    public static int readRotationIndex(@Nonnull World world, @Nonnull Vector3i pos) {
        return BlockSectionQueries.getRotationIndex(world, pos.x, pos.y, pos.z);
    }

    /**
     * Reads placement yaw from any cell in a vertical column. Arena markers for two-block-tall
     * furniture are often on the upper filler while the anchor below holds the rotation index.
     */
    public static int readRotationInColumn(
        @Nonnull World world,
        int x,
        int z,
        int centerY,
        int yRadius
    ) {
        for (int dy = -yRadius; dy <= yRadius; dy++) {
            Vector3i probe = new Vector3i(x, centerY + dy, z);
            int rotation = readRotationIndex(world, probe);
            if (rotation != 0) {
                return rotation;
            }
            Vector3i resolved = FurnitureAnchorUtil.resolveAnchor(world, probe);
            if (!probe.equals(resolved)) {
                rotation = readRotationIndex(world, resolved);
                if (rotation != 0) {
                    return rotation;
                }
            }
        }
        return 0;
    }

    @Nullable
    private static RotationTuple rotationTupleFromColumnBlockIds(
        @Nonnull World world,
        @Nonnull Vector3i blockPos,
        @Nonnull Vector3i anchor,
        @Nonnull String blockIdBase
    ) {
        RotationTuple fromAnchor = rotationTupleFromWatcherBlock(world, anchor, blockIdBase);
        if (fromAnchor != null) {
            return fromAnchor;
        }
        RotationTuple fromMarker = rotationTupleFromWatcherBlock(world, blockPos, blockIdBase);
        if (fromMarker != null) {
            return fromMarker;
        }
        for (int dy = -2; dy <= 2; dy++) {
            Vector3i probe = new Vector3i(blockPos.x, blockPos.y + dy, blockPos.z);
            RotationTuple fromProbe = rotationTupleFromWatcherBlock(world, probe, blockIdBase);
            if (fromProbe != null) {
                return fromProbe;
            }
            Vector3i resolved = FurnitureAnchorUtil.resolveAnchor(world, probe);
            if (!probe.equals(resolved)) {
                fromProbe = rotationTupleFromWatcherBlock(world, resolved, blockIdBase);
                if (fromProbe != null) {
                    return fromProbe;
                }
            }
        }
        return null;
    }

    @Nullable
    private static RotationTuple rotationTupleFromWatcherBlock(
        @Nonnull World world,
        @Nonnull Vector3i pos,
        @Nonnull String blockIdBase
    ) {
        BlockType blockType = readBlockType(world, pos);
        if (blockType == null || !matchesBlockIdBase(blockType.getId(), blockIdBase)) {
            return null;
        }
        RotationTuple fromId = rotationTupleFromBlockId(blockType.getId());
        if (fromId != null) {
            return fromId;
        }
        int rotationIndex = readRotationIndex(world, pos);
        return rotationIndex == 0 ? null : RotationTuple.get(rotationIndex);
    }

    @Nullable
    private static BlockType readBlockType(@Nonnull World world, @Nonnull Vector3i pos) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk != null) {
            return chunk.getBlockType(pos);
        }
        return BlockSectionQueries.getBlockTypeIfLoaded(world, pos.x, pos.y, pos.z);
    }

    private static boolean matchesBlockIdBase(@Nonnull String blockId, @Nonnull String blockIdBase) {
        return blockId.equals(blockIdBase) || blockId.startsWith(blockIdBase + "|");
    }

    private static int scanHitboxForRotation(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        @Nonnull BlockType blockType
    ) {
        BlockBoundingBoxes hitboxAsset = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        if (hitboxAsset == null) {
            return 0;
        }
        int[] best = {0};
        for (int variant = 0; variant < 4; variant++) {
            BlockBoundingBoxes.RotatedVariantBoxes hitbox = hitboxAsset.get(variant);
            if (hitbox == null) {
                continue;
            }
            FillerBlockUtil.forEachFillerBlock(hitbox, (dx, dy, dz) -> {
                int rotationIndex = readRotationIndex(
                    world,
                    new Vector3i(anchor.x + dx, anchor.y + dy, anchor.z + dz)
                );
                if (rotationIndex != 0) {
                    best[0] = rotationIndex;
                }
            });
            if (best[0] != 0) {
                break;
            }
        }
        return best[0];
    }

    @Nullable
    public static RotationTuple rotationTupleFromBlockId(@Nonnull String blockId) {
        int yawStart = blockId.indexOf("|Yaw=");
        if (yawStart < 0) {
            return null;
        }
        yawStart += "|Yaw=".length();
        int yawEnd = blockId.indexOf('|', yawStart);
        if (yawEnd < 0) {
            yawEnd = blockId.length();
        }
        try {
            int degrees = Integer.parseInt(blockId.substring(yawStart, yawEnd));
            return RotationTuple.of(Rotation.ofDegrees(degrees), Rotation.None);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
