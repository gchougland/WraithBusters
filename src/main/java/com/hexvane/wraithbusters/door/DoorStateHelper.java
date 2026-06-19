package com.hexvane.wraithbusters.door;

import com.hexvane.wraithbusters.util.BlockSectionQueries;
import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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

    /**
     * Opens every door leaf in a room. Side-by-side panels use opposite swing states
     * (OpenDoorIn / OpenDoorOut) so double doors open the same way, matching vanilla doors.
     */
    public static void tryOpenAll(@Nonnull World world, @Nonnull Collection<Vector3i> blockPositions) {
        List<Vector3i> anchors = uniqueSortedAnchors(world, blockPositions);
        if (anchors.isEmpty()) {
            return;
        }
        String primaryState = canOpenDoor(world, anchors.getFirst(), OPEN_DOOR_IN)
            ? OPEN_DOOR_IN
            : canOpenDoor(world, anchors.getFirst(), OPEN_DOOR_OUT) ? OPEN_DOOR_OUT : null;
        if (primaryState == null) {
            return;
        }
        String secondaryState = OPEN_DOOR_IN.equals(primaryState) ? OPEN_DOOR_OUT : OPEN_DOOR_IN;
        for (int i = 0; i < anchors.size(); i++) {
            String preferred = i % 2 == 0 ? primaryState : secondaryState;
            tryOpenAnchor(world, anchors.get(i), preferred);
        }
    }

    @Nonnull
    private static List<Vector3i> uniqueSortedAnchors(@Nonnull World world, @Nonnull Collection<Vector3i> blockPositions) {
        LinkedHashSet<Vector3i> anchors = new LinkedHashSet<>();
        for (Vector3i pos : blockPositions) {
            anchors.add(resolveDoorAnchor(world, pos));
        }
        List<Vector3i> sorted = new ArrayList<>(anchors);
        sorted.sort(Comparator.comparingInt((Vector3i block) -> block.y)
            .thenComparingInt(block -> block.x)
            .thenComparingInt(block -> block.z));
        return sorted;
    }

    private static void tryOpenAnchor(@Nonnull World world, @Nonnull Vector3i anchor, @Nonnull String preferredState) {
        String state = preferredState;
        if (!canOpenDoor(world, anchor, state)) {
            String alternate = OPEN_DOOR_IN.equals(state) ? OPEN_DOOR_OUT : OPEN_DOOR_IN;
            if (!canOpenDoor(world, anchor, alternate)) {
                return;
            }
            state = alternate;
        }
        BlockType blockType = world.getBlockType(anchor.x, anchor.y, anchor.z);
        if (blockType != null) {
            world.setBlockInteractionState(anchor, blockType, state);
            WraithBustersSoundUtil.playBlockStateSound(world, anchor, blockType, state);
        }
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
        int rotation = BlockSectionQueries.getRotationIndex(world, blockPosition.x, blockPosition.y, blockPosition.z);
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
