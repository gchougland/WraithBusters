package com.hexvane.wraithbusters.ghost;



import com.hexvane.wraithbusters.WraithBustersConstants;

import com.hexvane.wraithbusters.arena.ArenaLayout;

import com.hexvane.wraithbusters.arena.GhostPhaseDoorMarker;

import com.hexvane.wraithbusters.arena.PhaseDoorSize;

import com.hexvane.wraithbusters.debug.GhostTestService;

import com.hexvane.wraithbusters.game.GamePhase;

import com.hexvane.wraithbusters.game.GameSession;

import com.hexvane.wraithbusters.player.PlayerRole;

import com.hexvane.wraithbusters.setup.SetupModeService;

import com.hexvane.wraithbusters.util.PlayerTeleportUtil;

import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;

import com.hypixel.hytale.component.ComponentAccessor;

import com.hypixel.hytale.component.Ref;

import com.hypixel.hytale.component.Store;

import com.hypixel.hytale.math.shape.Box;

import com.hypixel.hytale.math.vector.Transform;

import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import com.hypixel.hytale.server.core.universe.world.World;

import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

import javax.annotation.Nonnull;

import javax.annotation.Nullable;

import org.joml.Vector3d;



public final class PhasePortalService {

    private PhasePortalService() {}

    public static boolean tryUse(

        @Nullable GameSession session,

        @Nonnull World world,

        @Nonnull Ref<EntityStore> playerRef,

        @Nonnull ComponentAccessor<EntityStore> accessor,

        @Nonnull Ref<EntityStore> portalRef

    ) {

        Store<EntityStore> store = playerRef.getStore();

        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());

        if (player == null) {

            return false;

        }

        UUID playerUuid = player.getUuid();

        boolean testGhost = GhostTestService.isActive(playerUuid);

        boolean setupBuilder = SetupModeService.isActive(playerUuid);

        if (!testGhost && !setupBuilder) {

            if (session == null || session.getPhase() != GamePhase.ACTIVE) {

                return false;

            }

            if (session.getOrCreatePlayer(playerUuid).getRole() != PlayerRole.GHOST) {

                return false;

            }

        }



        PhasePortalMarkerService.PortalBinding binding = PhasePortalMarkerService.findBinding(portalRef, store);

        if (binding == null) {

            return false;

        }

        GhostPhaseDoorMarker door = findDoor(session, binding.doorId(), playerUuid);

        if (door == null) {

            return false;

        }

        if (session != null && session.isPhaseDoorDisabled(binding.doorId())) {

            return false;

        }



        BoundingBox boundingBox = store.getComponent(playerRef, BoundingBox.getComponentType());

        if (boundingBox == null) {

            return false;

        }

        Box playerBox = boundingBox.getBoundingBox();

        PhaseDoorSize size = door.getDoorSize();

        Vector3d entryPos = door.getEntry().getPosition();

        Vector3d exitPos = door.getExit().getPosition();

        Vector3d midpoint = midpoint(entryPos, exitPos);

        Transform destinationPortal = binding.entrySide() ? door.getExit() : door.getEntry();

        Vector3d otherPortalPos = binding.entrySide() ? exitPos : entryPos;



        PlayerTeleportUtil.teleport(

            playerRef,

            accessor,

            world,

            PhasePortalTeleportUtil.buildEgress(

                world,

                playerBox,

                destinationPortal,

                midpoint,

                otherPortalPos,

                size.triggerRadius()

            )

        );

        playPhaseSound(world, door);

        return true;

    }



    @Nullable

    private static GhostPhaseDoorMarker findDoor(

        @Nullable GameSession session,

        @Nonnull String doorId,

        @Nonnull UUID playerUuid

    ) {

        if (GhostTestService.isActive(playerUuid) && session != null) {

            GhostPhaseDoorMarker door = findDoorInLayout(session.getArenaLayout(), doorId);

            if (door != null) {

                return door;

            }

        }

        if (SetupModeService.isActive(playerUuid)) {

            SetupModeService.SetupSession setup = SetupModeService.get(playerUuid);

            if (setup != null) {

                GhostPhaseDoorMarker door = findDoorInLayout(setup.getLayout(), doorId);

                if (door != null) {

                    return door;

                }

            }

        }

        if (session != null) {

            return findDoorInLayout(session.getArenaLayout(), doorId);

        }

        return null;

    }



    @Nullable

    private static GhostPhaseDoorMarker findDoorInLayout(@Nonnull ArenaLayout layout, @Nonnull String doorId) {

        for (GhostPhaseDoorMarker door : layout.getGhostPhaseDoors()) {

            if (doorId.equals(door.getId())) {

                return door;

            }

        }

        return null;

    }



    private static void playPhaseSound(@Nonnull World world, @Nonnull GhostPhaseDoorMarker door) {

        Vector3d entryPos = door.getEntry().getPosition();

        Vector3d exitPos = door.getExit().getPosition();

        Vector3d midpoint = midpoint(entryPos, exitPos);

        WraithBustersSoundUtil.play3dAtPosition(

            world,

            midpoint.x,

            midpoint.y,

            midpoint.z,

            WraithBustersConstants.PHASE_DOOR_SOUND_EVENT

        );

    }



    @Nonnull

    private static Vector3d midpoint(@Nonnull Vector3d a, @Nonnull Vector3d b) {

        return new Vector3d(

            (a.x + b.x) * 0.5,

            (a.y + b.y) * 0.5,

            (a.z + b.z) * 0.5

        );

    }

}


