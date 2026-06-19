package com.hexvane.wraithbusters.player;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class HumanSprintService {
    private HumanSprintService() {}

    public static void apply(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(WraithBustersConstants.HUMAN_NO_SPRINT_EFFECT_ID);
        if (effect == null) {
            return;
        }
        EffectControllerComponent controller = accessor.getComponent(playerRef, EffectControllerComponent.getComponentType());
        if (controller != null) {
            controller.addEffect(playerRef, effect, accessor);
        }
    }

    public static void remove(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        int effectIndex = EntityEffect.getAssetMap().getIndex(WraithBustersConstants.HUMAN_NO_SPRINT_EFFECT_ID);
        if (effectIndex == Integer.MIN_VALUE) {
            return;
        }
        EffectControllerComponent controller = accessor.getComponent(playerRef, EffectControllerComponent.getComponentType());
        if (controller != null && controller.hasEffect(effectIndex)) {
            controller.removeEffect(playerRef, effectIndex, accessor);
        }
    }
}
