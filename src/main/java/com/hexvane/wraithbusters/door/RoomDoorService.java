package com.hexvane.wraithbusters.door;

import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.game.GameSession;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Applies locked-door block states for room progression (open starting room, block the rest). */
public final class RoomDoorService {
    private static final String DOOR_BLOCKED = "DoorBlocked";
    private static final String OPEN_DOOR_IN = "OpenDoorIn";
    private static final String OPEN_DOOR_OUT = "OpenDoorOut";

    private RoomDoorService() {}

    public static void applyRoundStart(@Nonnull GameSession session, @Nonnull World world) {
        DeferredWorldTasks.run(world, () -> applyRoundStartNow(session, world));
    }

    public static void openDoor(@Nonnull World world, @Nonnull RoomDefinition room) {
        DeferredWorldTasks.run(world, () -> openDoorNow(world, room));
    }

    private static void applyRoundStartNow(@Nonnull GameSession session, @Nonnull World world) {
        for (RoomDefinition room : session.getArenaLayout().getRooms()) {
            blockDoor(world, room);
        }
        String startingRoomId = session.getStartingRoomId();
        if (startingRoomId == null) {
            return;
        }
        RoomDefinition starting = findRoom(session, startingRoomId);
        if (starting != null) {
            openDoorNow(world, starting);
        }
    }

    private static void openDoorNow(@Nonnull World world, @Nonnull RoomDefinition room) {
        setDoorState(world, room, true);
    }

    private static void blockDoor(@Nonnull World world, @Nonnull RoomDefinition room) {
        for (Vector3i pos : room.getDoorBlocks()) {
            blockDoorAt(world, pos);
        }
    }

    private static void blockDoorAt(@Nonnull World world, @Nonnull Vector3i pos) {
        BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
        if (blockType == null) {
            return;
        }
        if (blockType.getBlockForState(DOOR_BLOCKED) != null) {
            world.setBlockInteractionState(pos, blockType, DOOR_BLOCKED);
        }
    }

    private static void setDoorState(@Nonnull World world, @Nonnull RoomDefinition room, boolean open) {
        for (Vector3i pos : room.getDoorBlocks()) {
            setDoorStateAt(world, pos, open);
        }
    }

    private static void setDoorStateAt(@Nonnull World world, @Nonnull Vector3i pos, boolean open) {
        BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
        if (blockType == null) {
            return;
        }
        if (!open) {
            world.setBlockInteractionState(pos, blockType, DOOR_BLOCKED);
            return;
        }
        if (tryDoorState(world, pos, blockType, OPEN_DOOR_IN)) {
            return;
        }
        tryDoorState(world, pos, blockType, OPEN_DOOR_OUT);
    }

    private static boolean tryDoorState(
        @Nonnull World world,
        @Nonnull Vector3i pos,
        @Nonnull BlockType blockType,
        @Nonnull String state
    ) {
        if (blockType.getBlockForState(state) == null) {
            return false;
        }
        world.setBlockInteractionState(pos, blockType, state);
        return true;
    }

    @Nullable
    private static RoomDefinition findRoom(@Nonnull GameSession session, @Nonnull String roomId) {
        for (RoomDefinition room : session.getArenaLayout().getRooms()) {
            if (room.getRoomId().equals(roomId)) {
                return room;
            }
        }
        return null;
    }
}
