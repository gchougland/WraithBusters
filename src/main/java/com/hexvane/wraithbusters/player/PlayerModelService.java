package com.hexvane.wraithbusters.player;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.game.GameSession;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class PlayerModelService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PlayerModelService() {}

    public static void applyGhostModel(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(WraithBustersConstants.GHOST_PLAYER_MODEL_ID);
        if (modelAsset == null) {
            LOGGER.atWarning().log(
                "Ghost player model asset not found: %s",
                WraithBustersConstants.GHOST_PLAYER_MODEL_ID
            );
            return;
        }
        Model model = Model.createScaledModel(modelAsset, WraithBustersConstants.GHOST_PLAYER_MODEL_SCALE);
        accessor.putComponent(playerRef, ModelComponent.getComponentType(), new ModelComponent(model));
    }

    public static void resetToPlayerSkin(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        PlayerSkinComponent skinComponent = accessor.getComponent(playerRef, PlayerSkinComponent.getComponentType());
        if (skinComponent == null) {
            return;
        }
        Model restored = CosmeticsModule.get().createModel(skinComponent.getPlayerSkin());
        if (restored == null) {
            return;
        }
        accessor.putComponent(playerRef, ModelComponent.getComponentType(), new ModelComponent(restored));
        skinComponent.setNetworkOutdated();
    }

    public static void resetSessionModels(@Nonnull GameSession session, @Nonnull World world) {
        for (UUID playerUuid : session.playerUuidList()) {
            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
            if (playerRef == null) {
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            resetToPlayerSkin(ref, world.getEntityStore().getStore());
        }
    }
}
