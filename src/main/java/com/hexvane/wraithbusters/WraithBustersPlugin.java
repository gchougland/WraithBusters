package com.hexvane.wraithbusters;

import com.hexvane.wraithbusters.generated.HstatsBuildMetadata;
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
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.List;
import javax.annotation.Nonnull;

public final class WraithBustersPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static WraithBustersPlugin instance;

    public WraithBustersPlugin(JavaPluginInit init) {
        super(init);
    }

    public static WraithBustersPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        instance = this;

        String hstatsModUuid = HstatsBuildMetadata.HSTATS_MOD_UUID;
        String modVersion = this.getManifest().getVersion().toString();
        if (!hstatsModUuid.isBlank()) {
            new HStats(hstatsModUuid, modVersion);
            LOGGER.atInfo().log("HStats metrics enabled for WraithBusters v%s.", modVersion);
        } else {
            LOGGER.atInfo().log(
                "HStats metrics disabled: set WRAITHBUSTERS_HSTATS_MOD_UUID when building, "
                    + "or Gradle property hstats_mod_uuid, to your hstats.dev mod UUID at build time to enable."
            );
        }

        registerModCommonAssetDelivery();
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
                    "No common assets registered for pack %s — client item icons and models will be missing. "
                        + "Run processResources and restart the dev server.",
                    packId
                );
            return event;
        }
        module.sendAssetsToPlayer(event.getPacketHandler(), packAssets, false);
        return event;
    }
}
