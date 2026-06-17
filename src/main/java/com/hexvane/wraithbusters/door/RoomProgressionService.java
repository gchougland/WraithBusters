package com.hexvane.wraithbusters.door;

import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GameSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;

public final class RoomProgressionService {
    private RoomProgressionService() {}

    public static void initializeRound(
        @Nonnull GameSession session,
        int humanCount,
        @Nonnull WraithBustersPluginConfig config
    ) {
        List<RoomDefinition> pool = new ArrayList<>(session.getArenaLayout().getRooms());
        Collections.shuffle(pool);
        int roomsNeeded = Math.max(1, humanCount * config.getRoomsPerHuman());
        roomsNeeded = Math.min(roomsNeeded, pool.size());
        List<String> chain = new ArrayList<>();
        for (int i = 0; i < roomsNeeded; i++) {
            chain.add(pool.get(i).getRoomId());
        }
        session.setActiveRoomChain(chain);
        session.setCurrentRoomIndex(0);
        session.setStartingRoomId(chain.isEmpty() ? null : chain.getFirst());
        session.setAtticUnlocked(false);
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
        if (next >= session.getActiveRoomChain().size()) {
            session.setAtticUnlocked(true);
        } else {
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
}
