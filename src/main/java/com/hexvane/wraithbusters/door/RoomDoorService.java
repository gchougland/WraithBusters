package com.hexvane.wraithbusters.door;

import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.ghost.PhasePortalMarkerService;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Applies locked-door block states for room progression (open starting room, block the rest). */
public final class RoomDoorService {
    private RoomDoorService() {}

    public static void applyRoundStart(@Nonnull GameSession session, @Nonnull World world) {
        DeferredWorldTasks.run(world, () -> applyRoundStartNow(session, world));
    }

    public static void openDoor(@Nonnull GameSession session, @Nonnull World world, @Nonnull RoomDefinition room) {
        DeferredWorldTasks.run(world, () -> openDoorNow(session, world, room));
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
            openDoorNow(session, world, starting);
        }
    }

    private static void openDoorNow(@Nonnull GameSession session, @Nonnull World world, @Nonnull RoomDefinition room) {
        DoorStateHelper.tryOpenAll(world, uniqueDoorBlocks(room));
        PhasePortalMarkerService.onRoomDoorOpened(session, world, room);
        HumanLockedDoorMarkerService.onRoomDoorOpened(session, world, room);
    }

    private static void blockDoor(@Nonnull World world, @Nonnull RoomDefinition room) {
        for (Vector3i pos : uniqueDoorBlocks(room)) {
            DoorStateHelper.blockDoor(world, pos);
        }
    }

    @Nonnull
    private static Set<Vector3i> uniqueDoorBlocks(@Nonnull RoomDefinition room) {
        Set<Vector3i> unique = new LinkedHashSet<>();
        for (Vector3i pos : room.getDoorBlocks()) {
            unique.add(new Vector3i(pos));
        }
        return unique;
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
