package com.hexvane.wraithbusters.triggervolume;

import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerContext;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class ConsumeDroppedItemEffect extends TriggerEffect {
    @Nonnull
    public static final BuilderCodec<ConsumeDroppedItemEffect> CODEC = BuilderCodec.builder(
            ConsumeDroppedItemEffect.class, ConsumeDroppedItemEffect::new, BASE_CODEC
        )
        .build();

    @Override
    public void execute(@Nonnull TriggerContext context) {
        Ref<EntityStore> entityRef = context.getEntityRef();
        Store<EntityStore> store = context.getStore();
        if (!entityRef.isValid() || store.getComponent(entityRef, ItemComponent.getComponentType()) == null) {
            return;
        }
        store.removeEntity(entityRef, RemoveReason.REMOVE);
    }
}
