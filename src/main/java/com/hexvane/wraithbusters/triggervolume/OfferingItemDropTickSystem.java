package com.hexvane.wraithbusters.triggervolume;

import com.hypixel.hytale.builtin.triggervolumes.EntityTargetType;
import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerContext;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEventType;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.builtin.triggervolumes.shape.TriggerVolumeShape;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.OrderPriority;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.system.ItemSpatialSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * TriggerVolumes ticks ITEM_DROP targets against the entity spatial index, but dropped items are
 * {@link com.hypixel.hytale.server.core.modules.entity.component.Intangible} and only indexed in
 * the item spatial tree. This system bridges that gap for {@link WraithBustersOfferingEffect}.
 */
public final class OfferingItemDropTickSystem extends TickingSystem<EntityStore> {
    private static final float EJECTED_ITEM_IGNORE_SEC = 3.0f;

    private static final ConcurrentHashMap<String, Set<UUID>> ITEMS_INSIDE_VOLUME = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> IGNORED_ITEM_UNTIL_NANOS = new ConcurrentHashMap<>();

    public static void onVolumeRejected(@Nonnull UUID worldUuid, @Nonnull String volumeId) {
        ITEMS_INSIDE_VOLUME.remove(worldUuid + ":" + volumeId);
    }

    public static void registerIgnoredItem(@Nonnull UUID itemUuid) {
        IGNORED_ITEM_UNTIL_NANOS.put(
            itemUuid,
            System.nanoTime() + (long) (EJECTED_ITEM_IGNORE_SEC * 1_000_000_000L)
        );
    }

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemDependency<>(Order.AFTER, ItemSpatialSystem.class, OrderPriority.CLOSEST)
    );

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        TriggerVolumesPlugin triggerVolumes = TriggerVolumesPlugin.get();
        if (triggerVolumes == null) {
            return;
        }
        TriggerVolumeManager manager = store.getResource(triggerVolumes.getManagerResourceType());
        if (manager == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        SpatialResource<Ref<EntityStore>, EntityStore> itemSpatial = store.getResource(
            EntityModule.get().getItemSpatialResourceType()
        );
        if (itemSpatial == null) {
            return;
        }

        UUID worldUuid = world.getWorldConfig().getUuid();
        List<Ref<EntityStore>> candidates = SpatialResource.getThreadLocalReferenceList();

        for (VolumeEntry volume : manager.getVolumes()) {
            if (!volume.isEnabled() || !volume.getTargetTypes().contains(EntityTargetType.ITEM_DROP)) {
                continue;
            }
            if (!hasOfferingEnterEffect(volume)) {
                continue;
            }
            if (!volume.isKeepLoaded() && !isChunkLoaded(world, volume.getPosition())) {
                continue;
            }

            String trackKey = worldUuid + ":" + volume.getId();
            pruneExpiredIgnores();
            Set<UUID> previousInside = ITEMS_INSIDE_VOLUME.getOrDefault(trackKey, Set.of());
            Set<UUID> insideNow = new HashSet<>();

            TriggerVolumeShape shape = volume.getShape();
            Vector3d origin = volume.getPosition();
            candidates.clear();
            itemSpatial.getSpatialStructure().collect(origin, shape.getMaxDistanceFromOrigin(), candidates);

            for (Ref<EntityStore> itemRef : candidates) {
                if (!itemRef.isValid()) {
                    continue;
                }
                if (store.getComponent(itemRef, ItemComponent.getComponentType()) == null) {
                    continue;
                }
                TransformComponent transform = store.getComponent(itemRef, TransformComponent.getComponentType());
                if (transform == null || !isInsideVolume(shape, origin, transform, store, itemRef)) {
                    continue;
                }
                UUIDComponent uuidComponent = store.getComponent(itemRef, UUIDComponent.getComponentType());
                if (uuidComponent == null) {
                    continue;
                }
                UUID itemUuid = uuidComponent.getUuid();
                if (isIgnoredItem(itemUuid)) {
                    continue;
                }
                insideNow.add(itemUuid);
                if (!previousInside.contains(itemUuid)) {
                    fireOfferingEnterEffects(volume, itemRef, store);
                }
            }

            if (insideNow.isEmpty()) {
                ITEMS_INSIDE_VOLUME.remove(trackKey);
            } else {
                ITEMS_INSIDE_VOLUME.put(trackKey, insideNow);
            }
        }
    }

    private static boolean isIgnoredItem(@Nonnull UUID itemUuid) {
        Long until = IGNORED_ITEM_UNTIL_NANOS.get(itemUuid);
        if (until == null) {
            return false;
        }
        if (System.nanoTime() >= until) {
            IGNORED_ITEM_UNTIL_NANOS.remove(itemUuid);
            return false;
        }
        return true;
    }

    private static void pruneExpiredIgnores() {
        long now = System.nanoTime();
        IGNORED_ITEM_UNTIL_NANOS.entrySet().removeIf(entry -> now >= entry.getValue());
    }

    private static boolean hasOfferingEnterEffect(@Nonnull VolumeEntry volume) {
        for (TriggerEffect effect : volume.getEffects()) {
            if (effect instanceof WraithBustersOfferingEffect && effect.getEventType() == TriggerEventType.ENTER) {
                return true;
            }
        }
        return false;
    }

    private static void fireOfferingEnterEffects(
        @Nonnull VolumeEntry volume,
        @Nonnull Ref<EntityStore> itemRef,
        @Nonnull Store<EntityStore> store
    ) {
        TriggerContext context = new TriggerContext(itemRef, store, TriggerEventType.ENTER, volume);
        for (TriggerEffect effect : volume.getEffects()) {
            if (!(effect instanceof WraithBustersOfferingEffect) || effect.getEventType() != TriggerEventType.ENTER) {
                continue;
            }
            effect.execute(context);
        }
    }

    private static boolean isInsideVolume(
        @Nonnull TriggerVolumeShape shape,
        @Nonnull Vector3d origin,
        @Nonnull TransformComponent transform,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> itemRef
    ) {
        Vector3d pos = transform.getPosition();
        if (shape.contains(origin, pos)) {
            return true;
        }
        BoundingBox boundingBox = store.getComponent(itemRef, BoundingBox.getComponentType());
        double height = boundingBox != null ? boundingBox.getBoundingBox().height() : 0.5;
        if (height <= 0.0) {
            return false;
        }
        return shape.contains(origin, new Vector3d(pos.x, pos.y + height * 0.5, pos.z));
    }

    private static boolean isChunkLoaded(@Nonnull World world, @Nonnull Vector3d position) {
        long idx = ChunkUtil.indexChunkFromBlock(position.x, position.z);
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(idx);
        if (chunkRef == null || !chunkRef.isValid()) {
            return false;
        }
        WorldChunk worldChunk = chunkStore.getStore().getComponent(chunkRef, WorldChunk.getComponentType());
        return worldChunk != null && worldChunk.is(ChunkFlag.TICKING);
    }
}
