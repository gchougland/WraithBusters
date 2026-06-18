package com.hexvane.wraithbusters.puzzle;

import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.door.DoorKeySymbols;
import com.hexvane.wraithbusters.door.RoomProgressionService;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.team.Team;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

public final class KeySpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ConcurrentHashMap<UUID, SessionKeys> SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Queue<PendingKeySpawn>> PENDING_SPAWNS = new ConcurrentHashMap<>();

    private KeySpawnService() {}

    public static void startRound(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
    }

    public static void endRound(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
    }

    public static void clearForLobby(@Nonnull GameSession session, @Nonnull World world) {
        clearSession(session.getSessionId(), world);
    }

    /** @return true when a key spawn was queued (symbol resolved for the next door) */
    public static boolean spawnKeyForRoom(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull RoomDefinition completedRoom
    ) {
        String symbol = resolveSpawnSymbol(session, completedRoom);
        if (symbol == null) {
            return false;
        }
        if (!DoorKeySymbols.isKnownSymbol(symbol)) {
            LOGGER.atWarning().log(
                "Skipping key spawn for completed room %s: unknown symbol %s",
                completedRoom.getRoomId(),
                symbol
            );
            return false;
        }
        UUID worldUuid = world.getWorldConfig().getUuid();
        PENDING_SPAWNS
            .computeIfAbsent(worldUuid, ignored -> new ConcurrentLinkedQueue<>())
            .add(new PendingKeySpawn(session.getSessionId(), completedRoom.getRoomId(), symbol));
        DeferredWorldTasks.run(world, () -> processPending(session, world));
        return true;
    }

    /** Drains queued key spawns; call only from the world task queue (see {@link com.hexvane.wraithbusters.game.GameTickSystem}). */
    public static void processPending(@Nonnull GameSession session, @Nonnull World world) {
        if (!DeferredWorldTasks.isStoreOpen(world)) {
            return;
        }
        UUID worldUuid = world.getWorldConfig().getUuid();
        PendingKeySpawn spawn;
        while ((spawn = pollPending(worldUuid)) != null) {
            if (!session.getSessionId().equals(spawn.sessionId())) {
                continue;
            }
            RoomDefinition room = RoomProgressionService.findRoom(session, spawn.roomId());
            if (room == null) {
                LOGGER.atWarning().log(
                    "Skipping key spawn: room %s is missing from arena layout",
                    spawn.roomId()
                );
                continue;
            }
            if (spawnKeyEntity(session, world.getEntityStore().getStore(), room, spawn.symbol())) {
                broadcastKeyFound(session, spawn.symbol());
            }
        }
    }

    @Nullable
    private static PendingKeySpawn pollPending(@Nonnull UUID worldUuid) {
        Queue<PendingKeySpawn> queue = PENDING_SPAWNS.get(worldUuid);
        if (queue == null) {
            return null;
        }
        PendingKeySpawn spawn = queue.poll();
        if (spawn != null && queue.isEmpty()) {
            PENDING_SPAWNS.remove(worldUuid, queue);
        }
        return spawn;
    }

    @Nullable
    private static String resolveSpawnSymbol(
        @Nonnull GameSession session,
        @Nonnull RoomDefinition completedRoom
    ) {
        List<String> chain = session.getActiveRoomChain();
        if (chain.isEmpty()) {
            return null;
        }
        int completedIndex = chain.indexOf(completedRoom.getRoomId());
        if (completedIndex < 0) {
            return null;
        }
        int nextIndex = completedIndex + 1;
        if (nextIndex >= chain.size()) {
            return null;
        }
        RoomDefinition doorRoom = RoomProgressionService.findRoom(session, chain.get(nextIndex));
        if (doorRoom == null) {
            LOGGER.atWarning().log(
                "Skipping key spawn for completed room %s: chain entry %s is missing from the arena layout",
                completedRoom.getRoomId(),
                chain.get(nextIndex)
            );
            return null;
        }
        return DoorKeySymbols.normalizeSymbolId(doorRoom.getSymbolId());
    }

    private static void broadcastKeyFound(@Nonnull GameSession session, @Nonnull String symbol) {
        String displaySymbol = DoorKeySymbols.displayName(symbol);
        for (UUID playerUuid : session.playerUuidList()) {
            if (session.getOrCreatePlayer(playerUuid).getTeam() != Team.HUMAN) {
                continue;
            }
            PlayerRef pr = Universe.get().getPlayer(playerUuid);
            if (pr != null) {
                pr.sendMessage(
                    Message.translation("server.wraithbusters.key.found").param("symbol", displaySymbol)
                );
            }
        }
    }

    private static boolean spawnKeyEntity(
        @Nonnull GameSession session,
        @Nonnull Store<EntityStore> store,
        @Nonnull RoomDefinition room,
        @Nonnull String symbol
    ) {
        SessionKeys keys = SESSIONS.computeIfAbsent(session.getSessionId(), ignored -> new SessionKeys());
        despawnTrackedKeys(store, keys);

        String itemId = DoorKeySymbols.itemIdForSymbol(symbol);
        ItemStack stack = new ItemStack(itemId, 1);
        if (!stack.isValid()) {
            LOGGER.atWarning().log(
                "Skipping key spawn for completed room %s: invalid item %s",
                room.getRoomId(),
                itemId
            );
            return false;
        }

        Vector3i blockPos = room.getKeySpawn();
        Vector3d position = new Vector3d(blockPos.x + 0.5, blockPos.y + 0.35, blockPos.z + 0.5);
        Holder<EntityStore> holder = ItemComponent.generateItemDrop(
            store, stack, position, Rotation3f.ZERO, 0.0F, 0.0F, 0.0F
        );
        if (holder == null) {
            LOGGER.atWarning().log(
                "Skipping key spawn for completed room %s: generateItemDrop returned null at %s",
                room.getRoomId(),
                blockPos
            );
            return false;
        }
        ItemComponent itemComponent = holder.getComponent(ItemComponent.getComponentType());
        if (itemComponent != null) {
            itemComponent.setPickupDelay(0.0F);
        }
        holder.addComponent(PreventPickup.getComponentType(), PreventPickup.INSTANCE);
        Ref<EntityStore> entityRef = store.addEntity(holder, AddReason.SPAWN);
        if (entityRef != null) {
            keys.activeRefs.add(entityRef);
            return true;
        }
        LOGGER.atWarning().log(
            "Skipping key spawn for completed room %s: addEntity returned null at %s",
            room.getRoomId(),
            blockPos
        );
        return false;
    }

    private static void despawnTrackedKeys(@Nonnull Store<EntityStore> store, @Nonnull SessionKeys keys) {
        for (Ref<EntityStore> ref : keys.activeRefs) {
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
        keys.activeRefs.clear();
    }

    private static void clearSession(@Nonnull UUID sessionId, @Nonnull World world) {
        SessionKeys keys = SESSIONS.remove(sessionId);
        UUID worldUuid = world.getWorldConfig().getUuid();
        PENDING_SPAWNS.remove(worldUuid);
        if (keys == null || !DeferredWorldTasks.isStoreOpen(world)) {
            return;
        }
        despawnTrackedKeys(world.getEntityStore().getStore(), keys);
    }

    private record PendingKeySpawn(@Nonnull UUID sessionId, @Nonnull String roomId, @Nonnull String symbol) {}

    private static final class SessionKeys {
        private final Set<Ref<EntityStore>> activeRefs = new ReferenceOpenHashSet<>();
    }
}
