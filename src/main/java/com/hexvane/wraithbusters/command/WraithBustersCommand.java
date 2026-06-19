package com.hexvane.wraithbusters.command;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.WraithBustersMessages;
import com.hexvane.wraithbusters.util.DeferredWorldTasks;
import com.hexvane.wraithbusters.WraithBustersPlugin;
import com.hexvane.wraithbusters.arena.ArenaLayout;
import com.hexvane.wraithbusters.arena.RoomDefinition;
import com.hexvane.wraithbusters.arena.ArenaLayoutStore;
import com.hexvane.wraithbusters.arena.GhostPhaseDoorMarker;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.debug.GhostTestService;
import com.hexvane.wraithbusters.ghost.PhasePortalMarkerService;
import com.hexvane.wraithbusters.player.PlayerRole;
import com.hexvane.wraithbusters.player.PlayerSessionState;
import com.hexvane.wraithbusters.ui.GhostManaHudSupport;
import com.hexvane.wraithbusters.door.RoomProgressionService;
import com.hexvane.wraithbusters.puzzle.PuzzleService;
import com.hexvane.wraithbusters.game.GamePhase;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hexvane.wraithbusters.game.GameStartHandle;
import com.hexvane.wraithbusters.setup.SetupModeService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class WraithBustersCommand extends AbstractCommandCollection {
    public WraithBustersCommand() {
        super("wraithbusters", WraithBustersMessages.commandDescription("commands.root"));
        registerSubCommands(this);
    }

    static void registerSubCommands(@Nonnull AbstractCommandCollection root) {
        root.addSubCommand(new StartCommand());
        root.addSubCommand(new JoinCommand());
        root.addSubCommand(new LeaveCommand());
        root.addSubCommand(new ReadyCommand());
        root.addSubCommand(new ForceStartCommand());
        root.addSubCommand(new StopCommand());
        root.addSubCommand(new StatusCommand());
        root.addSubCommand(new ReloadCommand());
        root.addSubCommand(new TestGhostCommand());
        root.addSubCommand(new RefillManaCommand());
        root.addSubCommand(new CompleteRoomCommand());
        root.addSubCommand(new ResetPhaseDoorsCommand());
        root.addSubCommand(new SetupCommand());
    }

    private static final class StartCommand extends AbstractPlayerCommand {
        StartCommand() {
            super("start", WraithBustersMessages.commandDescription("commands.start"));
            addUsageVariant(new StartWithArenaCommand());
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            startGame(context, store, ref, playerRef, world, null);
        }
    }

    private static final class StartWithArenaCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> arenaArg =
            withRequiredArg("arena", WraithBustersMessages.commandDescription("commands.start.arena"), ArgTypes.STRING);

        StartWithArenaCommand() {
            super(WraithBustersMessages.commandDescription("commands.start"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            startGame(context, store, ref, playerRef, world, arenaArg.get(context));
        }
    }

    private static void startGame(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world,
        @Nullable String arenaId
    ) {
        WraithBustersPlugin plugin = WraithBustersPlugin.get();
        if (plugin == null) {
            return;
        }
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (uuid == null || tc == null) {
            return;
        }
        Transform returnPoint = new Transform(tc.getTransform());
        GameStartHandle handle = plugin.getGameService().beginStartGame(uuid.getUuid(), world, returnPoint, arenaId);
        DeferredWorldTasks.run(world, () -> {
            if (plugin.getGameService().beginHostJoin(handle, ref, store, returnPoint)) {
                playerRef.sendMessage(WraithBustersMessages.translation("start.created"));
            } else {
                playerRef.sendMessage(WraithBustersMessages.translation("start.failed"));
            }
        });
    }

    private static final class JoinCommand extends AbstractPlayerCommand {
        JoinCommand() {
            super("join", WraithBustersMessages.commandDescription("commands.join"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            WraithBustersPlugin plugin = WraithBustersPlugin.get();
            if (plugin == null) {
                return;
            }
            GameSession session = GameRegistry.get().findOpenLobby();
            if (session == null) {
                playerRef.sendMessage(WraithBustersMessages.translation("join.none"));
                return;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                return;
            }
            Transform returnPoint = new Transform(tc.getTransform());
            if (plugin.getGameService().joinSession(session, ref, store, returnPoint)) {
                playerRef.sendMessage(WraithBustersMessages.translation("join.success"));
            } else {
                playerRef.sendMessage(WraithBustersMessages.translation("join.failed"));
            }
        }
    }

    private static final class LeaveCommand extends AbstractPlayerCommand {
        LeaveCommand() {
            super("leave", WraithBustersMessages.commandDescription("commands.leave"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            WraithBustersPlugin plugin = WraithBustersPlugin.get();
            UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
            if (plugin == null || uuid == null) {
                return;
            }
            GameSession session = GameRegistry.get().getSessionForPlayer(uuid.getUuid());
            if (session == null) {
                playerRef.sendMessage(WraithBustersMessages.translation("leave.none"));
                return;
            }
            if (GhostTestService.isActive(uuid.getUuid())) {
                GhostTestService.disable(ref, store, playerRef, session, world);
            }
            plugin.getGameService().leavePlayer(session, ref, store);
            playerRef.sendMessage(WraithBustersMessages.translation("leave.success"));
        }
    }

    private static final class ReadyCommand extends AbstractPlayerCommand {
        ReadyCommand() {
            super("ready", WraithBustersMessages.commandDescription("commands.ready"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            WraithBustersPlugin plugin = WraithBustersPlugin.get();
            UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
            if (plugin == null || uuid == null) {
                return;
            }
            GameSession session = GameRegistry.get().getSessionForPlayer(uuid.getUuid());
            if (session == null) {
                playerRef.sendMessage(WraithBustersMessages.translation("ready.noGame"));
                return;
            }
            plugin.getGameService().toggleReady(session, uuid.getUuid(), ref, store);
        }
    }

    private static final class ForceStartCommand extends AbstractPlayerCommand {
        ForceStartCommand() {
            super("forcestart", WraithBustersMessages.commandDescription("commands.forcestart"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            WraithBustersPlugin plugin = WraithBustersPlugin.get();
            UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
            if (plugin == null || uuid == null) {
                return;
            }
            GameSession session = GameRegistry.get().getSessionForPlayer(uuid.getUuid());
            if (session == null) {
                return;
            }
            plugin.getGameService().forceStartCountdown(session);
            playerRef.sendMessage(WraithBustersMessages.translation("forcestart"));
        }
    }

    private static final class StopCommand extends AbstractPlayerCommand {
        StopCommand() {
            super("stop", WraithBustersMessages.commandDescription("commands.stop"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            WraithBustersPlugin plugin = WraithBustersPlugin.get();
            UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
            if (plugin == null || uuid == null) {
                return;
            }
            GameSession session = GameRegistry.get().getSessionForPlayer(uuid.getUuid());
            if (session == null || !session.getHostUuid().equals(uuid.getUuid())) {
                playerRef.sendMessage(WraithBustersMessages.translation("stop.denied"));
                return;
            }
            plugin.getGameService().forceStop(session);
            playerRef.sendMessage(WraithBustersMessages.translation("stop.done"));
        }
    }

    private static final class StatusCommand extends AbstractPlayerCommand {
        StatusCommand() {
            super("status", WraithBustersMessages.commandDescription("commands.status"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuid == null) {
                playerRef.sendMessage(WraithBustersMessages.translation("status.none"));
                return;
            }
            GameSession session = GameRegistry.get().getSessionForPlayer(uuid.getUuid());
            if (session == null) {
                session = GameRegistry.get().resolveSessionForWorld(world);
            }
            if (session == null) {
                playerRef.sendMessage(WraithBustersMessages.translation("status.none"));
                return;
            }
            StringBuilder status = new StringBuilder()
                .append("Phase ")
                .append(session.getPhase().name())
                .append(". Ready ")
                .append(session.readyCount())
                .append(" of ")
                .append(session.getPlayers().size());
            if (session.getPhase() == GamePhase.ACTIVE) {
                status.append("\nCurrent room: ")
                    .append(RoomProgressionService.currentRoom(session).getRoomId())
                    .append(". Chain: ")
                    .append(String.join(" -> ", session.getActiveRoomChain()));
            }
            playerRef.sendMessage(Message.raw(status.toString()));
        }
    }

    private static final class ReloadCommand extends AbstractPlayerCommand {
        ReloadCommand() {
            super("reload", WraithBustersMessages.commandDescription("commands.reload"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            WraithBustersPlugin plugin = WraithBustersPlugin.get();
            if (plugin != null) {
                plugin.reloadPluginConfig();
                playerRef.sendMessage(WraithBustersMessages.translation("reload.done"));
            }
        }
    }

    private static final class TestGhostCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> modeArg =
            withOptionalArg("mode", WraithBustersMessages.commandDescription("commands.testghost.mode"), ArgTypes.STRING);

        TestGhostCommand() {
            super("testghost", WraithBustersMessages.commandDescription("commands.testghost"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            WraithBustersPlugin plugin = WraithBustersPlugin.get();
            if (plugin == null) {
                return;
            }
            UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuid == null) {
                return;
            }
            boolean enable;
            if (!modeArg.provided(context)) {
                enable = !GhostTestService.isActive(uuid.getUuid());
            } else {
                String mode = modeArg.get(context).trim().toLowerCase();
                enable = switch (mode) {
                    case "on", "true", "1", "enable" -> true;
                    case "off", "false", "0", "disable" -> false;
                    default -> !GhostTestService.isActive(uuid.getUuid());
                };
            }
            GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
            if (enable) {
                GhostTestService.enable(plugin, ref, store, playerRef, session, world);
            } else {
                GhostTestService.disable(ref, store, playerRef, session, world);
            }
        }
    }

    private static final class RefillManaCommand extends AbstractPlayerCommand {
        RefillManaCommand() {
            super("refillmana", WraithBustersMessages.commandDescription("commands.refillmana"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            WraithBustersPlugin plugin = WraithBustersPlugin.get();
            UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
            if (plugin == null || uuid == null) {
                return;
            }
            GameSession session = GameRegistry.get().getSessionForPlayer(uuid.getUuid());
            if (session == null) {
                session = GameRegistry.get().resolveSessionForWorld(world);
            }
            if (session == null) {
                playerRef.sendMessage(WraithBustersMessages.translation("refillmana.noSession"));
                return;
            }
            PlayerSessionState state = session.getPlayers().get(uuid.getUuid());
            if (state == null || state.getRole() != PlayerRole.GHOST) {
                playerRef.sendMessage(WraithBustersMessages.translation("refillmana.notGhost"));
                return;
            }
            WraithBustersPluginConfig config = plugin.getPluginConfig();
            state.setGhostMana(config.getGhostMaxMana());
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                GhostManaHudSupport.refresh(player, playerRef, state, config);
            }
            playerRef.sendMessage(
                WraithBustersMessages.translation("refillmana.done")
                    .param("current", state.getGhostMana())
                    .param("max", config.getGhostMaxMana())
            );
        }
    }

    private static final class CompleteRoomCommand extends AbstractPlayerCommand {
        CompleteRoomCommand() {
            super("completeroom", WraithBustersMessages.commandDescription("commands.completeroom"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            GameSession session = GameRegistry.get().resolveSessionForWorld(world);
            if (session == null || session.getPhase() != GamePhase.ACTIVE) {
                playerRef.sendMessage(WraithBustersMessages.translation("completeroom.notActive"));
                return;
            }
            RoomDefinition room = RoomProgressionService.currentRoom(session);
            PuzzleService.ForceCompleteResult result = PuzzleService.forceCompleteCurrentRoom(session, world);
            switch (result) {
                case NOT_ACTIVE -> playerRef.sendMessage(WraithBustersMessages.translation("completeroom.notActive"));
                case ALREADY_DONE -> playerRef.sendMessage(WraithBustersMessages.translation("completeroom.alreadyDone"));
                case COMPLETED_WITH_KEY -> playerRef.sendMessage(
                    WraithBustersMessages.translation("completeroom.done").param("room", room.getRoomId())
                );
                case COMPLETED_WITHOUT_KEY -> playerRef.sendMessage(
                    WraithBustersMessages.translation("completeroom.noKey").param("room", room.getRoomId())
                );
            }
        }
    }

    private static final class ResetPhaseDoorsCommand extends AbstractPlayerCommand {
        ResetPhaseDoorsCommand() {
            super("resetphasedoors", WraithBustersMessages.commandDescription("commands.resetphasedoors"));
            addUsageVariant(new ResetPhaseDoorsWithArenaCommand());
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            resetPhaseDoors(playerRef, world, null);
        }
    }

    private static final class ResetPhaseDoorsWithArenaCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> arenaArg =
            withRequiredArg(
                "arena",
                WraithBustersMessages.commandDescription("commands.resetphasedoors.arena"),
                ArgTypes.STRING
            );

        ResetPhaseDoorsWithArenaCommand() {
            super(WraithBustersMessages.commandDescription("commands.resetphasedoors"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            resetPhaseDoors(playerRef, world, arenaArg.get(context));
        }
    }

    private static void resetPhaseDoors(
        @Nonnull PlayerRef playerRef,
        @Nonnull World world,
        @Nullable String arenaIdArg
    ) {
        WraithBustersPlugin plugin = WraithBustersPlugin.get();
        if (plugin == null) {
            return;
        }
        GameSession session = GameRegistry.get().resolveSessionForWorld(world);
        String arenaId = arenaIdArg != null && !arenaIdArg.isBlank()
            ? arenaIdArg.trim()
            : session != null
                ? session.getArenaId()
                : WraithBustersConstants.DEFAULT_ARENA_ID;

        ArenaLayout loaded = ArenaLayoutStore.loadOrDefault(plugin, arenaId);
        List<GhostPhaseDoorMarker> doors = GhostPhaseDoorMarker.copyAll(loaded.getGhostPhaseDoors());
        SetupModeService.syncPhaseDoorsForArena(arenaId, doors);

        ArenaLayout spawnLayout;
        if (session != null) {
            session.getArenaLayout().setGhostPhaseDoors(GhostPhaseDoorMarker.copyAll(doors));
            spawnLayout = session.getArenaLayout();
        } else {
            spawnLayout = loaded;
        }

        PhasePortalMarkerService.resetFromArenaLayout(world, spawnLayout, session);
        playerRef.sendMessage(
            WraithBustersMessages.translation("resetphasedoors.done")
                .param("count", doors.size())
                .param("arena", arenaId)
        );
    }

    private static final class SetupCommand extends AbstractCommandCollection {
        SetupCommand() {
            super("setup", WraithBustersMessages.commandDescription("commands.setup"));
            addSubCommand(new SetupEnterCommand());
            addSubCommand(new SetupExitCommand());
            addSubCommand(new SetupHelpCommand());
            addSubCommand(new SetupMarkCommand());
            addSubCommand(new SetupSaveCommand());
        }
    }

    private static final class SetupEnterCommand extends AbstractPlayerCommand {
        SetupEnterCommand() {
            super("enter", WraithBustersMessages.commandDescription("commands.setup.enter"));
            addUsageVariant(new SetupEnterWithArenaCommand());
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            enterSetup(playerRef, ref, store, world, WraithBustersConstants.DEFAULT_ARENA_ID);
        }
    }

    private static final class SetupEnterWithArenaCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> arenaArg =
            withRequiredArg("arena", WraithBustersMessages.commandDescription("commands.setup.arena"), ArgTypes.STRING);

        SetupEnterWithArenaCommand() {
            super(WraithBustersMessages.commandDescription("commands.setup.enter"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            enterSetup(playerRef, ref, store, world, arenaArg.get(context));
        }
    }

    private static void enterSetup(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull String arenaId
    ) {
        WraithBustersPlugin plugin = WraithBustersPlugin.get();
        if (plugin == null) {
            return;
        }
        GameSession session = GameRegistry.get().getSessionForWorld(world.getWorldConfig().getUuid());
        if (session != null && session.getPhase() == GamePhase.ACTIVE) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.roundActive"));
            return;
        }
        ArenaLayout layout = ArenaLayoutStore.loadOrDefault(plugin, arenaId);
        SetupModeService.enter(playerRef.getUuid(), layout);
        PhasePortalMarkerService.refreshSetup(playerRef.getUuid(), world, layout);
        Player.giveItem(new ItemStack(WraithBustersConstants.PHASE_DOOR_TOOL_ITEM), ref, store);
        playerRef.sendMessage(WraithBustersMessages.translation("setup.enter"));
    }

    private static final class SetupExitCommand extends AbstractPlayerCommand {
        SetupExitCommand() {
            super("exit", WraithBustersMessages.commandDescription("commands.setup.exit"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            SetupModeService.exit(playerRef.getUuid(), world);
            playerRef.sendMessage(WraithBustersMessages.translation("setup.exit"));
        }
    }

    private static final class SetupHelpCommand extends AbstractPlayerCommand {
        private static final String[] HELP_KEYS = {
            "setup.help.intro",
            "setup.help.position",
            "setup.help.lobbySpawn",
            "setup.help.humanSpawn",
            "setup.help.ghostSpawn",
            "setup.help.room",
            "setup.help.candle",
            "setup.help.possessable",
            "setup.help.manaPickup",
            "setup.help.exorcism",
            "setup.help.smallMouse",
            "setup.help.largeMouse",
            "setup.help.bookshelf",
            "setup.help.bookSpawn",
            "setup.help.phaseDoor",
            "setup.help.phaseDoorTool",
            "setup.help.save",
        };

        SetupHelpCommand() {
            super("help", WraithBustersMessages.commandDescription("commands.setup.help"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            for (String key : HELP_KEYS) {
                playerRef.sendMessage(WraithBustersMessages.translation(key));
            }
        }
    }

    private static void runSetupMark(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world,
        @Nonnull String markerType,
        @Nullable String extra
    ) {
        WraithBustersPlugin plugin = WraithBustersPlugin.get();
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (plugin == null || tc == null) {
            return;
        }
        String resolvedType = markerType;
        String resolvedExtra = extra;
        if (!isKnownMarkerType(markerType)) {
            if (extra == null || extra.isBlank()) {
                resolvedType = "room";
                resolvedExtra = markerType;
            }
        }
        try {
            SetupModeService.markAtPlayer(plugin, playerRef, tc, world, ref, store, resolvedType, resolvedExtra);
        } catch (IOException e) {
            playerRef.sendMessage(WraithBustersMessages.translation("setup.saveFailed"));
        }
    }

    private static boolean isKnownMarkerType(@Nonnull String markerType) {
        return switch (markerType.toLowerCase()) {
            case "lobbyspawn",
                 "humanspawn",
                 "ghostspawn",
                 "manapickup",
                 "possessable",
                 "room",
                 "candle",
                 "exorcism",
                 "phasedoor",
                 "small_mouse",
                 "large_mouse",
                 "bookshelf",
                 "book_spawn" -> true;
            default -> false;
        };
    }

    private static final class SetupMarkCommand extends AbstractPlayerCommand {
        SetupMarkCommand() {
            super("mark", WraithBustersMessages.commandDescription("commands.setup.mark"));
            addUsageVariant(new SetupMarkWithExtraCommand());
        }

        private final RequiredArg<String> typeArg =
            withRequiredArg("type", WraithBustersMessages.commandDescription("commands.setup.type"), ArgTypes.STRING);
        private final OptionalArg<String> extraArg =
            withOptionalArg("extra", WraithBustersMessages.commandDescription("commands.setup.extra"), ArgTypes.STRING);

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            runSetupMark(
                context,
                store,
                ref,
                playerRef,
                world,
                typeArg.get(context),
                extraArg.provided(context) ? extraArg.get(context) : null
            );
        }
    }

    private static final class SetupMarkWithExtraCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> typeArg =
            withRequiredArg("type", WraithBustersMessages.commandDescription("commands.setup.type"), ArgTypes.STRING);
        private final RequiredArg<String> extraArg =
            withRequiredArg("extra", WraithBustersMessages.commandDescription("commands.setup.extra"), ArgTypes.STRING);

        SetupMarkWithExtraCommand() {
            super(WraithBustersMessages.commandDescription("commands.setup.mark"));
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            runSetupMark(context, store, ref, playerRef, world, typeArg.get(context), extraArg.get(context));
        }
    }

    private static final class SetupSaveCommand extends AbstractPlayerCommand {
        SetupSaveCommand() {
            super("save", WraithBustersMessages.commandDescription("commands.setup.save"));
        }

        private final RequiredArg<String> arenaArg =
            withRequiredArg("arena", WraithBustersMessages.commandDescription("commands.setup.arena"), ArgTypes.STRING);

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            WraithBustersPlugin plugin = WraithBustersPlugin.get();
            if (plugin == null) {
                return;
            }
            try {
                SetupModeService.save(plugin, playerRef.getUuid(), arenaArg.get(context));
                playerRef.sendMessage(WraithBustersMessages.translation("setup.saved"));
            } catch (IOException e) {
                playerRef.sendMessage(WraithBustersMessages.translation("setup.saveFailed"));
            }
        }
    }
}
