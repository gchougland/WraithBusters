package com.hexvane.wraithbusters.ui;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.door.RoomProgressionService;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hypixel.hytale.server.core.Message;
import javax.annotation.Nonnull;

public final class ObjectiveHudContent {
    private static final String PREFIX = "server.wraithbusters.objective.";

    private ObjectiveHudContent() {}

    @Nonnull
    public static Message title(@Nonnull GameSession session) {
        RoomDefinition room = RoomProgressionService.currentRoom(session);
        return Message.translation(PREFIX + "room." + room.getRoomId());
    }

    @Nonnull
    public static Message description(@Nonnull GameSession session, @Nonnull PlayerSessionState state) {
        if (state.getRole() == PlayerRole.GHOST) {
            return Message.translation(PREFIX + "ghost.humansRemaining")
                .param("count", session.livingHumanCount());
        }
        RoomDefinition room = RoomProgressionService.currentRoom(session);
        return Message.translation(PREFIX + "puzzle." + puzzleLangSuffix(room));
    }

    @Nonnull
    private static String puzzleLangSuffix(@Nonnull RoomDefinition room) {
        if (WraithBustersConstants.ATTIC_ROOM_ID.equals(room.getRoomId())) {
            return "attic";
        }
        String puzzleId = room.getPuzzleId();
        if (puzzleId == null || puzzleId.isBlank()) {
            return "unknown";
        }
        return switch (puzzleId) {
            case "candles",
                 "kitchen_offering",
                 "lab_cauldron",
                 "cheese_chase",
                 "library_books" -> puzzleId;
            default -> "unknown";
        };
    }
}
