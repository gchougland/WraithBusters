package com.hexvane.wraithbusters.game;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.util.WraithBustersSoundUtil;
import com.hexvane.wraithbusters.arena.ArenaLayout;
import com.hexvane.wraithbusters.arena.ArenaLayoutStore;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.debug.GhostTestService;
import com.hexvane.wraithbusters.door.RoomDoorService;
import com.hexvane.wraithbusters.door.RoomProgressionService;
import com.hexvane.wraithbusters.inventory.MinigameInventoryService;
import com.hexvane.wraithbusters.instance.GameInstanceService;
import com.hexvane.wraithbusters.util.WorldThreadTasks;
import com.hypixel.hytale.builtin.instances.config.InstanceEntityConfig;
import com.hexvane.wraithbusters.door.HumanLockedDoorMarkerService;
import com.hexvane.wraithbusters.ghost.PhasePortalMarkerService;
import com.hexvane.wraithbusters.portrait.SlothPortraitService;
import com.hexvane.wraithbusters.possessable.PossessableFlamingSkullService;
import com.hexvane.wraithbusters.possessable.PossessableFoodTornadoService;
import com.hexvane.wraithbusters.possessable.PossessableHiveSwarmService;
import com.hexvane.wraithbusters.possessable.PossessableSnapdragonService;
import com.hexvane.wraithbusters.possessable.SwordStatueSwingService;
import com.hexvane.wraithbusters.possessable.WatcherStatueBurstService;
import com.hexvane.wraithbusters.pickup.ManaPickupService;
import com.hexvane.wraithbusters.possessable.PossessableMarkerIconService;
import com.hexvane.wraithbusters.player.PlayerModelService;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.team.Team;
import com.hexvane.wraithbusters.team.TeamAssigner;
import com.hexvane.wraithbusters.team.TeamSetupService;
import com.hexvane.wraithbusters.puzzle.CheeseChaseService;
import com.hexvane.wraithbusters.puzzle.LibraryBookService;
import com.hexvane.wraithbusters.puzzle.KeySpawnService;
import com.hexvane.wraithbusters.puzzle.PuzzleService;
import com.hexvane.wraithbusters.setup.SetupModeService;
import com.hexvane.wraithbusters.triggervolume.OfferingPuzzleService;
import com.hexvane.wraithbusters.block.ExorcismTableEffectService;
import com.hexvane.wraithbusters.block.ExorcismTableFillerRepairService;
import com.hexvane.wraithbusters.block.StatueFillerRepairService;
import com.hexvane.wraithbusters.triggervolume.OfferingVolumeRepairService;
import com.hexvane.wraithbusters.ui.GhostManaHudSupport;
import com.hexvane.wraithbusters.ui.LobbyStatusHudSupport;
import com.hexvane.wraithbusters.ui.ObjectiveHudSupport;
import com.hexvane.wraithbusters.ui.RoundEndPage;
import com.hexvane.wraithbusters.ui.RoundTimerHudSupport;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hexvane.wraithbusters.util.PlayerHealUtil;
import com.hexvane.wraithbusters.util.WraithBustersVoiceUtil;
import com.hexvane.wraithbusters.util.PlayerTeleportUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
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
    public GameStartHandle beginStartGame(
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
        GameRegistry.get().register(session);
        String worldKey = "wraithbusters-" + session.getSessionId().toString().substring(0, 8);
        CompletableFuture<World> worldFuture = GameInstanceService.createGameWorld(originWorld, hostTransform, worldKey);
        worldFuture.whenComplete((world, error) -> {
            if (world == null) {
                GameRegistry.get().unregister(session);
                return;
            }
            session.setWorldName(world.getName());
            GameRegistry.get().linkWorld(world.getWorldConfig().getUuid(), session);
        });
        return new GameStartHandle(session, worldFuture);
    }

    public boolean joinSession(
        @Nonnull GameSession session,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Transform returnPoint
    ) {
        return joinSession(
            session,
            playerRef,
            store,
            returnPoint,
            GameInstanceService.resolveOverworldReturnWorldUuid(playerRef, store, null)
        );
    }

    public boolean joinSession(
        @Nonnull GameSession session,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Transform returnPoint,
        @Nonnull UUID returnWorldUuid
    ) {
        World instanceWorld = session.getWorldUuid() == null
            ? null
            : Universe.get().getWorld(session.getWorldUuid());
        if (!isInstanceJoinable(instanceWorld)) {
            return false;
        }
        return beginJoinSession(
            session,
            playerRef,
            store,
            returnWorldUuid,
            returnPoint,
            CompletableFuture.completedFuture(instanceWorld)
        );
    }

    public boolean beginHostJoin(
        @Nonnull GameStartHandle handle,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Transform returnPoint
    ) {
        return beginHostJoin(
            handle,
            playerRef,
            store,
            returnPoint,
            GameInstanceService.resolveOverworldReturnWorldUuid(playerRef, store, null)
        );
    }

    public boolean beginHostJoin(
        @Nonnull GameStartHandle handle,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Transform returnPoint,
        @Nonnull UUID returnWorldUuid
    ) {
        return beginJoinSession(
            handle.session(),
            playerRef,
            store,
            returnWorldUuid,
            returnPoint,
            handle.worldFuture()
        );
    }

    private boolean beginJoinSession(
        @Nonnull GameSession session,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID returnWorldUuid,
        @Nonnull Transform returnPoint,
        @Nonnull CompletableFuture<World> worldFuture
    ) {
        UUIDComponent uuid = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uuid == null) {
            return false;
        }
        if (GameRegistry.get().getSession(session.getSessionId()) == null) {
            return false;
        }
        GamePhase phase = session.getPhase();
        if (phase != GamePhase.LOBBY && phase != GamePhase.COUNTDOWN) {
            return false;
        }
        UUID playerUuid = uuid.getUuid();
        UUID resolvedReturnWorldUuid = GameInstanceService.resolveOverworldReturnWorldUuid(
            playerRef,
            store,
            returnWorldUuid
        );
        PlayerSessionState state = session.getOrCreatePlayer(playerUuid);
        state.setSavedReturnWorldUuid(resolvedReturnWorldUuid);
        state.setSavedReturnTransform(returnPoint);
        GameRegistry.get().linkPlayer(playerUuid, session);
        session.markHadPlayers();
        session.setAwaitingFirstJoin(false);
        session.markPendingLobbyArrival(playerUuid);
        World originWorld = store.getExternalData().getWorld();
        DeferredWorldTasks.run(originWorld, () -> {
            MinigameInventoryService.stashOverworldInventory(session, playerRef, store);
            GameInstanceService.teleportToLoadingInstance(playerRef, store, worldFuture, returnPoint);
        });
        return true;
    }

    public void finishLobbyArrival(
        @Nonnull GameSession session,
        @Nonnull World instanceWorld,
        @Nonnull UUID playerUuid
    ) {
        if (!session.isPendingLobbyArrival(playerUuid)) {
            return;
        }
        Ref<EntityStore> liveRef = findPlayerRef(instanceWorld, playerUuid);
        if (liveRef == null) {
            return;
        }
        session.clearPendingLobbyArrival(playerUuid);
        Store<EntityStore> instanceStore = instanceWorld.getEntityStore().getStore();
        if (instanceStore == null || instanceStore.isShutdown()) {
            return;
        }
        PlayerSessionState state = session.getOrCreatePlayer(playerUuid);
        state.setReady(false);
        MinigameInventoryService.ensureEmptyIfStashed(session, liveRef, instanceStore);
        UUID returnWorldUuid = state.getSavedReturnWorldUuid();
        Transform returnTransform = state.getSavedReturnTransform();
        if (returnWorldUuid != null && returnTransform != null) {
            GameInstanceService.repairReturnPoint(liveRef, instanceStore, returnWorldUuid, returnTransform);
        }
        teleportToLobby(session, liveRef, instanceStore);
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
        if (player != null && pr != null) {
            GamePhase phase = session.getPhase();
            PlayerSessionState state = session.getOrCreatePlayer(pr.getUuid());
            if (phase == GamePhase.LOBBY || phase == GamePhase.COUNTDOWN) {
                LobbyStatusHudSupport.refresh(player, pr, session);
                LobbyStatusHudSupport.refreshAll(session, world);
            }
        }
    }

    public void toggleReady(
        @Nonnull GameSession session,
        @Nonnull UUID playerUuid,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        GamePhase phase = session.getPhase();
        PlayerSessionState state = session.getOrCreatePlayer(playerUuid);
        if (phase != GamePhase.LOBBY && phase != GamePhase.COUNTDOWN) {
            return;
        }
        state.setReady(!state.isReady());
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (state.isReady()) {
            if (pr != null) {
                pr.sendMessage(Message.translation("server.wraithbusters.ready.on"));
            }
            WraithBustersSoundUtil.play2d(playerRef, store, WraithBustersConstants.READY_ON_SOUND_EVENT);
        } else {
            if (pr != null) {
                pr.sendMessage(Message.translation("server.wraithbusters.ready.off"));
            }
            WraithBustersSoundUtil.play2d(playerRef, store, WraithBustersConstants.READY_OFF_SOUND_EVENT);
        }
        cancelCountdownIfInvalid(session);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player != null && pr != null) {
            LobbyStatusHudSupport.refreshAll(session, store.getExternalData().getWorld());
        }
        tryStartCountdown(session);
    }

    private void tryStartCountdown(@Nonnull GameSession session) {
        if (session.getPhase() != GamePhase.LOBBY) {
            return;
        }
        WraithBustersPluginConfig config = plugin.getPluginConfig();
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
        LobbyStatusHudSupport.refreshAll(session, world);
        purgePlayersOutsideInstance(session, world);
        shutdownSessionIfEmpty(session);
    }

    private void tickPostRound(@Nonnull GameSession session, @Nonnull World world) {
        WraithBustersPluginConfig config = plugin.getPluginConfig();
        long now = System.currentTimeMillis();
        if (session.getPostRoundEndEpochMs() > 0L && now >= session.getPostRoundEndEpochMs()) {
            autoStartNextRound(session, world, config);
            return;
        }
        refreshRoundEndPages(session, world);
        purgePlayersOutsideInstance(session, world);
        shutdownSessionIfEmpty(session);
    }

    private void purgePlayersOutsideInstance(@Nonnull GameSession session, @Nonnull World instanceWorld) {
        for (UUID playerUuid : new ArrayList<>(session.playerUuidList())) {
            if (session.isPendingLobbyArrival(playerUuid)) {
                continue;
            }
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
            // Mid-teleport the entity can briefly disappear from the universe.
            return session.isPendingLobbyArrival(playerUuid);
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return session.isPendingLobbyArrival(playerUuid);
        }
        World playerWorld = ref.getStore().getExternalData().getWorld();
        return session.getWorldUuid().equals(playerWorld.getWorldConfig().getUuid());
    }

    private void tickCountdown(@Nonnull GameSession session, @Nonnull World world) {
        if (!session.allReady()) {
            cancelCountdownIfInvalid(session);
            LobbyStatusHudSupport.refreshAll(session, world);
            return;
        }
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
        purgePlayersOutsideInstance(session, world);
        shutdownSessionIfEmpty(session);
    }

    private void beginRound(@Nonnull GameSession session, @Nonnull World world) {
        WraithBustersPluginConfig config = plugin.getPluginConfig();
        session.clearDisabledPhaseDoors();
        Map<UUID, Team> teams = TeamAssigner.assign(session.playerUuidList(), config);
        int humanCount = 0;
        for (Team team : teams.values()) {
            if (team == Team.HUMAN) {
                humanCount++;
            }
        }
        RoomProgressionService.initializeRound(session, humanCount, config);
        GhostTestService.onRoundStarting(session);
        PuzzleService.resetCandlesForRound(session, world);
        OfferingVolumeRepairService.repairIfNeeded(world);
        ExorcismTableFillerRepairService.repairAt(world, session.getArenaLayout().getExorcismTable());
        ExorcismTableEffectService.prepareForRound(session, world);
        StatueFillerRepairService.repairForLayout(world, session.getArenaLayout());
        OfferingPuzzleService.resetForSession(session);
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
            PlayerHealUtil.fullyHeal(ref, store);
            MinigameInventoryService.clearRuntimeInventory(ref, store);
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
                ObjectiveHudSupport.refresh(player, pr, session, state);
            }
        }
        TeamSetupService.refreshVisibility(session, world);
        broadcastTitle(world, session, Message.translation("server.wraithbusters.round.start"));
        SetupModeService.exitAll(world);
        PhasePortalMarkerService.prepareForRound(session, world);
        HumanLockedDoorMarkerService.prepareForRound(session, world);
        RoomDoorService.applyRoundStart(session, world);
        ManaPickupService.startRound(session, world);
        PossessableMarkerIconService.startRound(session, world, config);
        KeySpawnService.startRound(session, world);
        CheeseChaseService.startRound(session, world);
        LibraryBookService.startRound(session, world);
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
        if (session.connectedGhostCount() <= 0 && session.livingHumanCount() > 0) {
            endRound(session, world, Team.HUMAN, "server.wraithbusters.win.humans");
            return;
        }
        ExorcismTableEffectService.tick(session, world);
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
                ObjectiveHudSupport.refresh(player, pr, session, state);
                if (state.getRole() == PlayerRole.GHOST) {
                    GhostManaHudSupport.refresh(player, pr, state, config);
                }
            }
        }
        purgePlayersOutsideInstance(session, world);
        shutdownSessionIfEmpty(session);
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
        HumanLockedDoorMarkerService.endRound(session, world);
        KeySpawnService.endRound(session, world);
        CheeseChaseService.endRound(session, world);
        LibraryBookService.endRound(session, world);
        PossessableSnapdragonService.endRound(session, world);
        PossessableHiveSwarmService.endRound(session, world);
        PossessableFlamingSkullService.endRound(session, world);
        PossessableFoodTornadoService.endRound(session, world);
        PossessableMarkerIconService.endRound(session, world);
        ExorcismTableEffectService.playRoundWinSound(session, world, this);
        session.setPhase(GamePhase.ENDING);
        session.setWinningTeam(winners);
        session.setLastPostRoundSecondAnnounced(-1);
        session.setPostRoundEndEpochMs(
            System.currentTimeMillis() + plugin.getPluginConfig().getPostRoundSeconds() * 1000L
        );
        for (UUID playerUuid : session.playerUuidList()) {
            session.getOrCreatePlayer(playerUuid).setRoundEndDismissed(false);
            TeamSetupService.revealPlayerGlobally(playerUuid);
        }
        WraithBustersVoiceUtil.unsilenceSession(session);
        TeamSetupService.refreshVisibility(session, world);
        PlayerModelService.resetSessionModels(session, world);
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (UUID playerUuid : session.playerUuidList()) {
            Ref<EntityStore> ref = findPlayerRef(world, playerUuid);
            if (ref != null) {
                PlayerHealUtil.fullyHeal(ref, store);
            }
        }
        broadcastTitle(world, session, Message.translation(messageKey));
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
                ObjectiveHudSupport.removeHud(player, pr);
                player.getPageManager().openCustomPage(ref, store, new RoundEndPage(pr, session));
            }
        }
    }

    public void humansWin(@Nonnull GameSession session, @Nonnull World world) {
        endRound(session, world, Team.HUMAN, "server.wraithbusters.win.humans");
    }

    public void playAgain(@Nonnull GameSession session, @Nonnull World world, @Nonnull UUID playerUuid) {
        migrateToPlayAgainLobby(session, world, playerUuid, null, true);
    }

    private boolean migrateToPlayAgainLobby(
        @Nonnull GameSession oldSession,
        @Nonnull World world,
        @Nonnull UUID playerUuid,
        @Nullable PlayAgainMigrationTarget sharedTarget,
        boolean markDismissedBeforeJoin
    ) {
        if (oldSession.getPhase() != GamePhase.ENDING) {
            return false;
        }
        if (!oldSession.getPlayers().containsKey(playerUuid)) {
            return true;
        }
        Ref<EntityStore> ref = findPlayerRef(world, playerUuid);
        if (ref == null) {
            return false;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        PlayerSessionState state = oldSession.getPlayers().get(playerUuid);
        boolean trackedPlayer = state != null;
        if (trackedPlayer) {
            if (markDismissedBeforeJoin) {
                state.setRoundEndDismissed(true);
                state.setReady(false);
            }
            TeamSetupService.clearModes(ref, store, oldSession);
        }
        PlayerHealUtil.fullyHeal(ref, store);
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (player != null && pr != null) {
            player.getPageManager().setPage(ref, store, Page.None);
            RoundTimerHudSupport.removeHud(player, pr);
            GhostManaHudSupport.removeHud(player, pr);
            ObjectiveHudSupport.removeHud(player, pr);
        }

        Transform returnPoint = state != null
            ? resolvePlayerReturnPoint(state, ref, store)
            : resolveEntityReturnPoint(ref, store);
        UUID returnWorldUuid = state != null ? state.getSavedReturnWorldUuid() : null;
        if (returnWorldUuid == null) {
            returnWorldUuid = GameInstanceService.resolveOverworldReturnWorldUuid(ref, store, null);
        }
        boolean joined = joinPlayAgainTarget(oldSession, world, playerUuid, ref, store, returnPoint, returnWorldUuid, sharedTarget);
        if (!joined) {
            if (pr != null) {
                pr.sendMessage(Message.translation("server.wraithbusters.playAgain.failed"));
            }
            return false;
        }
        if (trackedPlayer) {
            state.setRoundEndDismissed(true);
            state.setReady(false);
            transferPlayerOutOfSession(oldSession, playerUuid, ref, store);
        }
        if (pr != null) {
            pr.sendMessage(Message.translation("server.wraithbusters.playAgain.success"));
        }
        return true;
    }

    private boolean joinPlayAgainTarget(
        @Nonnull GameSession oldSession,
        @Nonnull World world,
        @Nonnull UUID playerUuid,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Transform returnPoint,
        @Nonnull UUID returnWorldUuid,
        @Nullable PlayAgainMigrationTarget sharedTarget
    ) {
        if (sharedTarget != null) {
            if (sharedTarget.session().getSessionId().equals(oldSession.getSessionId())) {
                return false;
            }
            return beginJoinSession(
                sharedTarget.session(),
                ref,
                store,
                returnWorldUuid,
                returnPoint,
                sharedTarget.worldFuture()
            );
        }
        GameSession targetSession = GameRegistry.get().findJoinableLobby(oldSession.getSessionId());
        if (targetSession != null) {
            World instanceWorld = Universe.get().getWorld(targetSession.getWorldUuid());
            if (isInstanceJoinable(instanceWorld)) {
                return beginJoinSession(
                    targetSession,
                    ref,
                    store,
                    returnWorldUuid,
                    returnPoint,
                    CompletableFuture.completedFuture(instanceWorld)
                );
            }
        }
        World originWorld = Universe.get().getWorld(returnWorldUuid);
        if (originWorld == null) {
            originWorld = world;
        }
        GameStartHandle handle = beginStartGame(playerUuid, originWorld, returnPoint, oldSession.getArenaId());
        return beginHostJoin(handle, ref, store, returnPoint, returnWorldUuid);
    }

    @Nullable
    private PlayAgainMigrationTarget resolvePlayAgainTarget(
        @Nonnull GameSession oldSession,
        @Nonnull World world,
        @Nonnull UUID anchorPlayerUuid
    ) {
        GameSession existing = GameRegistry.get().findJoinableLobby(oldSession.getSessionId());
        if (existing != null) {
            World instanceWorld = Universe.get().getWorld(existing.getWorldUuid());
            if (isInstanceJoinable(instanceWorld)) {
                return new PlayAgainMigrationTarget(existing, CompletableFuture.completedFuture(instanceWorld));
            }
        }
        Ref<EntityStore> ref = findPlayerRef(world, anchorPlayerUuid);
        if (ref == null) {
            return null;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        PlayerSessionState state = oldSession.getPlayers().get(anchorPlayerUuid);
        Transform returnPoint = state != null
            ? resolvePlayerReturnPoint(state, ref, store)
            : resolveEntityReturnPoint(ref, store);
        UUID returnWorldUuid = state != null ? state.getSavedReturnWorldUuid() : null;
        if (returnWorldUuid == null) {
            returnWorldUuid = GameInstanceService.resolveOverworldReturnWorldUuid(ref, store, null);
        }
        World originWorld = Universe.get().getWorld(returnWorldUuid);
        if (originWorld == null) {
            originWorld = world;
        }
        GameStartHandle handle = beginStartGame(anchorPlayerUuid, originWorld, returnPoint, oldSession.getArenaId());
        return new PlayAgainMigrationTarget(handle.session(), handle.worldFuture());
    }

    @Nonnull
    private List<UUID> collectAutoPlayAgainPlayers(@Nonnull GameSession session, @Nonnull World world) {
        List<UUID> toMigrate = new ArrayList<>();
        for (UUID playerUuid : session.playerUuidList()) {
            if (!session.getOrCreatePlayer(playerUuid).isRoundEndDismissed()) {
                toMigrate.add(playerUuid);
            }
        }
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            UUID playerUuid = playerRef.getUuid();
            if (!session.getPlayers().containsKey(playerUuid) && !toMigrate.contains(playerUuid)) {
                toMigrate.add(playerUuid);
            }
        }
        return toMigrate;
    }

    private void transferPlayerOutOfSession(
        @Nonnull GameSession session,
        @Nonnull UUID playerUuid,
        @Nullable Ref<EntityStore> playerRef,
        @Nullable Store<EntityStore> store
    ) {
        if (!session.getPlayers().containsKey(playerUuid)) {
            return;
        }
        session.clearPendingLobbyArrival(playerUuid);
        session.getPlayers().remove(playerUuid);
        if (playerRef != null && store != null && playerRef.isValid()) {
            if (!session.getPlayers().isEmpty() && session.getPhase() == GamePhase.ENDING) {
                World instance = Universe.get().getWorld(session.getWorldUuid());
                if (instance != null) {
                    refreshRoundEndPages(session, instance);
                }
            }
        }
        shutdownSessionIfEmpty(session);
    }

    @Nonnull
    private Transform resolvePlayerReturnPoint(
        @Nonnull PlayerSessionState state,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        Transform saved = state.getSavedReturnTransform();
        if (saved != null) {
            return new Transform(saved);
        }
        return resolveEntityReturnPoint(playerRef, store);
    }

    @Nonnull
    private Transform resolveEntityReturnPoint(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        if (GameInstanceService.isInManagedInstance(playerRef, store)) {
            InstanceEntityConfig instanceConfig = store.getComponent(
                playerRef,
                InstanceEntityConfig.getComponentType()
            );
            if (instanceConfig != null && instanceConfig.getReturnPoint() != null) {
                return new Transform(instanceConfig.getReturnPoint().getReturnPoint());
            }
        }
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform != null) {
            return new Transform(transform.getTransform());
        }
        return new Transform();
    }

    private void autoStartNextRound(
        @Nonnull GameSession session,
        @Nonnull World world,
        @Nonnull WraithBustersPluginConfig config
    ) {
        if (session.getPhase() != GamePhase.ENDING) {
            return;
        }
        List<UUID> toMigrate = collectAutoPlayAgainPlayers(session, world);
        if (toMigrate.isEmpty()) {
            session.setPostRoundEndEpochMs(0L);
            return;
        }
        closeAllRoundEndPages(session, world);
        PlayAgainMigrationTarget target = resolvePlayAgainTarget(session, world, toMigrate.getFirst());
        if (target == null) {
            return;
        }
        int failed = 0;
        for (UUID playerUuid : toMigrate) {
            if (!session.getPlayers().containsKey(playerUuid)) {
                continue;
            }
            if (!migrateToPlayAgainLobby(session, world, playerUuid, target, false)) {
                failed++;
            }
        }
        if (failed == 0) {
            session.setPostRoundEndEpochMs(0L);
        }
    }

    private void closeAllRoundEndPages(@Nonnull GameSession session, @Nonnull World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (UUID playerUuid : session.playerUuidList()) {
            Ref<EntityStore> ref = findPlayerRef(world, playerUuid);
            if (ref == null) {
                continue;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().setPage(ref, store, Page.None);
            }
        }
    }

    private void refreshRoundEndPages(@Nonnull GameSession session, @Nonnull World world) {
        if (session.getPostRoundEndEpochMs() <= 0L) {
            return;
        }
        int seconds = (int) Math.ceil((session.getPostRoundEndEpochMs() - System.currentTimeMillis()) / 1000.0);
        seconds = Math.max(0, seconds);
        if (seconds == session.getLastPostRoundSecondAnnounced()) {
            return;
        }
        session.setLastPostRoundSecondAnnounced(seconds);
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (UUID playerUuid : session.playerUuidList()) {
            if (session.getOrCreatePlayer(playerUuid).isRoundEndDismissed()) {
                continue;
            }
            Ref<EntityStore> ref = findPlayerRef(world, playerUuid);
            if (ref == null) {
                continue;
            }
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null && pr != null) {
                player.getPageManager().openCustomPage(ref, store, new RoundEndPage(pr, session));
            }
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
        PlayerSessionState departingState = session.getPlayers().get(playerUuid);
        UUID returnWorldUuid = departingState != null ? departingState.getSavedReturnWorldUuid() : null;
        Transform returnTransform = departingState != null ? departingState.getSavedReturnTransform() : null;
        if (session.getPhase() == GamePhase.ENDING && departingState != null) {
            departingState.setRoundEndDismissed(true);
        }
        session.clearPendingLobbyArrival(playerUuid);
        TeamSetupService.revealPlayerGlobally(playerUuid);
        GameRegistry.get().unlinkPlayer(playerUuid);
        session.getPlayers().remove(playerUuid);

        if (session.getPhase() == GamePhase.ACTIVE) {
            World instance = Universe.get().getWorld(session.getWorldUuid());
            if (instance != null
                && session.connectedGhostCount() <= 0
                && session.livingHumanCount() > 0) {
                endRound(session, instance, Team.HUMAN, "server.wraithbusters.win.humans");
            }
        }

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
                ObjectiveHudSupport.removeHud(player, pr);
                if (session.getPhase() == GamePhase.ENDING) {
                    player.getPageManager().setPage(playerRef, store, Page.None);
                }
            }
            if (exitInstance && GameInstanceService.isInManagedInstance(playerRef, store)) {
                MinigameInventoryService.restoreOverworldInventory(departingState, playerRef, store);
                if (returnWorldUuid != null && returnTransform != null) {
                    GameInstanceService.repairReturnPoint(playerRef, store, returnWorldUuid, returnTransform);
                }
                GameInstanceService.exitToOriginSafe(playerRef, store, returnWorldUuid, returnTransform);
            }
            if (session.getPhase() == GamePhase.ENDING && !session.getPlayers().isEmpty()) {
                World instance = Universe.get().getWorld(session.getWorldUuid());
                if (instance != null) {
                    refreshRoundEndPages(session, instance);
                }
            }
        }

        if (shutdownIfEmpty) {
            cancelCountdownIfInvalid(session);
            refreshLobbyAfterDepart(session);
            shutdownSessionIfEmpty(session);
        }
    }

    private void cancelCountdownIfInvalid(@Nonnull GameSession session) {
        if (session.getPhase() != GamePhase.COUNTDOWN) {
            return;
        }
        WraithBustersPluginConfig config = plugin.getPluginConfig();
        if (session.getPlayers().size() < config.getMinPlayers() || !session.allReady()) {
            session.setPhase(GamePhase.LOBBY);
            session.setCountdownTicksRemaining(0);
            session.setLastCountdownSecondAnnounced(-1);
        }
    }

    private void refreshLobbyAfterDepart(@Nonnull GameSession session) {
        if (session.getPlayers().isEmpty() || session.getWorldUuid() == null) {
            return;
        }
        GamePhase phase = session.getPhase();
        if (phase != GamePhase.LOBBY && phase != GamePhase.COUNTDOWN) {
            return;
        }
        World instance = Universe.get().getWorld(session.getWorldUuid());
        if (instance != null) {
            LobbyStatusHudSupport.refreshAll(session, instance);
        }
    }

    public void shutdownSessionIfEmpty(@Nonnull GameSession session) {
        if (!session.getPlayers().isEmpty()) {
            return;
        }
        if (session.hasPendingLobbyArrivals()) {
            return;
        }
        // World may finish loading before the host join deferred task runs.
        if (session.isAwaitingFirstJoin() && !session.hadPlayers()) {
            return;
        }
        World instance = Universe.get().getWorld(session.getWorldUuid());
        if (instance != null && hasBlockingPlayersInInstance(instance, session)) {
            return;
        }
        shutdownSession(session);
    }

    /**
     * Wait until the instance world is physically empty before teardown. Players removed from the
     * session map (play-again, return home) can still be inside while their teleport finishes.
     */
    private boolean hasBlockingPlayersInInstance(@Nonnull World instance, @Nonnull GameSession session) {
        return !instance.getPlayerRefs().isEmpty();
    }

    public void shutdownSession(@Nonnull GameSession session) {
        if (GameRegistry.get().getSession(session.getSessionId()) == null) {
            return;
        }
        World instance = Universe.get().getWorld(session.getWorldUuid());
        if (instance != null) {
            evictStragglersFromInstance(instance, session);
            WraithBustersVoiceUtil.unsilenceWorldPlayers(instance);
            WorldThreadTasks.runOnWorldThread(instance, () -> {
                ManaPickupService.endRound(session, instance);
                PhasePortalMarkerService.shutdownSession(session, instance);
                KeySpawnService.endRound(session, instance);
                CheeseChaseService.endRound(session, instance);
                LibraryBookService.endRound(session, instance);
                PossessableSnapdragonService.endRound(session, instance);
                PossessableHiveSwarmService.endRound(session, instance);
                PossessableFlamingSkullService.endRound(session, instance);
                PossessableFoodTornadoService.endRound(session, instance);
                SlothPortraitService.shutdownWorld(instance);
                SwordStatueSwingService.clearWorld(instance.getWorldConfig().getUuid());
                WatcherStatueBurstService.clearWorld(instance.getWorldConfig().getUuid());
            });
            WorldThreadTasks.drainQueue(instance);
        }
        PuzzleService.resetForSession(session);
        OfferingPuzzleService.resetForSession(session);
        CheeseChaseService.resetForSession(session);
        LibraryBookService.resetForSession(session);
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

    private void evictStragglersFromInstance(@Nonnull World instance, @Nonnull GameSession session) {
        for (PlayerRef playerRef : new ArrayList<>(instance.getPlayerRefs())) {
            UUID playerUuid = playerRef.getUuid();
            if (session.getPlayers().containsKey(playerUuid)) {
                continue;
            }
            GameSession linkedSession = GameRegistry.get().getSessionForPlayer(playerUuid);
            if (linkedSession != null && !linkedSession.getSessionId().equals(session.getSessionId())) {
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            if (!GameInstanceService.isInManagedInstance(ref, store)) {
                continue;
            }
            GameInstanceService.exitToOriginSafe(ref, store, null, null);
        }
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
