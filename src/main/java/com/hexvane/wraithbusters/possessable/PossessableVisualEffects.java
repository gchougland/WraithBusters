package com.hexvane.wraithbusters.possessable;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.arena.PossessableMarker;
import com.hexvane.wraithbusters.debug.GhostTestService;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Ghost-only continuous sparkle effects on possessable markers. Plate trails are model-attached particles. */
public final class PossessableVisualEffects {
    /** Block-center is +0.5; particle anchor was a full block too high at +0.58. */
    private static final double SPARKLE_Y_OFFSET = -0.42;
    private static final float SPARKLE_SPAWN_SCALE = 0.81f;
    private static final Set<String> ACTIVE_SPARKLES = new HashSet<>();

    private PossessableVisualEffects() {}

    public static void tick(@Nonnull GameSession session, @Nonnull World world, float dt) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        ensureSparklesForViewers(session, world, store);
    }

    public static void clear() {
        ACTIVE_SPARKLES.clear();
    }

    private static void ensureSparklesForViewers(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store
    ) {
        List<PossessableMarker> markers = session.getArenaLayout().getPossessables();
        if (markers.isEmpty()) {
            return;
        }
        for (PlayerRef viewer : world.getPlayerRefs()) {
            if (!canSeeSparkles(session, viewer)) {
                continue;
            }
            Ref<EntityStore> ref = viewer.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            UUID viewerUuid = viewer.getUuid();
            List<Ref<EntityStore>> viewers = List.of(ref);
            for (PossessableMarker marker : markers) {
                Vector3i blockPos = marker.getBlockPos();
                String sparkleKey = sparkleKey(session.getSessionId(), blockPos, viewerUuid);
                if (ACTIVE_SPARKLES.contains(sparkleKey)) {
                    continue;
                }
                Vector3d pos = new Vector3d(blockPos.x + 0.5, blockPos.y + SPARKLE_Y_OFFSET, blockPos.z + 0.5);
                ParticleUtil.spawnParticleEffect(
                    WraithBustersConstants.POSSESSABLE_SPARKLE_PARTICLE,
                    pos.x,
                    pos.y,
                    pos.z,
                    0f,
                    0f,
                    0f,
                    SPARKLE_SPAWN_SCALE,
                    null,
                    null,
                    viewers,
                    store
                );
                ACTIVE_SPARKLES.add(sparkleKey);
            }
        }
    }

    @Nonnull
    private static String sparkleKey(@Nonnull UUID sessionId, @Nonnull Vector3i blockPos, @Nonnull UUID viewerUuid) {
        return sessionId + ":" + blockPos.x + "," + blockPos.y + "," + blockPos.z + ":" + viewerUuid;
    }

    private static boolean canSeeSparkles(@Nonnull GameSession session, @Nonnull PlayerRef viewer) {
        UUID uuid = viewer.getUuid();
        if (GhostTestService.isActive(uuid)) {
            return true;
        }
        PlayerSessionState state = session.getPlayers().get(uuid);
        return state != null && state.getRole() == PlayerRole.GHOST;
    }
}
