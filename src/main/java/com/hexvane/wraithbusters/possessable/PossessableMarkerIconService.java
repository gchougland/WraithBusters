package com.hexvane.wraithbusters.possessable;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.arena.PossessableMarker;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.debug.GhostTestService;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.util.StatueFacingUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Ghost-only floating icon NPCs above possessable markers. */
public final class PossessableMarkerIconService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<UUID, SessionIcons> SESSIONS = new ConcurrentHashMap<>();

    private PossessableMarkerIconService() {}

    public static boolean canPlayerSeeIcons(
        @Nonnull GameSession session,
        @Nonnull UUID playerUuid,
        @Nonnull Player player
    ) {
        if (player.getGameMode() == GameMode.Creative) {
            return true;
        }
        if (GhostTestService.isActive(playerUuid)) {
            return true;
        }
        PlayerSessionState state = session.getPlayers().get(playerUuid);
        return state != null && state.getRole() == PlayerRole.GHOST;
    }

    @Nonnull
    public static Set<Ref<EntityStore>> activeIconRefs(@Nonnull UUID sessionId) {
        SessionIcons icons = SESSIONS.get(sessionId);
        if (icons == null) {
            return Collections.emptySet();
        }
        return icons.activeRefs;
    }

    public static void startRound(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull WraithBustersPluginConfig config
    ) {
        clearSession(session.getSessionId(), world);
        SessionIcons icons = new SessionIcons();
        for (PossessableMarker marker : session.getArenaLayout().getPossessables()) {
            icons.spawns.add(new IconSpawn(marker));
        }
        SESSIONS.put(session.getSessionId(), icons);
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (IconSpawn spawn : icons.spawns) {
            trySpawn(store, icons, spawn, world, config);
        }
    }

    public static void endRound(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
    }

    public static void clearForLobby(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
    }

    public static void clear() {
        SESSIONS.clear();
    }

    public static void tick(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull WraithBustersPluginConfig config
    ) {
        if (session.getPhase() != GamePhase.ACTIVE && !GhostTestService.hasTestMarkers(session)) {
            return;
        }
        SessionIcons icons = SESSIONS.get(session.getSessionId());
        if (icons == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (IconSpawn spawn : icons.spawns) {
            if (spawn.entityRef == null || !spawn.entityRef.isValid()) {
                if (spawn.entityRef != null) {
                    icons.activeRefs.remove(spawn.entityRef);
                    spawn.entityRef = null;
                    spawn.basePosition = null;
                }
                trySpawn(store, icons, spawn, world, config);
            }
        }
        List<GhostViewer> viewers = collectGhostViewers(session, world, store);
        long nowMs = System.currentTimeMillis();
        for (IconSpawn spawn : icons.spawns) {
            if (spawn.entityRef == null || !spawn.entityRef.isValid() || spawn.basePosition == null) {
                continue;
            }
            updateIconTransform(store, spawn, viewers, config, nowMs);
        }
    }

    private static void trySpawn(
        @Nonnull Store<EntityStore> store,
        @Nonnull SessionIcons icons,
        @Nonnull IconSpawn spawn,
        @Nonnull World world,
        @Nonnull WraithBustersPluginConfig config
    ) {
        Vector3i anchor = PossessableService.resolveMarkerAnchor(world, spawn.marker);
        if (anchor == null) {
            return;
        }
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            LOGGER.atWarning().log("NPCPlugin unavailable; cannot spawn possessable marker icon");
            return;
        }
        double height = config.getPossessableIconHeight(spawn.marker.getTypeId());
        Vector3d basePosition = new Vector3d(anchor.x + 0.5, anchor.y + height, anchor.z + 0.5);
        var pair = npc.spawnNPC(
            store,
            WraithBustersConstants.POSSESSABLE_MARKER_ICON_NPC_ROLE,
            null,
            basePosition,
            Rotation3f.ZERO
        );
        if (pair == null) {
            LOGGER.atWarning().log(
                "Failed to spawn possessable marker icon for %s at [%d, %d, %d]",
                spawn.marker.getTypeId(),
                anchor.x,
                anchor.y,
                anchor.z
            );
            return;
        }
        spawn.entityRef = pair.first();
        spawn.basePosition = basePosition;
        spawn.bobPhase = bobPhaseForMarker(spawn.marker);
        applyIconScale(store, spawn.entityRef, config);
        icons.activeRefs.add(spawn.entityRef);
    }

    private static void applyIconScale(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> iconRef,
        @Nonnull WraithBustersPluginConfig config
    ) {
        float scale = config.getPossessableIconScale();
        if (scale <= 0.0f) {
            scale = 1.0f;
        }
        EntityScaleComponent scaleComponent = store.getComponent(iconRef, EntityScaleComponent.getComponentType());
        if (scaleComponent == null) {
            store.addComponent(iconRef, EntityScaleComponent.getComponentType(), new EntityScaleComponent(scale));
            return;
        }
        if (Math.abs(scaleComponent.getScale() - scale) > 0.001f) {
            scaleComponent.setScale(scale);
        }
    }

    private static double bobPhaseForMarker(@Nonnull PossessableMarker marker) {
        Vector3i pos = marker.getBlockPos();
        return (pos.x * 0.17 + pos.y * 0.11 + pos.z * 0.23) % (Math.PI * 2.0);
    }

    private static double bobOffset(@Nonnull WraithBustersPluginConfig config, double bobPhase, long nowMs) {
        double amplitude = config.getPossessableIconBobAmplitude();
        if (amplitude <= 0.0) {
            return 0.0;
        }
        double period = config.getPossessableIconBobPeriodSeconds();
        if (period <= 0.0) {
            return 0.0;
        }
        double radians = nowMs / 1000.0 * (Math.PI * 2.0 / period) + bobPhase;
        return Math.sin(radians) * amplitude;
    }

    private static void updateIconTransform(
        @Nonnull Store<EntityStore> store,
        @Nonnull IconSpawn spawn,
        @Nonnull List<GhostViewer> viewers,
        @Nonnull WraithBustersPluginConfig config,
        long nowMs
    ) {
        Ref<EntityStore> iconRef = spawn.entityRef;
        if (iconRef == null || spawn.basePosition == null) {
            return;
        }
        TransformComponent iconTransform = store.getComponent(iconRef, TransformComponent.getComponentType());
        if (iconTransform == null) {
            return;
        }
        applyIconScale(store, iconRef, config);

        Vector3d lockedPosition = new Vector3d(spawn.basePosition);
        lockedPosition.y += bobOffset(config, spawn.bobPhase, nowMs);
        iconTransform.setPosition(lockedPosition);

        Velocity velocity = store.getComponent(iconRef, Velocity.getComponentType());
        if (velocity != null) {
            velocity.setZero();
        }

        Rotation3f rotation = iconTransform.getRotation();
        GhostViewer nearest = findNearestViewer(lockedPosition, viewers);
        if (nearest != null) {
            Vector3d forward = new Vector3d(
                nearest.position.x - lockedPosition.x,
                0.0,
                nearest.position.z - lockedPosition.z
            );
            if (forward.lengthSquared() >= 0.0001) {
                forward.normalize();
                float yaw = StatueFacingUtil.yawRadians(forward);
                rotation = new Rotation3f(0.0F, yaw, 0.0F);
            }
        }
        iconTransform.setRotation(rotation);
        HeadRotation headRotation = store.ensureAndGetComponent(iconRef, HeadRotation.getComponentType());
        headRotation.setRotation(rotation);
    }

    @Nullable
    private static GhostViewer findNearestViewer(
        @Nonnull Vector3d iconPosition,
        @Nonnull List<GhostViewer> viewers
    ) {
        if (viewers.isEmpty()) {
            return null;
        }
        GhostViewer nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (GhostViewer viewer : viewers) {
            double dx = viewer.position.x - iconPosition.x;
            double dz = viewer.position.z - iconPosition.z;
            double distSq = dx * dx + dz * dz;
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = viewer;
            } else if (distSq == nearestDistSq && nearest != null && viewer.uuid.compareTo(nearest.uuid) < 0) {
                nearest = viewer;
            }
        }
        return nearest;
    }

    @Nonnull
    private static List<GhostViewer> collectGhostViewers(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store
    ) {
        List<GhostViewer> viewers = new ArrayList<>();
        for (UUID playerUuid : session.playerUuidList()) {
            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
            if (playerRef == null) {
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null || !canPlayerSeeIcons(session, playerUuid, player)) {
                continue;
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }
            viewers.add(new GhostViewer(playerUuid, new Vector3d(transform.getPosition())));
        }
        viewers.sort(Comparator.comparing(v -> v.uuid));
        return viewers;
    }

    private static void clearSession(@Nonnull UUID sessionId, @Nonnull World world) {
        SessionIcons icons = SESSIONS.remove(sessionId);
        if (icons == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (IconSpawn spawn : icons.spawns) {
            despawnEntity(store, icons, spawn);
        }
    }

    private static void despawnEntity(
        @Nonnull Store<EntityStore> store,
        @Nonnull SessionIcons icons,
        @Nonnull IconSpawn spawn
    ) {
        if (spawn.entityRef != null && spawn.entityRef.isValid()) {
            store.removeEntity(spawn.entityRef, RemoveReason.REMOVE);
        }
        if (spawn.entityRef != null) {
            icons.activeRefs.remove(spawn.entityRef);
        }
        spawn.entityRef = null;
        spawn.basePosition = null;
    }

    private static final class SessionIcons {
        private final List<IconSpawn> spawns = new ArrayList<>();
        private final Set<Ref<EntityStore>> activeRefs = new ReferenceOpenHashSet<>();
    }

    private static final class IconSpawn {
        @Nonnull
        private final PossessableMarker marker;
        @Nullable
        private Ref<EntityStore> entityRef;
        @Nullable
        private Vector3d basePosition;
        private double bobPhase;

        private IconSpawn(@Nonnull PossessableMarker marker) {
            this.marker = marker;
        }
    }

    private record GhostViewer(@Nonnull UUID uuid, @Nonnull Vector3d position) {}
}
