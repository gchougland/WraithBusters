package com.hexvane.wraithbusters.triggervolume;

import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.team.Team;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class OfferingPlayerNotify {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private OfferingPlayerNotify() {}

    static void sessionHumans(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull String messageKey,
        @Nonnull String... params
    ) {
        sessionPlayersInWorld(session, world, messageKey, Team.HUMAN, params);
    }

    static void sessionPlayers(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull String messageKey,
        @Nonnull String... params
    ) {
        sessionPlayersInWorld(session, world, messageKey, null, params);
    }

    static void worldPlayers(@Nonnull World world, @Nonnull String messageKey, @Nonnull String... params) {
        Message message = buildMessage(messageKey, params);
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            playerRef.sendMessage(message);
        }
    }

    static void logBlocked(@Nonnull String reason, @Nonnull String volumeId, @Nonnull String roomId, @Nonnull String detail) {
        LOGGER.atInfo().log("Offering blocked on volume %s (room %s): %s — %s", volumeId, roomId, reason, detail);
    }

    private static void sessionPlayersInWorld(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull String messageKey,
        @Nullable Team teamFilter,
        @Nonnull String... params
    ) {
        Message message = buildMessage(messageKey, params);
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            UUID playerUuid = playerRef.getUuid();
            if (!session.getPlayers().containsKey(playerUuid)) {
                continue;
            }
            if (teamFilter != null && session.getOrCreatePlayer(playerUuid).getTeam() != teamFilter) {
                continue;
            }
            playerRef.sendMessage(message);
        }
    }

    @Nonnull
    private static Message buildMessage(@Nonnull String messageKey, @Nonnull String... params) {
        Message message = Message.translation(messageKey);
        for (int i = 0; i + 1 < params.length; i += 2) {
            message = message.param(params[i], params[i + 1]);
        }
        return message;
    }
}
