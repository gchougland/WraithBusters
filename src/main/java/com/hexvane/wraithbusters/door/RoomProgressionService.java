package com.hexvane.wraithbusters.door;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GameSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class RoomProgressionService {
    private RoomProgressionService() {}

    public static void initializeRound(
        @Nonnull GameSession session,
        int humanCount,
        @Nonnull WraithBustersPluginConfig config
    ) {
        List<RoomDefinition> allRooms = session.getArenaLayout().getRooms();
        RoomDefinition atticRoom = null;
        List<RoomDefinition> puzzlePool = new ArrayList<>();
        for (RoomDefinition room : allRooms) {
            if (WraithBustersConstants.ATTIC_ROOM_ID.equals(room.getRoomId())) {
                atticRoom = room;
            } else {
                puzzlePool.add(room);
            }
        }
        Collections.shuffle(puzzlePool);
        int roomsNeeded = Math.max(1, humanCount * config.getRoomsPerHuman());
        roomsNeeded = Math.min(roomsNeeded, puzzlePool.size());
        List<String> chain = new ArrayList<>();
        for (int i = 0; i < roomsNeeded; i++) {
            chain.add(puzzlePool.get(i).getRoomId());
        }
        if (atticRoom != null) {
            chain.add(atticRoom.getRoomId());
        }
        session.setActiveRoomChain(chain);
        session.setCurrentRoomIndex(0);
        session.setStartingRoomId(chain.isEmpty() ? null : chain.getFirst());
    }

    @Nonnull
    public static RoomDefinition currentRoom(@Nonnull GameSession session) {
        List<String> chain = session.getActiveRoomChain();
        if (chain.isEmpty()) {
            return session.getArenaLayout().getRooms().getFirst();
        }
        String roomId = chain.get(Math.min(session.getCurrentRoomIndex(), chain.size() - 1));
        for (RoomDefinition room : session.getArenaLayout().getRooms()) {
            if (room.getRoomId().equals(roomId)) {
                return room;
            }
        }
        return session.getArenaLayout().getRooms().getFirst();
    }

    public static void advanceAfterPuzzle(@Nonnull GameSession session) {
        int next = session.getCurrentRoomIndex() + 1;
        if (next < session.getActiveRoomChain().size()) {
            session.setCurrentRoomIndex(next);
        }
    }

    public static boolean isDoorUnlocked(@Nonnull GameSession session, @Nonnull String roomId) {
        List<String> chain = session.getActiveRoomChain();
        if (chain.isEmpty()) {
            return false;
        }
        int index = chain.indexOf(roomId);
        if (index < 0) {
            return false;
        }
        return index <= session.getCurrentRoomIndex();
    }

    public static boolean isStartingRoom(@Nonnull GameSession session, @Nonnull String roomId) {
        String startingRoomId = session.getStartingRoomId();
        return startingRoomId != null && startingRoomId.equals(roomId);
    }

    @Nullable
    public static RoomDefinition findRoom(@Nonnull GameSession session, @Nonnull String roomId) {
        for (RoomDefinition room : session.getArenaLayout().getRooms()) {
            if (room.getRoomId().equals(roomId)) {
                return room;
            }
        }
        return null;
    }

    @Nullable
    public static RoomDefinition nextRoom(@Nonnull GameSession session) {
        List<String> chain = session.getActiveRoomChain();
        int nextIndex = session.getCurrentRoomIndex();
        if (nextIndex < 0 || nextIndex >= chain.size()) {
            return null;
        }
        return findRoom(session, chain.get(nextIndex));
    }
}
