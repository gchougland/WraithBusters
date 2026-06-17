package com.hexvane.wraithbusters.debug;

import com.hexvane.wraithbusters.WraithBustersMessages;
import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.ghost.PhasePortalMarkerService;
import com.hexvane.wraithbusters.pickup.ManaPickupService;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.team.Team;
import com.hexvane.wraithbusters.team.TeamSetupService;
import com.hexvane.wraithbusters.ui.GhostManaHudSupport;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Solo ghost testing without starting a round. Flight works anywhere; phase doors, orbs, and
 * session portals require a game instance on the current world ({@code /wb start}).
 */
public final class GhostTestService {
    private static final ConcurrentHashMap<UUID, Boolean> ACTIVE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> TEST_MARKER_REF_COUNT = new ConcurrentHashMap<>();

    private GhostTestService() {}

    public static boolean isActive(@Nonnull UUID playerUuid) {
        return ACTIVE.containsKey(playerUuid);
    }

    public static boolean hasTestMarkers(@Nonnull GameSession session) {
        return TEST_MARKER_REF_COUNT.getOrDefault(session.getSessionId(), 0) > 0;
    }

    public static void onRoundStarting(@Nonnull GameSession session) {
        TEST_MARKER_REF_COUNT.remove(session.getSessionId());
        for (UUID playerUuid : session.playerUuidList()) {
            ACTIVE.remove(playerUuid);
        }
    }

    public static void enable(
        @Nonnull WraithBustersPlugin plugin,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRefComponent,
        @Nullable GameSession session,
        @Nonnull World world
    ) {
        UUID playerUuid = playerRefComponent.getUuid();
        ACTIVE.put(playerUuid, Boolean.TRUE);
        WraithBustersPluginConfig config = plugin.getPluginConfig();

        if (session == null) {
            TeamSetupService.applyGhost(playerRef, store, config);
            playerRefComponent.sendMessage(WraithBustersMessages.translation("testghost.enabledNoSession"));
            return;
        }

        PlayerSessionState state = session.getOrCreatePlayer(playerUuid);
        state.setRole(PlayerRole.GHOST);
        state.setTeam(Team.GHOST);
        state.setGhostMana(config.getGhostMaxMana());
        TeamSetupService.applyGhost(playerRef, store, config, session, world);

        if (session.getPhase() != GamePhase.ACTIVE) {
            acquireTestMarkers(session, world);
        }

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            GhostManaHudSupport.refresh(player, playerRefComponent, state, config);
        }
        playerRefComponent.sendMessage(WraithBustersMessages.translation("testghost.enabled"));
    }

    public static void disable(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRefComponent,
        @Nullable GameSession session,
        @Nonnull World world
    ) {
        UUID playerUuid = playerRefComponent.getUuid();
        if (!ACTIVE.remove(playerUuid)) {
            playerRefComponent.sendMessage(WraithBustersMessages.translation("testghost.notActive"));
            return;
        }

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            GhostManaHudSupport.removeHud(player, playerRefComponent);
        }

        if (session != null) {
            releaseTestMarkers(session, world);
            if (session.getPhase() == GamePhase.LOBBY || session.getPhase() == GamePhase.COUNTDOWN) {
                PlayerSessionState state = session.getPlayers().get(playerUuid);
                if (state != null) {
                    state.setRole(PlayerRole.LOBBY);
                    state.setTeam(Team.NONE);
                    state.setGhostMana(0);
                }
            }
            TeamSetupService.clearModes(playerRef, store, session);
            TeamSetupService.refreshVisibility(session, world);
        } else {
            TeamSetupService.resetMovement(playerRef, store);
        }

        playerRefComponent.sendMessage(WraithBustersMessages.translation("testghost.disabled"));
    }

    private static void acquireTestMarkers(@Nonnull GameSession session, @Nonnull World world) {
        UUID sessionId = session.getSessionId();
        int count = TEST_MARKER_REF_COUNT.merge(sessionId, 1, Integer::sum);
        if (count == 1) {
            PhasePortalMarkerService.startRound(session, world);
            ManaPickupService.startRound(session, world);
        }
    }

    private static void releaseTestMarkers(@Nonnull GameSession session, @Nonnull World world) {
        UUID sessionId = session.getSessionId();
        Integer count = TEST_MARKER_REF_COUNT.get(sessionId);
        if (count == null) {
            return;
        }
        if (count <= 1) {
            TEST_MARKER_REF_COUNT.remove(sessionId);
            if (session.getPhase() != GamePhase.ACTIVE) {
                PhasePortalMarkerService.clearForLobby(session, world);
                ManaPickupService.clearForLobby(session, world);
            }
        } else {
            TEST_MARKER_REF_COUNT.put(sessionId, count - 1);
        }
    }
}
