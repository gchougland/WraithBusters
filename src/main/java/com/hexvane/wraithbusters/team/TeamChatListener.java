package com.hexvane.wraithbusters.team;

import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * During an active round, ghost chat is delivered only to other ghosts in the same session.
 * Human chat keeps the default recipient list (humans and ghosts can read it).
 */
public final class TeamChatListener {
    private TeamChatListener() {}

    public static void register(@Nonnull WraithBustersPlugin plugin) {
        plugin.getEventRegistry()
            .registerAsyncGlobal(
                PlayerChatEvent.class,
                future -> future.thenApply(TeamChatListener::filterGhostChatTargets)
            );
    }

    @Nonnull
    private static PlayerChatEvent filterGhostChatTargets(@Nonnull PlayerChatEvent event) {
        if (event.isCancelled()) {
            return event;
        }
        PlayerRef sender = event.getSender();
        UUID senderUuid = sender.getUuid();
        var ref = sender.getReference();
        if (ref == null || !ref.isValid()) {
            return event;
        }
        World world = ref.getStore().getExternalData().getWorld();
        if (world == null) {
            return event;
        }
        GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
        if (session == null || session.getPhase() != GamePhase.ACTIVE) {
            return event;
        }
        PlayerSessionState senderState = session.getPlayers().get(senderUuid);
        if (senderState == null || senderState.getRole() != PlayerRole.GHOST) {
            return event;
        }
        event.setTargets(collectGhostChatTargets(session));
        return event;
    }

    @Nonnull
    private static List<PlayerRef> collectGhostChatTargets(@Nonnull GameSession session) {
        List<PlayerRef> targets = new ArrayList<>();
        for (UUID playerUuid : session.playerUuidList()) {
            PlayerSessionState state = session.getPlayers().get(playerUuid);
            if (state == null || state.getRole() != PlayerRole.GHOST) {
                continue;
            }
            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
            if (playerRef != null) {
                targets.add(playerRef);
            }
        }
        return targets;
    }
}
