package com.hexvane.wraithbusters.util;

import com.hexvane.wraithbusters.game.GameSession;
import com.hypixel.hytale.server.core.modules.voice.VoiceModule;
import com.hypixel.hytale.server.core.modules.voice.VoicePlayerState;
import com.hypixel.hytale.server.core.modules.voice.VoiceRouter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class WraithBustersVoiceUtil {
    private WraithBustersVoiceUtil() {}

    public static void unsilence(@Nonnull PlayerRef playerRef) {
        VoiceModule voiceModule = VoiceModule.get();
        if (voiceModule == null) {
            return;
        }
        VoicePlayerState state = voiceModule.getPlayerState(playerRef.getUuid());
        if (state != null) {
            state.setSilenced(false);
            state.setSpeaking(false);
        }
        VoiceRouter router = voiceModule.getVoiceRouter();
        if (router != null) {
            router.sendVoiceConfig(playerRef);
        }
        voiceModule.scheduleImmediatePositionUpdate(playerRef);
    }

    public static void unsilence(@Nonnull UUID playerUuid) {
        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
        if (playerRef != null) {
            unsilence(playerRef);
        }
    }

    public static void unsilenceSession(@Nonnull GameSession session) {
        for (UUID playerUuid : session.playerUuidList()) {
            unsilence(playerUuid);
        }
    }

    public static void unsilenceWorldPlayers(@Nonnull World world) {
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            unsilence(playerRef);
        }
    }
}
