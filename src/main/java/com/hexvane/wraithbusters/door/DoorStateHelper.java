package com.hexvane.wraithbusters.door;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Vector3i;
public final class DoorStateHelper {
    private static final String DOOR_BLOCKED = "DoorBlocked";
    private static final String OPEN_DOOR_IN = "OpenDoorIn";
    private static final String OPEN_DOOR_OUT = "OpenDoorOut";

    private DoorStateHelper() {}

    @Nonnull
    public static Vector3i resolveDoorAnchor(@Nonnull World world, @Nonnull Vector3i blockPos) {
        com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk chunk = world.getChunk(
            ChunkUtil.indexChunkFromBlock(blockPos.x, blockPos.z)
        );
        if (chunk == null) {
            return new Vector3i(blockPos);
        }
        int filler = chunk.getFiller(blockPos.x, blockPos.y, blockPos.z);
        if (filler == 0) {
            return new Vector3i(blockPos);
        }
        return new Vector3i(
            blockPos.x - FillerBlockUtil.unpackX(filler),
            blockPos.y - FillerBlockUtil.unpackY(filler),
            blockPos.z - FillerBlockUtil.unpackZ(filler)
        );
    }

    public static void blockDoor(@Nonnull World world, @Nonnull Vector3i blockPos) {
        Vector3i anchor = resolveDoorAnchor(world, blockPos);
        BlockType blockType = world.getBlockType(anchor.x, anchor.y, anchor.z);
        if (blockType == null || blockType.getBlockForState(DOOR_BLOCKED) == null) {
            return;
        }
        world.setBlockInteractionState(anchor, blockType, DOOR_BLOCKED);
    }

    public static void tryOpen(@Nonnull World world, @Nonnull Vector3i blockPos) {
        tryOpenAll(world, List.of(blockPos));
    }

    /** Opens every door in a set using the same swing direction when possible. */
    public static void tryOpenAll(@Nonnull World world, @Nonnull Collection<Vector3i> blockPositions) {
        LinkedHashSet<Vector3i> anchors = new LinkedHashSet<>();
        for (Vector3i pos : blockPositions) {
            anchors.add(resolveDoorAnchor(world, pos));
        }
        if (anchors.isEmpty()) {
            return;
        }
        if (tryOpenAllWithState(world, anchors, OPEN_DOOR_IN)) {
            return;
        }
        tryOpenAllWithState(world, anchors, OPEN_DOOR_OUT);
    }

    private static boolean tryOpenAllWithState(
        @Nonnull World world,
        @Nonnull Set<Vector3i> anchors,
        @Nonnull String state
    ) {
        for (Vector3i anchor : anchors) {
            BlockType blockType = world.getBlockType(anchor.x, anchor.y, anchor.z);
            if (blockType == null || !canOpenDoor(world, anchor, state)) {
                return false;
            }
        }
        for (Vector3i anchor : anchors) {
            BlockType blockType = world.getBlockType(anchor.x, anchor.y, anchor.z);
            if (blockType != null) {
                world.setBlockInteractionState(anchor, blockType, state);
            }
        }
        return true;
    }

    private static boolean canOpenDoor(@Nonnull World world, @Nonnull Vector3i blockPosition, @Nonnull String state) {
        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z));
        if (chunk == null) {
            return false;
        }
        int blockId = chunk.getBlock(blockPosition.x, blockPosition.y, blockPosition.z);
        BlockType originalBlockType = BlockType.getAssetMap().getAsset(blockId);
        if (originalBlockType == null) {
            return false;
        }
        BlockType variantBlockType = originalBlockType.getBlockForState(state);
        if (variantBlockType == null) {
            return false;
        }
        @SuppressWarnings("deprecation")
        int rotation = chunk.getRotationIndex(blockPosition.x, blockPosition.y, blockPosition.z);
        return world.testPlaceBlock(
            blockPosition.x,
            blockPosition.y,
            blockPosition.z,
            variantBlockType,
            rotation,
            (blockX, blockY, blockZ, ignoredType, ignoredRotation, filler) -> {
                if (filler != 0) {
                    blockX -= FillerBlockUtil.unpackX(filler);
                    blockY -= FillerBlockUtil.unpackY(filler);
                    blockZ -= FillerBlockUtil.unpackZ(filler);
                }
                return blockX == blockPosition.x && blockY == blockPosition.y && blockZ == blockPosition.z;
            }
        );
    }
}
