package com.hexvane.wraithbusters.triggervolume;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
import javax.annotation.Nonnull;

public final class TriggerVolumeRegistration {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private TriggerVolumeRegistration() {}

    public static void register() {
        TriggerVolumesPlugin plugin = TriggerVolumesPlugin.get();
        if (plugin == null) {
            LOGGER.atWarning().log("TriggerVolumes plugin not loaded; WraithBusters offering volumes unavailable");
            return;
        }
        plugin.registerConditionType("WraithBusters_DroppedItem", DroppedItemCondition.class, DroppedItemCondition.CODEC);
        plugin.registerEffectType("WraithBusters_ConsumeDroppedItem", ConsumeDroppedItemEffect.class, ConsumeDroppedItemEffect.CODEC);
        plugin.registerEffectType("WraithBusters_Offering", WraithBustersOfferingEffect.class, WraithBustersOfferingEffect.CODEC);
        plugin.registerAssetField("WraithBusters_DroppedItem", "Item", "Item");
        plugin.registerAssetField("WraithBusters_Offering", "RequiredItems", "Item");
        plugin.registerAssetField("WraithBusters_Offering", "InsertParticleSystem", "ParticleSystem");
        plugin.registerAssetField("WraithBusters_Offering", "RejectParticleSystem", "ParticleSystem");
        plugin.registerAssetField("WraithBusters_Offering", "SuccessParticleSystem", "ParticleSystem");
        plugin.registerAssetField("WraithBusters_Offering", "InsertSoundEvent", "SoundEvent");
        plugin.registerAssetField("WraithBusters_Offering", "RejectSoundEvent", "SoundEvent");
        plugin.registerAssetField("WraithBusters_Offering", "SuccessSoundEvent", "SoundEvent");
    }
}
