package com.hexvane.wraithbusters.triggervolume;

import com.hypixel.hytale.builtin.triggervolumes.EntityTargetType;
import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerVolumeCodecs;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 * TriggerVolumes uses a tolerant effect decoder: if a custom effect fails to load (unknown type,
 * bad enum field, etc.) it is skipped silently and the volume ends up with zero effects.
 * That looks exactly like "items never consumed, no feedback" in-game.
 */
public final class OfferingVolumeRepairService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String TEMPLATE_RESOURCE =
        "/Server/Instances/WraithBusters_Mansion/resources/TriggerVolumeData.json";

    private OfferingVolumeRepairService() {}

    public static void repairIfNeeded(@Nonnull World world) {
        TriggerVolumesPlugin triggerVolumes = TriggerVolumesPlugin.get();
        if (triggerVolumes == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        TriggerVolumeManager manager = store.getResource(triggerVolumes.getManagerResourceType());
        if (manager == null) {
            return;
        }

        Map<String, List<TriggerEffect>> templateEffects = loadTemplateEffects();
        if (templateEffects.isEmpty()) {
            return;
        }

        for (VolumeEntry live : manager.getVolumes()) {
            List<TriggerEffect> template = templateEffects.get(live.getId());
            if (template == null || template.isEmpty()) {
                continue;
            }
            if (!live.getTargetTypes().contains(EntityTargetType.ITEM_DROP)) {
                continue;
            }
            if (alreadySynced(live, template)) {
                continue;
            }
            int previousEffectCount = live.getEffects().size();
            live.getEffects().clear();
            live.getEffects().addAll(TriggerEffect.deepCopyList(template));
            live.setKeepLoaded(true);
            LOGGER.atWarning().log(
                "Synced offering effects on volume %s in world %s (replaced %d effect(s) with %d from template)",
                live.getId(),
                world.getName(),
                previousEffectCount,
                template.size()
            );
        }
    }

    private static boolean alreadySynced(@Nonnull VolumeEntry live, @Nonnull List<TriggerEffect> template) {
        if (live.getEffects().size() != template.size()) {
            return false;
        }
        for (TriggerEffect effect : live.getEffects()) {
            if (!(effect instanceof WraithBustersOfferingEffect offering) || !offering.isProperlyConfigured()) {
                return false;
            }
        }
        return live.getEffects().stream().anyMatch(WraithBustersOfferingEffect.class::isInstance);
    }

    @Nonnull
    private static Map<String, List<TriggerEffect>> loadTemplateEffects() {
        Map<String, List<TriggerEffect>> effectsByVolume = new HashMap<>();
        try (InputStream stream = OfferingVolumeRepairService.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (stream == null) {
                LOGGER.atWarning().log("Offering volume template missing from mod resources: %s", TEMPLATE_RESOURCE);
                return effectsByVolume;
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            BsonDocument root = BsonDocument.parse(json);
            BsonDocument volumeDocs = root.getDocument("Volumes");
            if (volumeDocs == null) {
                return effectsByVolume;
            }
            ExtraInfo extraInfo = ExtraInfo.THREAD_LOCAL.get();
            for (Map.Entry<String, BsonValue> entry : volumeDocs.entrySet()) {
                if (!(entry.getValue() instanceof BsonDocument doc)) {
                    continue;
                }
                BsonValue effectsValue = doc.get("Effects");
                if (effectsValue == null) {
                    continue;
                }
                try {
                    TriggerEffect[] decoded = TriggerVolumeCodecs.TOLERANT_EFFECTS.decode(effectsValue, extraInfo);
                    List<TriggerEffect> offeringEffects = new ArrayList<>();
                    for (TriggerEffect effect : decoded) {
                        if (effect instanceof WraithBustersOfferingEffect) {
                            offeringEffects.add(effect);
                        }
                    }
                    if (!offeringEffects.isEmpty()) {
                        effectsByVolume.put(entry.getKey(), offeringEffects);
                    } else {
                        LOGGER.atWarning().log(
                            "Offering template volume %s has no decodable WraithBusters_Offering effects (had %d effect entries)",
                            entry.getKey(),
                            decoded.length
                        );
                    }
                } catch (Exception decodeError) {
                    LOGGER.atWarning().withCause(decodeError).log(
                        "Failed to decode offering template effects for volume %s",
                        entry.getKey()
                    );
                }
            }
        } catch (IOException ioError) {
            LOGGER.atWarning().withCause(ioError).log("Failed to read offering volume template");
        }
        return effectsByVolume;
    }
}
