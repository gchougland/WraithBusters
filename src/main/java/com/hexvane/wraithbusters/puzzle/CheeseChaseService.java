package com.hexvane.wraithbusters.puzzle;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.arena.ArenaLayout;
import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.door.RoomProgressionService;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

public final class CheeseChaseService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<UUID, SessionMice> SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, MouseKind> ENTITY_BINDINGS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> COMPLETED = new ConcurrentHashMap<>();

    public enum MouseKind {
        SMALL,
        CHUMBO
    }

    private CheeseChaseService() {}

    @Nullable
    public static MouseKind findBinding(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull Store<EntityStore> store
    ) {
        UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return null;
        }
        return ENTITY_BINDINGS.get(uuidComponent.getUuid());
    }

    public static void resetForSession(@Nonnull GameSession session) {
        String prefix = session.getSessionId().toString();
        COMPLETED.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** Marks a cheese-chase room solved and clears mice without advancing progression. */
    public static void markRoomComplete(
        @Nonnull GameSession session,
        @Nonnull RoomDefinition room,
        @Nonnull World world
    ) {
        if (isCompleted(session, room)) {
            return;
        }
        COMPLETED.put(puzzleKey(session, room), Boolean.TRUE);
        clearSession(session.getSessionId(), world);
        stripCheeseFromHumans(session, world);
    }

    public static void startRound(@Nonnull GameSession session, @Nonnull World world) {
        resetForSession(session);
        clearSession(session.getSessionId(), world);
        onCurrentRoomChanged(session, world);
    }

    public static void endRound(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
    }

    public static void clearForLobby(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
    }

    public static void onCurrentRoomChanged(@Nonnull GameSession session, @Nonnull World world) {
        DeferredWorldTasks.run(world, () -> refreshForCurrentRoom(session, world));
    }

    public static boolean tryCatchMouse(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> mouseRef
    ) {
        if (session.getPhase() != GamePhase.ACTIVE) {
            return false;
        }
        PlayerSessionState state = playerState(session, playerRef, store);
        if (state == null || state.getRole() != PlayerRole.HUMAN || !state.isAlive()) {
            return false;
        }
        RoomDefinition currentRoom = RoomProgressionService.currentRoom(session);
        if (!WraithBustersConstants.CHEESE_CHASE_PUZZLE_ID.equals(currentRoom.getPuzzleId())) {
            return false;
        }
        if (isCompleted(session, currentRoom)) {
            return false;
        }
        if (findBinding(mouseRef, store) != MouseKind.SMALL) {
            return false;
        }
        SessionMice mice = SESSIONS.get(session.getSessionId());
        if (mice == null || !mice.activeRefs.contains(mouseRef)) {
            return false;
        }

        CombinedItemContainer inventory = InventoryComponent.getCombined(
            store,
            playerRef,
            InventoryComponent.EVERYTHING
        );
        ItemStack cheese = new ItemStack(WraithBustersConstants.CHEESE_ITEM_ID, 1);
        if (!inventory.canAddItemStack(cheese)) {
            send(store, playerRef, "server.wraithbusters.puzzle.cheeseChase.inventoryFull");
            return false;
        }
        inventory.addItemStack(cheese);
        send(store, playerRef, "server.wraithbusters.puzzle.cheeseChase.gotCheese");
        DeferredWorldTasks.run(world, () -> despawnMouse(session.getSessionId(), world, mouseRef));
        return true;
    }

    public static boolean tryFeedChumbo(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> chumboRef,
        @Nullable ItemStack heldItem
    ) {
        if (session.getPhase() != GamePhase.ACTIVE) {
            return false;
        }
        PlayerSessionState state = playerState(session, playerRef, store);
        if (state == null || state.getRole() != PlayerRole.HUMAN || !state.isAlive()) {
            return false;
        }
        RoomDefinition currentRoom = RoomProgressionService.currentRoom(session);
        if (!WraithBustersConstants.CHEESE_CHASE_PUZZLE_ID.equals(currentRoom.getPuzzleId())) {
            return false;
        }
        if (isCompleted(session, currentRoom)) {
            return false;
        }
        if (findBinding(chumboRef, store) != MouseKind.CHUMBO) {
            return false;
        }
        SessionMice mice = SESSIONS.get(session.getSessionId());
        if (mice == null || !mice.activeRefs.contains(chumboRef)) {
            return false;
        }
        if (heldItem == null
            || ItemStack.isEmpty(heldItem)
            || !WraithBustersConstants.CHEESE_ITEM_ID.equals(heldItem.getItemId())) {
            send(store, playerRef, "server.wraithbusters.puzzle.cheeseChase.needCheese");
            return false;
        }

        CombinedItemContainer inventory = InventoryComponent.getCombined(
            store,
            playerRef,
            InventoryComponent.EVERYTHING
        );
        if (!inventory.removeItemStack(heldItem.withQuantity(1), true, true).succeeded()) {
            send(store, playerRef, "server.wraithbusters.puzzle.cheeseChase.needCheese");
            return false;
        }

        mice.feedCount++;
        send(
            store,
            playerRef,
            "server.wraithbusters.puzzle.cheeseChase.fedChumbo",
            mice.feedCount,
            WraithBustersConstants.CHEESE_REQUIRED
        );
        if (mice.feedCount < WraithBustersConstants.CHEESE_REQUIRED) {
            return true;
        }

        DeferredWorldTasks.run(world, () -> completePuzzle(session, world, playerRef, store, currentRoom));
        return true;
    }

    private static void refreshForCurrentRoom(@Nonnull GameSession session, @Nonnull World world) {
        if (session.getPhase() != GamePhase.ACTIVE) {
            clearSession(session.getSessionId(), world);
            return;
        }
        RoomDefinition currentRoom = RoomProgressionService.currentRoom(session);
        if (!WraithBustersConstants.CHEESE_CHASE_PUZZLE_ID.equals(currentRoom.getPuzzleId())) {
            clearSession(session.getSessionId(), world);
            return;
        }
        if (isCompleted(session, currentRoom)) {
            clearSession(session.getSessionId(), world);
            return;
        }
        spawnForRoom(session, world);
    }

    private static void spawnForRoom(@Nonnull GameSession session, @Nonnull World world) {
        SessionMice mice = SESSIONS.computeIfAbsent(session.getSessionId(), ignored -> new SessionMice());
        if (mice.spawned) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }

        ArenaLayout layout = session.getArenaLayout();
        List<Transform> smallSpawns = layout.getCheeseChaseSmallMice();
        if (smallSpawns.size() < WraithBustersConstants.CHEESE_REQUIRED) {
            LOGGER.atWarning().log(
                "Cheese chase has %s small mouse marks but needs at least %s",
                smallSpawns.size(),
                WraithBustersConstants.CHEESE_REQUIRED
            );
        }
        for (Transform spawn : smallSpawns) {
            spawnMouse(store, mice, spawn, WraithBustersConstants.CHEESE_MOUSE_NPC_ROLE, MouseKind.SMALL);
        }
        Transform chumboSpawn = layout.getCheeseChaseChumbo();
        if (chumboSpawn != null) {
            spawnMouse(store, mice, chumboSpawn, WraithBustersConstants.CHUMBO_NPC_ROLE, MouseKind.CHUMBO);
        } else {
            LOGGER.atWarning().log("Cheese chase has no large_mouse mark for Chumbo");
        }
        mice.spawned = true;
    }

    private static void spawnMouse(
        @Nonnull Store<EntityStore> store,
        @Nonnull SessionMice mice,
        @Nonnull Transform transform,
        @Nonnull String roleId,
        @Nonnull MouseKind kind
    ) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            LOGGER.atWarning().log("NPCPlugin unavailable; cannot spawn cheese chase mouse");
            return;
        }
        Vector3d pos = transform.getPosition();
        Vector3d spawnPos = new Vector3d(pos.x, pos.y, pos.z);
        Rotation3f rotation = new Rotation3f(transform.getRotation());
        var pair = npc.spawnNPC(store, roleId, null, spawnPos, rotation);
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn cheese chase NPC at %s", spawnPos);
            return;
        }
        Ref<EntityStore> entityRef = pair.first();
        mice.lastSpawnedRef = entityRef;
        mice.activeRefs.add(entityRef);
        if (kind == MouseKind.CHUMBO) {
            mice.chumboRef = entityRef;
        }
        bindEntity(entityRef, store, kind);
    }

    private static void bindEntity(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull MouseKind kind
    ) {
        if (!entityRef.isValid()) {
            return;
        }
        UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
        if (uuidComponent != null) {
            ENTITY_BINDINGS.put(uuidComponent.getUuid(), kind);
        }
    }

    private static void despawnMouse(
        @Nonnull UUID sessionId,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> mouseRef
    ) {
        SessionMice mice = SESSIONS.get(sessionId);
        if (mice == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        if (findBinding(mouseRef, store) == MouseKind.SMALL) {
            spawnMousePoof(store, mouseRef);
        }
        removeEntity(store, mice, mouseRef);
    }

    private static void spawnMousePoof(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> mouseRef) {
        if (!mouseRef.isValid()) {
            return;
        }
        TransformComponent transform = store.getComponent(mouseRef, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        Vector3d pos = transform.getPosition();
        ParticleUtil.spawnParticleEffect(
            WraithBustersConstants.CHEESE_CHASE_MOUSE_POOF_PARTICLE,
            pos,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            WraithBustersConstants.CHEESE_CHASE_MOUSE_POOF_DURATION_SEC,
            store
        );
    }

    private static void completePuzzle(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull RoomDefinition room
    ) {
        if (isCompleted(session, room)) {
            return;
        }
        COMPLETED.put(puzzleKey(session, room), Boolean.TRUE);
        clearSession(session.getSessionId(), world);
        stripCheeseFromHumans(session, world);
        RoomProgressionService.advanceAfterPuzzle(session);
        KeySpawnService.spawnKeyForRoom(session, world, room);
        send(store, playerRef, "server.wraithbusters.puzzle.cheeseChase.complete");
        WraithBustersSoundUtil.play2d(playerRef, store, WraithBustersConstants.PUZZLE_SUCCESS_SOUND_EVENT);
        onCurrentRoomChanged(session, world);
    }

    private static void stripCheeseFromHumans(@Nonnull GameSession session, @Nonnull World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (UUID playerUuid : session.playerUuidList()) {
            PlayerSessionState state = session.getOrCreatePlayer(playerUuid);
            if (state.getRole() != PlayerRole.HUMAN) {
                continue;
            }
            PlayerRef player = Universe.get().getPlayer(playerUuid);
            if (player == null) {
                continue;
            }
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            CombinedItemContainer inventory = InventoryComponent.getCombined(
                store,
                ref,
                InventoryComponent.EVERYTHING
            );
            inventory.removeItemStack(
                new ItemStack(WraithBustersConstants.CHEESE_ITEM_ID, Integer.MAX_VALUE),
                true,
                true
            );
        }
    }

    private static void removeEntity(
        @Nonnull Store<EntityStore> store,
        @Nonnull SessionMice mice,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        if (entityRef.isValid()) {
            clearBinding(store, entityRef);
            store.removeEntity(entityRef, RemoveReason.REMOVE);
        }
        mice.activeRefs.remove(entityRef);
        if (entityRef.equals(mice.chumboRef)) {
            mice.chumboRef = null;
        }
    }

    private static void clearBinding(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> entityRef) {
        UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
        if (uuidComponent != null) {
            ENTITY_BINDINGS.remove(uuidComponent.getUuid());
        }
    }

    private static void clearSession(@Nonnull UUID sessionId, @Nonnull World world) {
        SessionMice mice = SESSIONS.remove(sessionId);
        if (mice == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        for (Ref<EntityStore> ref : new ArrayList<>(mice.activeRefs)) {
            removeEntity(store, mice, ref);
        }
    }

    private static boolean isCompleted(@Nonnull GameSession session, @Nonnull RoomDefinition room) {
        return COMPLETED.containsKey(puzzleKey(session, room));
    }

    @Nonnull
    private static String puzzleKey(@Nonnull GameSession session, @Nonnull RoomDefinition room) {
        return session.getSessionId() + ":" + room.getRoomId() + ":" + room.getPuzzleId();
    }

    @Nullable
    private static PlayerSessionState playerState(
        @Nonnull GameSession session,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null) {
            return null;
        }
        return session.getOrCreatePlayer(pr.getUuid());
    }

    private static void send(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef, @Nonnull String key) {
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation(key));
        }
    }

    private static void send(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull String key,
        int current,
        int total
    ) {
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(
                Message.translation(key)
                    .param("current", String.valueOf(current))
                    .param("total", String.valueOf(total))
            );
        }
    }

    private static final class SessionMice {
        private final Set<Ref<EntityStore>> activeRefs = new ReferenceOpenHashSet<>();
        @Nullable
        private Ref<EntityStore> chumboRef;
        @Nullable
        private Ref<EntityStore> lastSpawnedRef;
        private int feedCount;
        private boolean spawned;
    }
}
