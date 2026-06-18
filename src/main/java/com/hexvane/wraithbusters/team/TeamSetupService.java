package com.hexvane.wraithbusters.team;

import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.component.HiddenFromAdventurePlayers;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class TeamSetupService {
    private TeamSetupService() {}

    public static void applyGhost(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull WraithBustersPluginConfig config
    ) {
        MovementManager movement = accessor.getComponent(playerRef, MovementManager.getComponentType());
        if (movement != null) {
            movement.getSettings().canFly = true;
            movement.getSettings().collisionExpulsionForce = 0.02f;
            sendMovementUpdate(accessor, playerRef, movement);
        }
        accessor.tryRemoveComponent(playerRef, Intangible.getComponentType());
        accessor.ensureAndGetComponent(playerRef, Invulnerable.getComponentType());
    }

    public static void applyGhost(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull WraithBustersPluginConfig config,
        @Nonnull GameSession session,
        @Nonnull World world
    ) {
        applyGhost(playerRef, accessor, config);
        refreshVisibility(session, world);
    }

    public static void applyHuman(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull GameSession session,
        @Nonnull UUID humanUuid
    ) {
        revealPlayerGlobally(humanUuid);
        MovementManager movement = accessor.getComponent(playerRef, MovementManager.getComponentType());
        if (movement != null) {
            movement.getSettings().canFly = false;
            movement.getSettings().collisionExpulsionForce = 0.04f;
            sendMovementUpdate(accessor, playerRef, movement);
        }
        accessor.tryRemoveComponent(playerRef, Intangible.getComponentType());
        accessor.tryRemoveComponent(playerRef, Invulnerable.getComponentType());
        accessor.tryRemoveComponent(playerRef, HiddenFromAdventurePlayers.getComponentType());
        PlayerRef pr = accessor.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.getHiddenPlayersManager().showPlayer(humanUuid);
        }
    }

    public static void applySpectator(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        MovementManager movement = accessor.getComponent(playerRef, MovementManager.getComponentType());
        if (movement != null) {
            movement.getSettings().canFly = true;
            movement.getSettings().collisionExpulsionForce = 0f;
            sendMovementUpdate(accessor, playerRef, movement);
        }
        accessor.ensureAndGetComponent(playerRef, Intangible.getComponentType());
        accessor.ensureAndGetComponent(playerRef, HiddenFromAdventurePlayers.getComponentType());
        accessor.tryRemoveComponent(playerRef, Invulnerable.getComponentType());
    }

    public static void clearModes(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull GameSession session
    ) {
        resetMovement(playerRef, accessor);
        PlayerRef pr = accessor.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            revealPlayerGlobally(pr.getUuid());
            for (UUID other : session.playerUuidList()) {
                pr.getHiddenPlayersManager().showPlayer(other);
            }
        }
    }

    public static void revealPlayerGlobally(@Nonnull UUID playerUuid) {
        for (PlayerRef viewer : Universe.get().getPlayers()) {
            viewer.getHiddenPlayersManager().showPlayer(playerUuid);
        }
    }

    public static void resetMovement(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        MovementManager movement = accessor.getComponent(playerRef, MovementManager.getComponentType());
        if (movement != null) {
            movement.resetDefaultsAndUpdate(playerRef, accessor);
        }
        accessor.tryRemoveComponent(playerRef, Intangible.getComponentType());
        accessor.tryRemoveComponent(playerRef, Invulnerable.getComponentType());
        accessor.tryRemoveComponent(playerRef, HiddenFromAdventurePlayers.getComponentType());
    }

    public static void hidePlayerFromSession(@Nonnull GameSession session, @Nonnull UUID hiddenUuid) {
        for (PlayerRef viewer : Universe.get().getPlayers()) {
            if (!session.getPlayers().containsKey(viewer.getUuid()) || viewer.getUuid().equals(hiddenUuid)) {
                continue;
            }
            viewer.getHiddenPlayersManager().hidePlayer(hiddenUuid);
        }
    }

    public static void refreshVisibility(@Nonnull GameSession session, @Nonnull World world) {
        DeferredWorldTasks.run(world, () -> refreshVisibilityNow(session, world));
    }

    private static void refreshVisibilityNow(@Nonnull GameSession session, @Nonnull World world) {
        for (UUID playerUuid : session.playerUuidList()) {
            revealPlayerGlobally(playerUuid);
        }
        if (session.getPhase() != GamePhase.ACTIVE) {
            return;
        }
        for (PlayerRef viewerRef : world.getPlayerRefs()) {
            UUID viewerUuid = viewerRef.getUuid();
            PlayerSessionState viewerState = session.getPlayers().get(viewerUuid);
            if (viewerState == null) {
                continue;
            }
            if (viewerState.getRole() == PlayerRole.HUMAN) {
                refreshVisibilityForHuman(session, viewerRef);
            } else {
                refreshVisibilityForNonHuman(session, viewerRef);
            }
        }
    }

    /** Ghosts see living players and other ghosts; spectators stay hidden from everyone. */
    private static void refreshVisibilityForNonHuman(@Nonnull GameSession session, @Nonnull PlayerRef viewerRef) {
        showAllSessionPlayers(session, viewerRef);
        for (UUID otherUuid : session.playerUuidList()) {
            if (session.getOrCreatePlayer(otherUuid).getRole() == PlayerRole.SPECTATOR) {
                viewerRef.getHiddenPlayersManager().hidePlayer(otherUuid);
            }
        }
    }

    private static void showAllSessionPlayers(@Nonnull GameSession session, @Nonnull PlayerRef viewerRef) {
        for (UUID otherUuid : session.playerUuidList()) {
            viewerRef.getHiddenPlayersManager().showPlayer(otherUuid);
        }
    }

    /** Humans cannot see ghosts or dead spectators during a round. */
    private static void refreshVisibilityForHuman(@Nonnull GameSession session, @Nonnull PlayerRef viewerRef) {
        showAllSessionPlayers(session, viewerRef);
        for (UUID otherUuid : session.playerUuidList()) {
            PlayerSessionState otherState = session.getOrCreatePlayer(otherUuid);
            if (otherState.getRole() == PlayerRole.GHOST || otherState.getRole() == PlayerRole.SPECTATOR) {
                viewerRef.getHiddenPlayersManager().hidePlayer(otherUuid);
            }
        }
    }

    private static void sendMovementUpdate(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull MovementManager movement
    ) {
        PlayerRef pr = accessor.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            movement.update(pr.getPacketHandler());
        }
    }
}
