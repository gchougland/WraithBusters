package com.hexvane.wraithbusters.triggervolume;

import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.builtin.triggervolumes.shape.BoxShape;
import com.hypixel.hytale.builtin.triggervolumes.shape.TriggerVolumeShape;
import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.door.RoomProgressionService;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.puzzle.CheeseChaseService;
import com.hexvane.wraithbusters.puzzle.KeySpawnService;
import com.hexvane.wraithbusters.puzzle.LibraryBookService;
import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public final class OfferingPuzzleService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float EJECT_PICKUP_DELAY_SEC = 2.0f;
    private static final double EJECT_OUTSIDE_MARGIN = 0.75;
    private static final ConcurrentHashMap<String, SlotState> SLOTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> COMPLETED = new ConcurrentHashMap<>();

    private OfferingPuzzleService() {}

    public static void resetForSession(@Nonnull GameSession session) {
        String prefix = session.getSessionId().toString();
        SLOTS.keySet().removeIf(key -> key.startsWith(prefix));
        COMPLETED.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public static void handleInsert(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> itemRef,
        @Nonnull VolumeEntry volume,
        @Nonnull String roomId,
        @Nonnull OfferingMode mode,
        @Nonnull String[] requiredItems,
        int slotCapacity,
        @Nonnull Vector3d ejectPosition,
        @Nonnull Vector3d ejectVelocity,
        @Nonnull OfferingFeedbackConfig feedback
    ) {
        String volumeId = volume.getId();
        if (requiredItems.length == 0) {
            OfferingPlayerNotify.sessionPlayers(session, world, "server.wraithbusters.puzzle.offering.misconfigured");
            OfferingPlayerNotify.logBlocked("misconfigured", volumeId, roomId, "RequiredItems is empty");
            return;
        }
        if (session.getPhase() != GamePhase.ACTIVE) {
            OfferingPlayerNotify.sessionPlayers(session, world, "server.wraithbusters.puzzle.offering.notActive");
            OfferingPlayerNotify.logBlocked("notActive", volumeId, roomId, "phase=" + session.getPhase());
            return;
        }
        RoomDefinition currentRoom = RoomProgressionService.currentRoom(session);
        if (!roomId.equals(currentRoom.getRoomId())) {
            OfferingPlayerNotify.sessionPlayers(
                session,
                world,
                "server.wraithbusters.puzzle.offering.wrongRoom",
                "expected",
                roomId,
                "current",
                currentRoom.getRoomId()
            );
            OfferingPlayerNotify.logBlocked(
                "wrongRoom",
                volumeId,
                roomId,
                "current=" + currentRoom.getRoomId() + " chain=" + session.getActiveRoomChain()
            );
            return;
        }
        String puzzleKey = puzzleKey(session, volumeId);
        if (COMPLETED.containsKey(puzzleKey)) {
            OfferingPlayerNotify.logBlocked("alreadyComplete", volumeId, roomId, puzzleKey);
            return;
        }
        int capacity = slotCapacity > 0 ? slotCapacity : requiredItems.length;
        if (capacity != requiredItems.length) {
            capacity = requiredItems.length;
        }

        ItemComponent itemComponent = store.getComponent(itemRef, ItemComponent.getComponentType());
        if (itemComponent == null) {
            OfferingPlayerNotify.logBlocked("notItemDrop", volumeId, roomId, "entity has no ItemComponent");
            return;
        }
        ItemStack stack = itemComponent.getItemStack();
        if (ItemStack.isEmpty(stack) || !stack.isValid()) {
            OfferingPlayerNotify.logBlocked("emptyItem", volumeId, roomId, "invalid ItemStack");
            return;
        }

        SlotState state = SLOTS.computeIfAbsent(puzzleKey, ignored -> new SlotState());
        if (state.insertCount >= capacity) {
            OfferingPlayerNotify.logBlocked("slotFull", volumeId, roomId, "waiting for batch evaluation");
            return;
        }

        ItemStack stashed = new ItemStack(stack.getItemId(), stack.getQuantity());
        store.removeEntity(itemRef, RemoveReason.REMOVE);
        state.stash.add(stashed);
        state.insertCount++;
        playInsertFeedback(world, store, ejectPosition, feedback);

        if (state.insertCount < capacity) {
            LOGGER.atInfo().log(
                "Offering progress on %s: %d/%d item=%s",
                volumeId,
                state.insertCount,
                capacity,
                stashed.getItemId()
            );
            OfferingPlayerNotify.sessionPlayers(
                session,
                world,
                "server.wraithbusters.puzzle.offering.progress",
                "count",
                String.valueOf(state.insertCount),
                "total",
                String.valueOf(capacity)
            );
            return;
        }

        boolean solved = evaluate(mode, state.stash, requiredItems, capacity);
        if (solved) {
            LOGGER.atInfo().log("Offering solved on %s", volumeId);
            COMPLETED.put(puzzleKey, Boolean.TRUE);
            SLOTS.remove(puzzleKey);
            completePuzzle(session, world, store, currentRoom, ejectPosition, feedback);
        } else {
            LOGGER.atInfo().log(
                "Offering rejected on %s: offered=%s required=%s",
                volumeId,
                itemIds(state.stash),
                Arrays.toString(requiredItems)
            );
            ejectItems(world, store, volume, state.stash, ejectVelocity);
            SLOTS.remove(puzzleKey);
            OfferingItemDropTickSystem.onVolumeRejected(world.getWorldConfig().getUuid(), volumeId);
            playRejectFeedback(world, store, ejectPosition, feedback);
            OfferingPlayerNotify.sessionPlayers(session, world, "server.wraithbusters.puzzle.offering.wrong");
        }
    }

    private static boolean evaluate(
        @Nonnull OfferingMode mode,
        @Nonnull List<ItemStack> stash,
        @Nonnull String[] requiredItems,
        int slotCapacity
    ) {
        if (stash.size() != slotCapacity || requiredItems.length != slotCapacity) {
            return false;
        }
        if (mode == OfferingMode.ORDERED) {
            for (int i = 0; i < slotCapacity; i++) {
                if (!requiredItems[i].equals(stash.get(i).getItemId())) {
                    return false;
                }
            }
            return true;
        }
        return multisetEquals(itemIds(stash), List.of(requiredItems));
    }

    @Nonnull
    private static List<String> itemIds(@Nonnull List<ItemStack> stacks) {
        List<String> ids = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            ids.add(stack.getItemId());
        }
        return ids;
    }

    private static boolean multisetEquals(@Nonnull List<String> left, @Nonnull List<String> right) {
        Map<String, Integer> leftCounts = countIds(left);
        Map<String, Integer> rightCounts = countIds(right);
        return leftCounts.equals(rightCounts);
    }

    @Nonnull
    private static Map<String, Integer> countIds(@Nonnull List<String> ids) {
        Map<String, Integer> counts = new HashMap<>();
        for (String id : ids) {
            counts.merge(id, 1, Integer::sum);
        }
        return counts;
    }

    private static void completePuzzle(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull RoomDefinition room,
        @Nonnull Vector3d feedbackPosition,
        @Nonnull OfferingFeedbackConfig feedback
    ) {
        RoomProgressionService.advanceAfterPuzzle(session);
        KeySpawnService.spawnKeyForRoom(session, world, room);
        OfferingPlayerNotify.sessionPlayers(session, world, "server.wraithbusters.puzzle.offering.complete");
        playSuccessFeedback(world, store, feedbackPosition, feedback);
        CheeseChaseService.onCurrentRoomChanged(session, world);
        LibraryBookService.onCurrentRoomChanged(session, world);
    }

    private static void playInsertFeedback(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d position,
        @Nonnull OfferingFeedbackConfig feedback
    ) {
        if (feedback.hasInsertParticle()) {
            spawnFeedbackParticles(
                store,
                position,
                feedback.insertParticleOffset(),
                feedback.insertParticleSystem(),
                feedback.resolveInsertParticleDuration()
            );
        }
        if (feedback.hasInsertSound()) {
            playOfferingSound(
                world,
                position,
                feedback.resolveInsertSoundEvent(),
                feedback.resolveInsertSoundVolume()
            );
        }
    }

    private static void playRejectFeedback(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d position,
        @Nonnull OfferingFeedbackConfig feedback
    ) {
        spawnFeedbackParticles(
            store,
            position,
            feedback.rejectParticleOffset(),
            feedback.resolveRejectParticleSystem(),
            feedback.resolveRejectParticleDuration()
        );
        playOfferingSound(
            world,
            position,
            feedback.resolveRejectSoundEvent(),
            feedback.resolveRejectSoundVolume()
        );
    }

    private static void playSuccessFeedback(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d position,
        @Nonnull OfferingFeedbackConfig feedback
    ) {
        spawnFeedbackParticles(
            store,
            position,
            feedback.successParticleOffset(),
            feedback.resolveSuccessParticleSystem(),
            feedback.resolveSuccessParticleDuration()
        );
        playOfferingSound(
            world,
            position,
            feedback.resolveSuccessSoundEvent(),
            feedback.resolveSuccessSoundVolume()
        );
    }

    private static void playOfferingSound(
        @Nonnull World world,
        @Nonnull Vector3d position,
        @Nonnull String soundEventId,
        float volumeModifier
    ) {
        if (soundEventId.isBlank()) {
            return;
        }
        WraithBustersSoundUtil.play3dAtPosition(
            world,
            position.x,
            position.y,
            position.z,
            soundEventId,
            volumeModifier,
            1.0f
        );
    }

    private static void spawnFeedbackParticles(
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d position,
        @Nonnull Vector3d offset,
        @Nonnull String particleSystem,
        float duration
    ) {
        if (particleSystem.isBlank()) {
            return;
        }
        Vector3d spawnPosition = new Vector3d(position).add(offset);
        ParticleUtil.spawnParticleEffect(
            particleSystem,
            spawnPosition,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            duration,
            store
        );
    }

    private static void ejectItems(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull VolumeEntry volume,
        @Nonnull List<ItemStack> stash,
        @Nonnull Vector3d velocity
    ) {
        double spread = 0.35;
        for (int i = 0; i < stash.size(); i++) {
            ItemStack stack = stash.get(i);
            if (ItemStack.isEmpty(stack) || !stack.isValid()) {
                continue;
            }
            double offsetX = (i - 1) * spread;
            Vector3d dropPos = ejectSpawnOutside(volume, velocity, offsetX);
            Holder<EntityStore> holder = ItemComponent.generateItemDrop(
                store,
                stack,
                dropPos,
                Rotation3f.ZERO,
                (float) velocity.x,
                (float) velocity.y,
                (float) velocity.z
            );
            if (holder != null) {
                ItemComponent itemComponent = holder.getComponent(ItemComponent.getComponentType());
                if (itemComponent != null) {
                    itemComponent.setPickupDelay(EJECT_PICKUP_DELAY_SEC);
                }
                Ref<EntityStore> spawned = store.addEntity(holder, AddReason.SPAWN);
                if (spawned != null && spawned.isValid()) {
                    UUIDComponent uuidComponent = store.getComponent(spawned, UUIDComponent.getComponentType());
                    if (uuidComponent != null) {
                        OfferingItemDropTickSystem.registerIgnoredItem(uuidComponent.getUuid());
                    }
                }
            }
        }
    }

    @Nonnull
    private static Vector3d ejectSpawnOutside(
        @Nonnull VolumeEntry volume,
        @Nonnull Vector3d velocity,
        double lateralOffset
    ) {
        Vector3d origin = volume.getPosition();
        TriggerVolumeShape shape = volume.getShape();
        if (shape instanceof BoxShape box) {
            Vector3d min = new Vector3d();
            Vector3d max = new Vector3d();
            box.getWorldAABB(origin, min, max);
            double centerX = (min.x + max.x) * 0.5;
            double centerY = (min.y + max.y) * 0.5;
            double centerZ = (min.z + max.z) * 0.5;
            double absX = Math.abs(velocity.x);
            double absY = Math.abs(velocity.y);
            double absZ = Math.abs(velocity.z);
            double spawnX = centerX + lateralOffset;
            double spawnY = centerY;
            double spawnZ = centerZ;
            if (absX >= absY && absX >= absZ) {
                spawnX = velocity.x >= 0.0 ? max.x + EJECT_OUTSIDE_MARGIN : min.x - EJECT_OUTSIDE_MARGIN;
                spawnX += lateralOffset;
            } else if (absY >= absZ) {
                spawnY = velocity.y >= 0.0 ? max.y + EJECT_OUTSIDE_MARGIN : min.y - EJECT_OUTSIDE_MARGIN;
            } else {
                spawnZ = velocity.z >= 0.0 ? max.z + EJECT_OUTSIDE_MARGIN : min.z - EJECT_OUTSIDE_MARGIN;
            }
            return new Vector3d(spawnX, spawnY, spawnZ);
        }
        Vector3d fallback = new Vector3d(origin);
        if (velocity.lengthSquared() > 1.0E-6) {
            Vector3d outward = new Vector3d(velocity).normalize().mul(EJECT_OUTSIDE_MARGIN + shape.getMaxDistanceFromOrigin());
            fallback.add(outward);
        } else {
            fallback.y += EJECT_OUTSIDE_MARGIN;
        }
        fallback.x += lateralOffset;
        return fallback;
    }

    @Nonnull
    private static String puzzleKey(@Nonnull GameSession session, @Nonnull String volumeId) {
        return session.getSessionId() + ":" + volumeId;
    }

    private static final class SlotState {
        private final List<ItemStack> stash = new ArrayList<>();
        private int insertCount;
    }
}
