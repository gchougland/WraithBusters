package com.hexvane.wraithbusters.team;

import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.player.HumanSprintService;
import com.hexvane.wraithbusters.player.PlayerModelService;
import com.hexvane.wraithbusters.setup.SetupModeService;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.component.HiddenFromAdventurePlayers;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hexvane.wraithbusters.util.WraithBustersVoiceUtil;
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
        accessor.tryRemoveComponent(playerRef, Intangible.getComponentType());
        accessor.ensureAndGetComponent(playerRef, Invulnerable.getComponentType());
        // Model change triggers PlayerUpdateMovementManager, which resets canFly — apply model first.
        PlayerModelService.applyGhostModel(playerRef, accessor);
        applyGhostMovement(playerRef, accessor);
    }

    private static void applyGhostMovement(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        MovementManager movement = accessor.getComponent(playerRef, MovementManager.getComponentType());
        if (movement != null) {
            movement.getSettings().canFly = true;
            movement.getSettings().collisionExpulsionForce = 0.02f;
            sendMovementUpdate(accessor, playerRef, movement);
        }
    }

    public static void applyGhost(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull WraithBustersPluginConfig config,
        @Nonnull GameSession session,
        @Nonnull World world
    ) {
        applyGhost(playerRef, accessor, config);
        DeferredWorldTasks.run(world, () -> {
            if (!playerRef.isValid() || !DeferredWorldTasks.isStoreOpen(world)) {
                return;
            }
            applyGhostMovement(playerRef, world.getEntityStore().getStore());
        });
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
        HumanSprintService.apply(playerRef, accessor);
        accessor.tryRemoveComponent(playerRef, Intangible.getComponentType());
        accessor.tryRemoveComponent(playerRef, Invulnerable.getComponentType());
        accessor.tryRemoveComponent(playerRef, HiddenFromAdventurePlayers.getComponentType());
        PlayerRef pr = accessor.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.getHiddenPlayersManager().showPlayer(humanUuid);
        }
        PlayerModelService.resetToPlayerSkin(playerRef, accessor);
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
        HumanSprintService.remove(playerRef, accessor);
        PlayerModelService.resetToPlayerSkin(playerRef, accessor);
    }

    public static void clearModes(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull GameSession session
    ) {
        HumanSprintService.remove(playerRef, accessor);
        resetMovement(playerRef, accessor);
        PlayerModelService.resetToPlayerSkin(playerRef, accessor);
        PlayerRef pr = accessor.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            revealPlayerGlobally(pr.getUuid());
            for (UUID other : session.playerUuidList()) {
                pr.getHiddenPlayersManager().showPlayer(other);
            }
            WraithBustersVoiceUtil.unsilence(pr);
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
        if (session.getPhase() != GamePhase.ACTIVE) {
            for (UUID playerUuid : session.playerUuidList()) {
                revealPlayerGlobally(playerUuid);
            }
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
            if (!shouldViewerSeeSessionPlayer(session, viewerRef.getUuid(), otherUuid)) {
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
            if (!shouldViewerSeeSessionPlayer(session, viewerRef.getUuid(), otherUuid)) {
                viewerRef.getHiddenPlayersManager().hidePlayer(otherUuid);
            }
        }
    }

    public static boolean canBypassTeamVisibility(
        @Nonnull UUID viewerUuid,
        @Nonnull Player viewer
    ) {
        if (viewer.getGameMode() == GameMode.Creative) {
            return true;
        }
        return SetupModeService.isActive(viewerUuid);
    }

    public static boolean shouldViewerSeeSessionPlayer(
        @Nonnull GameSession session,
        @Nonnull UUID viewerUuid,
        @Nonnull UUID targetUuid
    ) {
        if (viewerUuid.equals(targetUuid)) {
            return true;
        }
        PlayerSessionState viewerState = session.getPlayers().get(viewerUuid);
        PlayerSessionState targetState = session.getPlayers().get(targetUuid);
        if (viewerState == null || targetState == null) {
            return true;
        }
        PlayerRole targetRole = targetState.getRole();
        if (viewerState.getRole() == PlayerRole.HUMAN) {
            return targetRole != PlayerRole.GHOST && targetRole != PlayerRole.SPECTATOR;
        }
        return targetRole != PlayerRole.SPECTATOR;
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
