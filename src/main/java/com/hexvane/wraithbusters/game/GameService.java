package com.hexvane.wraithbusters.game;

import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.arena.ArenaLayout;
import com.hexvane.wraithbusters.arena.ArenaLayoutStore;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.debug.GhostTestService;
import com.hexvane.wraithbusters.door.RoomDoorService;
import com.hexvane.wraithbusters.door.RoomProgressionService;
import com.hexvane.wraithbusters.instance.GameInstanceService;
import com.hexvane.wraithbusters.util.WorldThreadTasks;
import com.hexvane.wraithbusters.ghost.PhasePortalMarkerService;
import com.hexvane.wraithbusters.portrait.SlothPortraitService;
import com.hexvane.wraithbusters.pickup.ManaPickupService;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.team.Team;
import com.hexvane.wraithbusters.team.TeamAssigner;
import com.hexvane.wraithbusters.team.TeamSetupService;
import com.hexvane.wraithbusters.puzzle.KeySpawnService;
import com.hexvane.wraithbusters.puzzle.PuzzleService;
import com.hexvane.wraithbusters.ui.GhostManaHudSupport;
import com.hexvane.wraithbusters.ui.LobbyStatusHudSupport;
import com.hexvane.wraithbusters.ui.RoundEndPage;
import com.hexvane.wraithbusters.ui.RoundTimerHudSupport;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hexvane.wraithbusters.util.PlayerTeleportUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GameService {
    @Nonnull
    private final WraithBustersPlugin plugin;

    public GameService(@Nonnull WraithBustersPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    public CompletableFuture<GameSession> startGame(
        @Nonnull UUID hostUuid,
        @Nonnull World originWorld,
        @Nonnull Transform hostTransform,
        @Nullable String arenaId
    ) {
        WraithBustersPluginConfig config = plugin.getPluginConfig();
        String resolvedArena = arenaId == null || arenaId.isBlank() ? config.getDefaultArenaId() : arenaId.trim();
        ArenaLayout layout = ArenaLayoutStore.copyForRuntime(ArenaLayoutStore.loadOrDefault(plugin, resolvedArena));
        GameSession session = new GameSession(UUID.randomUUID(), hostUuid, resolvedArena, layout);
        session.setAwaitingFirstJoin(true);
        String worldKey = "wraithbusters-" + session.getSessionId().toString().substring(0, 8);
        return GameInstanceService.createGameWorld(originWorld, hostTransform, worldKey).thenApply(world -> {
            session.setWorldName(world.getName());
            session.setWorldUuid(world.getWorldConfig().getUuid());
            GameRegistry.get().register(session);
            GameRegistry.get().linkWorld(world.getWorldConfig().getUuid(), session);
            return session;
        });
    }

    public boolean joinSession(
        @Nonnull GameSession session,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Transform returnPoint
    ) {
        UUIDComponent uuid = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uuid == null || session.getWorldUuid() == null) {
            return false;
        }
        if (GameRegistry.get().getSession(session.getSessionId()) == null) {
            return false;
        }
        World instanceWorld = Universe.get().getWorld(session.getWorldUuid());
        if (!isInstanceJoinable(instanceWorld)) {
            return false;
        }
        GamePhase phase = session.getPhase();
        if (phase != GamePhase.LOBBY && phase != GamePhase.COUNTDOWN) {
            return false;
        }
        UUID playerUuid = uuid.getUuid();
        PlayerSessionState state = session.getOrCreatePlayer(playerUuid);
        state.setSavedReturnTransform(returnPoint);
        GameRegistry.get().linkPlayer(playerUuid, session);
        session.setAwaitingFirstJoin(false);
        CompletableFuture<World> loaded = CompletableFuture.completedFuture(instanceWorld);
        GameInstanceService.teleportToLoadingInstance(playerRef, store, loaded, returnPoint);
        DeferredWorldTasks.run(instanceWorld, () -> {
            SlothPortraitService.scanWorld(instanceWorld);
            Ref<EntityStore> liveRef = findPlayerRef(instanceWorld, playerUuid);
            if (liveRef != null) {
                teleportToLobby(session, liveRef, instanceWorld.getEntityStore().getStore());
            }
        });
        return true;
    }

    public static boolean isInstanceJoinable(@Nullable World instanceWorld) {
        return instanceWorld != null && instanceWorld.isAlive() && DeferredWorldTasks.isStoreOpen(instanceWorld);
    }

    public void teleportToLobby(
        @Nonnull GameSession session,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        World world = store.getExternalData().getWorld();
        if (session.getWorldUuid() != null && !session.getWorldUuid().equals(world.getWorldConfig().getUuid())) {
            return;
        }
        PlayerTeleportUtil.teleport(playerRef, store, world, session.getArenaLayout().getLobbySpawn());
        Player player = store.getComponent(playerRef, Player.getComponentType());
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player != null && pr != null && session.getPhase() == GamePhase.LOBBY) {
            LobbyStatusHudSupport.refreshAll(session, world);
        }
    }

    public void toggleReady(
        @Nonnull GameSession session,
        @Nonnull UUID playerUuid,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlayerSessionState state = session.getOrCreatePlayer(playerUuid);
        state.setReady(!state.isReady());
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            if (state.isReady()) {
                pr.sendMessage(Message.translation("server.wraithbusters.ready.on"));
            } else {
                pr.sendMessage(Message.translation("server.wraithbusters.ready.off"));
            }
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player != null && pr != null) {
            LobbyStatusHudSupport.refreshAll(session, store.getExternalData().getWorld());
        }
        tryStartCountdown(session);
    }

    private void tryStartCountdown(@Nonnull GameSession session) {
        WraithBustersPluginConfig config = plugin.getPluginConfig();
        if (session.getPhase() != GamePhase.LOBBY) {
            return;
        }
        if (session.getPlayers().size() < config.getMinPlayers()) {
            return;
        }
        if (!session.allReady()) {
            return;
        }
        session.setPhase(GamePhase.COUNTDOWN);
        session.setCountdownTicksRemaining(config.getCountdownSeconds() * 20);
        session.setLastCountdownSecondAnnounced(-1);
    }

    public void forceStartCountdown(@Nonnull GameSession session) {
        if (session.getPhase() != GamePhase.LOBBY) {
            return;
        }
        WraithBustersPluginConfig config = plugin.getPluginConfig();
        session.setPhase(GamePhase.COUNTDOWN);
        session.setCountdownTicksRemaining(config.getCountdownSeconds() * 20);
        session.setLastCountdownSecondAnnounced(-1);
    }

    public void tickSession(@Nonnull GameSession session, @Nonnull World world) {
        if (session.getWorldUuid() == null || !session.getWorldUuid().equals(world.getWorldConfig().getUuid())) {
            return;
        }
        switch (session.getPhase()) {
            case COUNTDOWN -> tickCountdown(session, world);
            case ACTIVE -> tickActive(session, world);
            case ENDING -> tickPostRound(session, world);
            case LOBBY -> tickLobby(session, world);
            default -> {}
        }
    }

    private void tickLobby(@Nonnull GameSession session, @Nonnull World world) {
        shutdownSessionIfEmpty(session);
    }

    private void tickPostRound(@Nonnull GameSession session, @Nonnull World world) {
        purgePlayersOutsideInstance(session, world);
        shutdownSessionIfEmpty(session);
    }

    private void purgePlayersOutsideInstance(@Nonnull GameSession session, @Nonnull World instanceWorld) {
        for (UUID playerUuid : new ArrayList<>(session.playerUuidList())) {
            if (!isPlayerInSessionWorld(session, playerUuid)) {
                departPlayer(session, playerUuid, null, null, false, true);
            }
        }
    }

    private boolean isPlayerInSessionWorld(@Nonnull GameSession session, @Nonnull UUID playerUuid) {
        if (session.getWorldUuid() == null) {
            return false;
        }
        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
        if (playerRef == null) {
            return false;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return false;
        }
        World playerWorld = ref.getStore().getExternalData().getWorld();
        return session.getWorldUuid().equals(playerWorld.getWorldConfig().getUuid());
    }

    private void tickCountdown(@Nonnull GameSession session, @Nonnull World world) {
        int remaining = session.getCountdownTicksRemaining();
        if (remaining <= 0) {
            beginRound(session, world);
            return;
        }
        int seconds = (remaining + 19) / 20;
        if (seconds != session.getLastCountdownSecondAnnounced()) {
            session.setLastCountdownSecondAnnounced(seconds);
            broadcastCountdown(world, session, seconds);
        }
        session.setCountdownTicksRemaining(remaining - 1);
        shutdownSessionIfEmpty(session);
    }

    private void beginRound(@Nonnull GameSession session, @Nonnull World world) {
        WraithBustersPluginConfig config = plugin.getPluginConfig();
        Map<UUID, Team> teams = TeamAssigner.assign(session.playerUuidList(), config);
        int humanCount = 0;
        for (Team team : teams.values()) {
            if (team == Team.HUMAN) {
                humanCount++;
            }
        }
        RoomProgressionService.initializeRound(session, humanCount, config);
        GhostTestService.onRoundStarting(session);
        RoomDoorService.applyRoundStart(session, world);
        PuzzleService.resetCandlesForRound(session, world);
        session.setPhase(GamePhase.ACTIVE);
        session.setRoundEndEpochMs(System.currentTimeMillis() + config.getRoundDurationSeconds() * 1000L);

        Transform[] humanSpawns = session.getArenaLayout().getHumanSpawns().toArray(Transform[]::new);
        Transform[] ghostSpawns = session.getArenaLayout().getGhostSpawns().toArray(Transform[]::new);
        int humanIndex = 0;
        int ghostIndex = 0;

        Store<EntityStore> store = world.getEntityStore().getStore();
        for (Map.Entry<UUID, Team> entry : teams.entrySet()) {
            UUID playerUuid = entry.getKey();
            Team team = entry.getValue();
            PlayerSessionState state = session.getOrCreatePlayer(playerUuid);
            state.setTeam(team);
            Ref<EntityStore> ref = findPlayerRef(world, playerUuid);
            if (ref == null) {
                continue;
            }
            if (team == Team.GHOST) {
                state.setRole(PlayerRole.GHOST);
                state.setGhostMana(config.getGhostMaxMana());
                TeamSetupService.applyGhost(ref, store, config, session, world);
                PlayerTeleportUtil.teleport(
                    ref, store, world, session.getArenaLayout().getLobbySpawn(), ghostIndex++, ghostSpawns
                );
            } else {
                state.setRole(PlayerRole.HUMAN);
                state.setAlive(true);
                TeamSetupService.applyHuman(ref, store, session, playerUuid);
                PlayerTeleportUtil.teleport(
                    ref, store, world, session.getArenaLayout().getLobbySpawn(), humanIndex++, humanSpawns
                );
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null && pr != null) {
                LobbyStatusHudSupport.removeHud(player, pr);
                if (team == Team.GHOST) {
                    GhostManaHudSupport.refresh(player, pr, state, config);
                }
                RoundTimerHudSupport.refresh(player, pr, session, config);
            }
        }
        TeamSetupService.refreshVisibility(session, world);
        broadcastTitle(world, session, Message.translation("server.wraithbusters.round.start"));
        ManaPickupService.startRound(session, world);
        PhasePortalMarkerService.startRound(session, world);
        KeySpawnService.startRound(session, world);
        SlothPortraitService.scanWorld(world);
    }

    private void tickActive(@Nonnull GameSession session, @Nonnull World world) {
        WraithBustersPluginConfig config = plugin.getPluginConfig();
        if (System.currentTimeMillis() >= session.getRoundEndEpochMs()) {
            endRound(session, world, Team.GHOST, "server.wraithbusters.win.ghosts.timer");
            return;
        }
        if (session.livingHumanCount() <= 0) {
            endRound(session, world, Team.GHOST, "server.wraithbusters.win.ghosts.eliminated");
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (UUID playerUuid : session.playerUuidList()) {
            Ref<EntityStore> ref = findPlayerRef(world, playerUuid);
            if (ref == null) {
                continue;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null && pr != null) {
                RoundTimerHudSupport.refresh(player, pr, session, config);
                PlayerSessionState state = session.getOrCreatePlayer(playerUuid);
                if (state.getRole() == PlayerRole.GHOST) {
                    GhostManaHudSupport.refresh(player, pr, state, config);
                }
            }
        }
    }

    public void endRound(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull Team winners,
        @Nonnull String messageKey
    ) {
        if (session.getPhase() == GamePhase.ENDING) {
            return;
        }
        ManaPickupService.endRound(session, world);
        PhasePortalMarkerService.endRound(session, world);
        KeySpawnService.endRound(session, world);
        session.setPhase(GamePhase.ENDING);
        session.setWinningTeam(winners);
        for (UUID playerUuid : session.playerUuidList()) {
            TeamSetupService.revealPlayerGlobally(playerUuid);
        }
        broadcastTitle(world, session, Message.translation(messageKey));
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (UUID playerUuid : session.playerUuidList()) {
            Ref<EntityStore> ref = findPlayerRef(world, playerUuid);
            if (ref == null) {
                continue;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null && pr != null) {
                RoundTimerHudSupport.removeHud(player, pr);
                GhostManaHudSupport.removeHud(player, pr);
                player.getPageManager().openCustomPage(ref, store, new RoundEndPage(pr, session));
            }
        }
    }

    public void humansWin(@Nonnull GameSession session, @Nonnull World world) {
        endRound(session, world, Team.HUMAN, "server.wraithbusters.win.humans");
    }

    public void playAgain(@Nonnull GameSession session, @Nonnull World world) {
        session.resetForLobby();
        ManaPickupService.clearForLobby(session, world);
        PhasePortalMarkerService.clearForLobby(session, world);
        KeySpawnService.clearForLobby(session, world);
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (UUID playerUuid : session.playerUuidList()) {
            Ref<EntityStore> ref = findPlayerRef(world, playerUuid);
            if (ref == null) {
                continue;
            }
            TeamSetupService.clearModes(ref, store, session);
            teleportToLobby(session, ref, store);
        }
    }

    public void leavePlayer(
        @Nonnull GameSession session,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        UUIDComponent uuid = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uuid == null) {
            return;
        }
        departPlayer(session, uuid.getUuid(), playerRef, store, true, true);
    }

    public void departPlayer(
        @Nonnull GameSession session,
        @Nonnull UUID playerUuid,
        @Nullable Ref<EntityStore> playerRef,
        @Nullable Store<EntityStore> store,
        boolean exitInstance,
        boolean shutdownIfEmpty
    ) {
        if (!session.getPlayers().containsKey(playerUuid)) {
            return;
        }
        TeamSetupService.revealPlayerGlobally(playerUuid);
        GameRegistry.get().unlinkPlayer(playerUuid);
        session.getPlayers().remove(playerUuid);

        if (playerRef != null && store != null && playerRef.isValid()) {
            TeamSetupService.clearModes(playerRef, store, session);
            World world = store.getExternalData().getWorld();
            if (!session.getPlayers().isEmpty() && session.getPhase() == GamePhase.ACTIVE) {
                TeamSetupService.refreshVisibility(session, world);
            }
            Player player = store.getComponent(playerRef, Player.getComponentType());
            PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
            if (player != null && pr != null) {
                LobbyStatusHudSupport.removeHud(player, pr);
                RoundTimerHudSupport.removeHud(player, pr);
                GhostManaHudSupport.removeHud(player, pr);
                if (session.getPhase() == GamePhase.ENDING) {
                    player.getPageManager().setPage(playerRef, store, Page.None);
                }
            }
            if (exitInstance && GameInstanceService.isInManagedInstance(playerRef, store)) {
                GameInstanceService.exitToOrigin(playerRef, store);
            }
            if (session.getPhase() == GamePhase.LOBBY) {
                LobbyStatusHudSupport.refreshAll(session, world);
            }
        }

        if (shutdownIfEmpty) {
            shutdownSessionIfEmpty(session);
        }
    }

    public void shutdownSessionIfEmpty(@Nonnull GameSession session) {
        if (session.isAwaitingFirstJoin() || !session.getPlayers().isEmpty()) {
            return;
        }
        shutdownSession(session);
    }

    public void shutdownSession(@Nonnull GameSession session) {
        if (GameRegistry.get().getSession(session.getSessionId()) == null) {
            return;
        }
        World instance = Universe.get().getWorld(session.getWorldUuid());
        if (instance != null) {
            WorldThreadTasks.runOnWorldThread(instance, () -> {
                ManaPickupService.endRound(session, instance);
                PhasePortalMarkerService.shutdownSession(session, instance);
                KeySpawnService.endRound(session, instance);
                SlothPortraitService.shutdownWorld(instance);
            });
            WorldThreadTasks.drainQueue(instance);
        }
        PuzzleService.resetForSession(session);
        GhostTestService.onRoundStarting(session);
        GameRegistry.get().unregister(session);
        GameInstanceService.safeRemoveWorld(instance);
    }

    public void forceStop(@Nonnull GameSession session) {
        World world = Universe.get().getWorld(session.getWorldUuid());
        if (world == null) {
            GameRegistry.get().unregister(session);
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (UUID playerUuid : new ArrayList<>(session.playerUuidList())) {
            Ref<EntityStore> ref = findPlayerRef(world, playerUuid);
            departPlayer(session, playerUuid, ref, store, true, false);
        }
        shutdownSession(session);
    }

    @Nullable
    public Ref<EntityStore> findPlayerRef(@Nonnull World world, @Nonnull UUID playerUuid) {
        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
        if (playerRef == null) {
            return null;
        }
        return playerRef.getReference();
    }

    private void broadcastCountdown(@Nonnull World world, @Nonnull GameSession session, int seconds) {
        Message message = Message.translation("server.wraithbusters.countdown.title").param("seconds", seconds);
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (UUID playerUuid : session.playerUuidList()) {
            Ref<EntityStore> ref = findPlayerRef(world, playerUuid);
            PlayerRef pr = ref == null ? null : store.getComponent(ref, PlayerRef.getComponentType());
            if (pr != null) {
                pr.sendMessage(message);
            }
        }
    }

    private void broadcastTitle(@Nonnull World world, @Nonnull GameSession session, @Nonnull Message message) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (UUID playerUuid : session.playerUuidList()) {
            Ref<EntityStore> ref = findPlayerRef(world, playerUuid);
            PlayerRef pr = ref == null ? null : store.getComponent(ref, PlayerRef.getComponentType());
            if (pr != null) {
                EventTitleUtil.showEventTitleToPlayer(pr, message, Message.empty(), true);
            }
        }
    }
}
