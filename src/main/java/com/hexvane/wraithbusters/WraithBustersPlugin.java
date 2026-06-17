package com.hexvane.wraithbusters;

import com.hexvane.wraithbusters.command.WbCommand;
import com.hexvane.wraithbusters.command.WraithBustersCommand;
import com.hexvane.wraithbusters.arena.ArenaLayoutStore;
import com.hexvane.wraithbusters.config.PluginConfigMerge;
import com.hexvane.wraithbusters.config.WraithBustersPluginConfig;
import com.hexvane.wraithbusters.game.GameService;
import com.hexvane.wraithbusters.game.GameTickSystem;
import com.hexvane.wraithbusters.generated.HstatsBuildMetadata;
import com.hexvane.wraithbusters.ghost.GhostPhaseDoorSystem;
import com.hexvane.wraithbusters.ghost.PhasePortalVisibilitySystem;
import com.hexvane.wraithbusters.pickup.ManaOrbVisibilitySystem;
import com.hexvane.wraithbusters.interaction.CandlePuzzleInteraction;
import com.hexvane.wraithbusters.interaction.ExorcismInteraction;
import com.hexvane.wraithbusters.interaction.LockedDoorInteraction;
import com.hexvane.wraithbusters.interaction.PossessInteraction;
import com.hexvane.wraithbusters.interaction.ReadyUpInteraction;
import com.hexvane.wraithbusters.interaction.SetupPhaseDoorToolInteraction;
import com.hexvane.wraithbusters.player.HumanDeathHandlerSystem;
import com.hexvane.wraithbusters.game.PlayerSessionListener;
import com.hexvane.wraithbusters.instance.WorldRemovalListener;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.AssetPackRegisterEvent;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.common.events.SendCommonAssetsEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nonnull;

public final class WraithBustersPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static WraithBustersPlugin instance;

    private final Config<WraithBustersPluginConfig> config = this.withConfig("config", WraithBustersPluginConfig.CODEC);
    private GameService gameService;

    public WraithBustersPlugin(JavaPluginInit init) {
        super(init);
    }

    public static WraithBustersPlugin get() {
        return instance;
    }

    @Nonnull
    public WraithBustersPluginConfig getPluginConfig() {
        return config.get();
    }

    @Nonnull
    public GameService getGameService() {
        return gameService;
    }

    public void reloadPluginConfig() {
        Path configPath = getDataDirectory().resolve("config.json");
        PluginConfigMerge.appendMissingKeys(configPath, WraithBustersPluginConfig.CODEC);
        config.load().join();
    }

    @Override
    protected void setup() {
        instance = this;
        gameService = new GameService(this);

        Path configPath = getDataDirectory().resolve("config.json");
        PluginConfigMerge.appendMissingKeys(configPath, WraithBustersPluginConfig.CODEC);
        if (!configPath.toFile().exists()) {
            config.save().join();
        }

        ArenaLayoutStore.ensureDefaultArenaFiles(this);

        registerInteractions();
        getCommandRegistry().registerCommand(new WraithBustersCommand());
        getCommandRegistry().registerCommand(new WbCommand());
        getEntityStoreRegistry().registerSystem(new GameTickSystem(this));
        getEntityStoreRegistry().registerSystem(new GhostPhaseDoorSystem());
        getEntityStoreRegistry().registerSystem(new ManaOrbVisibilitySystem());
        getEntityStoreRegistry().registerSystem(new PhasePortalVisibilitySystem());
        getEntityStoreRegistry().registerSystem(new HumanDeathHandlerSystem(this));

        PlayerSessionListener.register(this);
        WorldRemovalListener.register(this);

        String hstatsModUuid = HstatsBuildMetadata.HSTATS_MOD_UUID;
        String modVersion = this.getManifest().getVersion().toString();
        if (!hstatsModUuid.isBlank()) {
            new HStats(hstatsModUuid, modVersion);
            LOGGER.atInfo().log("HStats metrics enabled for WraithBusters v%s.", modVersion);
        }

        registerModCommonAssetDelivery();
    }

    private void registerInteractions() {
        getCodecRegistry(Interaction.CODEC).register("WraithBusters_ReadyUp", ReadyUpInteraction.class, ReadyUpInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register("WraithBusters_LockedDoor", LockedDoorInteraction.class, LockedDoorInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register("WraithBusters_Exorcism", ExorcismInteraction.class, ExorcismInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register("WraithBusters_Possess", PossessInteraction.class, PossessInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register("WraithBusters_CandlePuzzle", CandlePuzzleInteraction.class, CandlePuzzleInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register(
            "WraithBusters_SetupPhaseDoorTool",
            SetupPhaseDoorToolInteraction.class,
            SetupPhaseDoorToolInteraction.CODEC
        );
    }

    @Override
    protected void start() {
        if (this.getManifest().includesAssetPack()) {
            String packId = new PluginIdentifier(this.getManifest()).toString();
            AssetPack pack = AssetModule.get().getAssetPack(packId);
            if (pack != null) {
                HytaleServer.get()
                    .getEventBus()
                    .<Void, AssetPackRegisterEvent>dispatchFor(AssetPackRegisterEvent.class)
                    .dispatch(new AssetPackRegisterEvent(pack));
                CommonAssetModule commonAssets = CommonAssetModule.get();
                if (commonAssets != null) {
                    commonAssets.loadCommonAssets(pack, System.nanoTime());
                    if (Universe.get().getPlayerCount() > 0) {
                        Universe.get().broadcastPacketNoCache(new RequestCommonAssetsRebuild());
                    }
                }
            } else {
                LOGGER.atWarning().log("Asset pack %s not found in AssetModule", packId);
            }
        }
    }

    private void registerModCommonAssetDelivery() {
        if (!this.getManifest().includesAssetPack()) {
            return;
        }
        this.getEventRegistry()
            .registerAsyncGlobal(
                EventPriority.LAST,
                SendCommonAssetsEvent.class,
                future -> future.thenApply(this::pushModCommonAssetsToJoiningClient)
            );
    }

    @Nonnull
    private SendCommonAssetsEvent pushModCommonAssetsToJoiningClient(@Nonnull SendCommonAssetsEvent event) {
        CommonAssetModule module = CommonAssetModule.get();
        if (module == null) {
            return event;
        }
        String packId = new PluginIdentifier(this.getManifest()).toString();
        List<CommonAsset> packAssets = CommonAssetRegistry.getCommonAssetsStartingWith(packId, "");
        if (packAssets.isEmpty()) {
            LOGGER
                .atWarning()
                .log(
                    "No common assets registered for pack %s. Run processResources and restart the dev server.",
                    packId
                );
            return event;
        }
        module.sendAssetsToPlayer(event.getPacketHandler(), packAssets, false);
        return event;
    }
}
