package com.hexvane.wraithbusters.util;

import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.team.Team;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class WraithBustersSoundUtil {
    private WraithBustersSoundUtil() {}

    public static void play2d(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull String soundEventId
    ) {
        int soundIndex = SoundEvent.getAssetMap().getIndex(soundEventId);
        if (soundIndex == Integer.MIN_VALUE) {
            return;
        }
        SoundUtil.playSoundEvent2d(playerRef, soundIndex, SoundCategory.SFX, store);
    }

    public static void play2dForSessionHumans(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull String soundEventId
    ) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            UUID playerUuid = playerRef.getUuid();
            if (!session.getPlayers().containsKey(playerUuid)) {
                continue;
            }
            var playerState = session.getOrCreatePlayer(playerUuid);
            if (playerState.getTeam() != Team.HUMAN || playerState.getRole() != PlayerRole.HUMAN || !playerState.isAlive()) {
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                play2d(ref, store, soundEventId);
            }
        }
    }

    public static void play3dAtBlock(@Nonnull World world, @Nonnull Vector3i blockPos, @Nonnull String soundEventId) {
        play3dAtPosition(world, blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5, soundEventId);
    }

    public static void play3dAtEntity(
        @Nonnull World world,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull String soundEventId
    ) {
        if (!entityRef.isValid()) {
            return;
        }
        TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        Vector3d pos = transform.getPosition();
        play3dAtPosition(world, pos.x, pos.y, pos.z, soundEventId);
    }

    public static void play3dAtPosition(
        @Nonnull World world,
        double x,
        double y,
        double z,
        @Nonnull String soundEventId
    ) {
        play3dAtPosition(world, x, y, z, soundEventId, 1.0f, 1.0f);
    }

    public static void play3dAtPosition(
        @Nonnull World world,
        double x,
        double y,
        double z,
        @Nonnull String soundEventId,
        float volumeModifier,
        float pitchModifier
    ) {
        int soundIndex = SoundEvent.getAssetMap().getIndex(soundEventId);
        if (soundIndex == Integer.MIN_VALUE) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        SoundUtil.playSoundEvent3d(
            soundIndex,
            SoundCategory.SFX,
            x,
            y,
            z,
            volumeModifier,
            pitchModifier,
            store
        );
    }

    public static void playBlockStateSound(
        @Nonnull World world,
        @Nonnull Vector3i blockPos,
        @Nullable BlockType blockType,
        @Nonnull String stateName
    ) {
        if (blockType == null) {
            return;
        }
        BlockType stateBlock = blockType.getBlockForState(stateName);
        if (stateBlock == null) {
            return;
        }
        int soundIndex = stateBlock.getInteractionSoundEventIndex();
        if (soundIndex == Integer.MIN_VALUE) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        SoundUtil.playSoundEvent3d(
            soundIndex,
            SoundCategory.SFX,
            blockPos.x + 0.5,
            blockPos.y + 0.5,
            blockPos.z + 0.5,
            store
        );
    }
}
