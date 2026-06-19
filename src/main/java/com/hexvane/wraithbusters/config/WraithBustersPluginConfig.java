package com.hexvane.wraithbusters.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;

public final class WraithBustersPluginConfig {
    public static final BuilderCodec<WraithBustersPluginConfig> CODEC =
        BuilderCodec.builder(WraithBustersPluginConfig.class, WraithBustersPluginConfig::new)
            .append(new KeyedCodec<>("MinPlayers", Codec.INTEGER), (o, v) -> o.minPlayers = v, o -> o.minPlayers)
            .add()
            .append(new KeyedCodec<>("MinGhosts", Codec.INTEGER), (o, v) -> o.minGhosts = v, o -> o.minGhosts)
            .add()
            .append(
                new KeyedCodec<>("HumansPerExtraGhost", Codec.INTEGER),
                (o, v) -> o.humansPerExtraGhost = v,
                o -> o.humansPerExtraGhost
            )
            .add()
            .append(
                new KeyedCodec<>("RoomsPerHuman", Codec.INTEGER),
                (o, v) -> o.roomsPerHuman = v,
                o -> o.roomsPerHuman
            )
            .add()
            .append(
                new KeyedCodec<>("RoundDurationSeconds", Codec.INTEGER),
                (o, v) -> o.roundDurationSeconds = v,
                o -> o.roundDurationSeconds
            )
            .add()
            .append(new KeyedCodec<>("GhostMaxMana", Codec.INTEGER), (o, v) -> o.ghostMaxMana = v, o -> o.ghostMaxMana)
            .add()
            .append(new KeyedCodec<>("PlateManaCost", Codec.INTEGER), (o, v) -> o.plateManaCost = v, o -> o.plateManaCost)
            .add()
            .append(new KeyedCodec<>("CandleManaCost", Codec.INTEGER), (o, v) -> o.candleManaCost = v, o -> o.candleManaCost)
            .add()
            .append(new KeyedCodec<>("StatueManaCost", Codec.INTEGER), (o, v) -> o.statueManaCost = v, o -> o.statueManaCost)
            .add()
            .append(new KeyedCodec<>("BushManaCost", Codec.INTEGER), (o, v) -> o.bushManaCost = v, o -> o.bushManaCost)
            .add()
            .append(
                new KeyedCodec<>("BushSnapdragonDurationSeconds", Codec.INTEGER),
                (o, v) -> o.bushSnapdragonDurationSeconds = v,
                o -> o.bushSnapdragonDurationSeconds
            )
            .add()
            .append(
                new KeyedCodec<>("BushSnapdragonDamage", Codec.FLOAT),
                (o, v) -> o.bushSnapdragonDamage = v,
                o -> o.bushSnapdragonDamage
            )
            .add()
            .append(
                new KeyedCodec<>("BushSnapdragonPoisonDurationSeconds", Codec.INTEGER),
                (o, v) -> o.bushSnapdragonPoisonDurationSeconds = v,
                o -> o.bushSnapdragonPoisonDurationSeconds
            )
            .add()
            .append(new KeyedCodec<>("HiveManaCost", Codec.INTEGER), (o, v) -> o.hiveManaCost = v, o -> o.hiveManaCost)
            .add()
            .append(new KeyedCodec<>("HiveBeeCount", Codec.INTEGER), (o, v) -> o.hiveBeeCount = v, o -> o.hiveBeeCount)
            .add()
            .append(new KeyedCodec<>("HiveBeeSpeed", Codec.FLOAT), (o, v) -> o.hiveBeeSpeed = v, o -> o.hiveBeeSpeed)
            .add()
            .append(
                new KeyedCodec<>("HiveBeeHitRadius", Codec.FLOAT),
                (o, v) -> o.hiveBeeHitRadius = v,
                o -> o.hiveBeeHitRadius
            )
            .add()
            .append(
                new KeyedCodec<>("HiveBeeMaxDurationSeconds", Codec.INTEGER),
                (o, v) -> o.hiveBeeMaxDurationSeconds = v,
                o -> o.hiveBeeMaxDurationSeconds
            )
            .add()
            .append(new KeyedCodec<>("HiveHitDamage", Codec.FLOAT), (o, v) -> o.hiveHitDamage = v, o -> o.hiveHitDamage)
            .add()
            .append(
                new KeyedCodec<>("HivePoisonDurationSeconds", Codec.INTEGER),
                (o, v) -> o.hivePoisonDurationSeconds = v,
                o -> o.hivePoisonDurationSeconds
            )
            .add()
            .append(new KeyedCodec<>("CocoonManaCost", Codec.INTEGER), (o, v) -> o.cocoonManaCost = v, o -> o.cocoonManaCost)
            .add()
            .append(
                new KeyedCodec<>("CocoonBurstRadius", Codec.FLOAT),
                (o, v) -> o.cocoonBurstRadius = v,
                o -> o.cocoonBurstRadius
            )
            .add()
            .append(new KeyedCodec<>("CocoonDamage", Codec.FLOAT), (o, v) -> o.cocoonDamage = v, o -> o.cocoonDamage)
            .add()
            .append(
                new KeyedCodec<>("CocoonSlowDurationSeconds", Codec.INTEGER),
                (o, v) -> o.cocoonSlowDurationSeconds = v,
                o -> o.cocoonSlowDurationSeconds
            )
            .add()
            .append(
                new KeyedCodec<>("CocoonSlowSpeedMultiplier", Codec.FLOAT),
                (o, v) -> o.cocoonSlowSpeedMultiplier = v,
                o -> o.cocoonSlowSpeedMultiplier
            )
            .add()
            .append(new KeyedCodec<>("SkullManaCost", Codec.INTEGER), (o, v) -> o.skullManaCost = v, o -> o.skullManaCost)
            .add()
            .append(new KeyedCodec<>("WatcherManaCost", Codec.INTEGER), (o, v) -> o.watcherManaCost = v, o -> o.watcherManaCost)
            .add()
            .append(new KeyedCodec<>("WatcherFeatherDamage", Codec.FLOAT), (o, v) -> o.watcherFeatherDamage = v, o -> o.watcherFeatherDamage)
            .add()
            .append(
                new KeyedCodec<>("WatcherFeatherCount", Codec.INTEGER),
                (o, v) -> o.watcherFeatherCount = v,
                o -> o.watcherFeatherCount
            )
            .add()
            .append(
                new KeyedCodec<>("WatcherFeatherShotDelayTicks", Codec.INTEGER),
                (o, v) -> o.watcherFeatherShotDelayTicks = v,
                o -> o.watcherFeatherShotDelayTicks
            )
            .add()
            .append(
                new KeyedCodec<>("WatcherBurstCooldownTicks", Codec.INTEGER),
                (o, v) -> o.watcherBurstCooldownTicks = v,
                o -> o.watcherBurstCooldownTicks
            )
            .add()
            .append(new KeyedCodec<>("SkullDamage", Codec.FLOAT), (o, v) -> o.skullDamage = v, o -> o.skullDamage)
            .add()
            .append(new KeyedCodec<>("SkullSpeed", Codec.FLOAT), (o, v) -> o.skullSpeed = v, o -> o.skullSpeed)
            .add()
            .append(
                new KeyedCodec<>("SkullHitRadius", Codec.FLOAT),
                (o, v) -> o.skullHitRadius = v,
                o -> o.skullHitRadius
            )
            .add()
            .append(
                new KeyedCodec<>("SkullMaxDurationSeconds", Codec.INTEGER),
                (o, v) -> o.skullMaxDurationSeconds = v,
                o -> o.skullMaxDurationSeconds
            )
            .add()
            .append(new KeyedCodec<>("BarrelManaCost", Codec.INTEGER), (o, v) -> o.barrelManaCost = v, o -> o.barrelManaCost)
            .add()
            .append(
                new KeyedCodec<>("BarrelFoodTornadoDurationSeconds", Codec.INTEGER),
                (o, v) -> o.barrelFoodTornadoDurationSeconds = v,
                o -> o.barrelFoodTornadoDurationSeconds
            )
            .add()
            .append(
                new KeyedCodec<>("BarrelFoodTornadoSpeed", Codec.FLOAT),
                (o, v) -> o.barrelFoodTornadoSpeed = v,
                o -> o.barrelFoodTornadoSpeed
            )
            .add()
            .append(new KeyedCodec<>("BarrelCornDamage", Codec.FLOAT), (o, v) -> o.barrelCornDamage = v, o -> o.barrelCornDamage)
            .add()
            .append(
                new KeyedCodec<>("BarrelCornShotIntervalTicks", Codec.INTEGER),
                (o, v) -> o.barrelCornShotIntervalTicks = v,
                o -> o.barrelCornShotIntervalTicks
            )
            .add()
            .append(
                new KeyedCodec<>("SkullPossessableIconHeight", Codec.DOUBLE),
                (o, v) -> o.skullPossessableIconHeight = v,
                o -> o.skullPossessableIconHeight
            )
            .add()
            .append(new KeyedCodec<>("StatueDamage", Codec.FLOAT), (o, v) -> o.statueDamage = v, o -> o.statueDamage)
            .add()
            .append(
                new KeyedCodec<>("StatueSwingDamageDelayTicks", Codec.INTEGER),
                (o, v) -> o.statueSwingDamageDelayTicks = v,
                o -> o.statueSwingDamageDelayTicks
            )
            .add()
            .append(
                new KeyedCodec<>("StatueSwingDurationTicks", Codec.INTEGER),
                (o, v) -> o.statueSwingDurationTicks = v,
                o -> o.statueSwingDurationTicks
            )
            .add()
            .append(new KeyedCodec<>("StatueSwingHitRadius", Codec.FLOAT), (o, v) -> o.statueSwingHitRadius = v, o -> o.statueSwingHitRadius)
            .add()
            .append(new KeyedCodec<>("CandleFireRadius", Codec.FLOAT), (o, v) -> o.candleFireRadius = v, o -> o.candleFireRadius)
            .add()
            .append(
                new KeyedCodec<>("ManaPickupAmount", Codec.INTEGER),
                (o, v) -> o.manaPickupAmount = v,
                o -> o.manaPickupAmount
            )
            .add()
            .append(
                new KeyedCodec<>("ManaPickupRespawnSeconds", Codec.INTEGER),
                (o, v) -> o.manaPickupRespawnSeconds = v,
                o -> o.manaPickupRespawnSeconds
            )
            .add()
            .append(new KeyedCodec<>("PlateDamage", Codec.FLOAT), (o, v) -> o.plateDamage = v, o -> o.plateDamage)
            .add()
            .append(
                new KeyedCodec<>("CountdownSeconds", Codec.INTEGER),
                (o, v) -> o.countdownSeconds = v,
                o -> o.countdownSeconds
            )
            .add()
            .append(
                new KeyedCodec<>("DefaultArenaId", Codec.STRING),
                (o, v) -> o.defaultArenaId = v,
                o -> o.defaultArenaId
            )
            .add()
            .append(
                new KeyedCodec<>("SlothPortraitTrackRange", Codec.DOUBLE),
                (o, v) -> o.slothPortraitTrackRange = v,
                o -> o.slothPortraitTrackRange
            )
            .add()
            .append(
                new KeyedCodec<>("SlothPortraitHalfFovWidth", Codec.DOUBLE),
                (o, v) -> o.slothPortraitHalfFovWidth = v,
                o -> o.slothPortraitHalfFovWidth
            )
            .add()
            .append(
                new KeyedCodec<>("SlothPortraitPoseCount", Codec.INTEGER),
                (o, v) -> o.slothPortraitPoseCount = v,
                o -> o.slothPortraitPoseCount
            )
            .add()
            .append(
                new KeyedCodec<>("DebugForceRoomChain", Codec.STRING_ARRAY),
                (o, v) -> o.debugForceRoomChain = v == null ? new String[0] : v,
                o -> o.debugForceRoomChain
            )
            .add()
            .append(
                new KeyedCodec<>("PostRoundSeconds", Codec.INTEGER),
                (o, v) -> o.postRoundSeconds = v,
                o -> o.postRoundSeconds
            )
            .add()
            .append(
                new KeyedCodec<>("PlatePossessableIconHeight", Codec.DOUBLE),
                (o, v) -> o.platePossessableIconHeight = v,
                o -> o.platePossessableIconHeight
            )
            .add()
            .append(
                new KeyedCodec<>("CandlePossessableIconHeight", Codec.DOUBLE),
                (o, v) -> o.candlePossessableIconHeight = v,
                o -> o.candlePossessableIconHeight
            )
            .add()
            .append(
                new KeyedCodec<>("StatuePossessableIconHeight", Codec.DOUBLE),
                (o, v) -> o.statuePossessableIconHeight = v,
                o -> o.statuePossessableIconHeight
            )
            .add()
            .append(
                new KeyedCodec<>("BushPossessableIconHeight", Codec.DOUBLE),
                (o, v) -> o.bushPossessableIconHeight = v,
                o -> o.bushPossessableIconHeight
            )
            .add()
            .append(
                new KeyedCodec<>("HivePossessableIconHeight", Codec.DOUBLE),
                (o, v) -> o.hivePossessableIconHeight = v,
                o -> o.hivePossessableIconHeight
            )
            .add()
            .append(
                new KeyedCodec<>("CocoonPossessableIconHeight", Codec.DOUBLE),
                (o, v) -> o.cocoonPossessableIconHeight = v,
                o -> o.cocoonPossessableIconHeight
            )
            .add()
            .append(
                new KeyedCodec<>("WatcherPossessableIconHeight", Codec.DOUBLE),
                (o, v) -> o.watcherPossessableIconHeight = v,
                o -> o.watcherPossessableIconHeight
            )
            .add()
            .append(
                new KeyedCodec<>("BarrelPossessableIconHeight", Codec.DOUBLE),
                (o, v) -> o.barrelPossessableIconHeight = v,
                o -> o.barrelPossessableIconHeight
            )
            .add()
            .append(
                new KeyedCodec<>("PossessableIconScale", Codec.FLOAT),
                (o, v) -> o.possessableIconScale = v,
                o -> o.possessableIconScale
            )
            .add()
            .append(
                new KeyedCodec<>("PossessableIconBobAmplitude", Codec.DOUBLE),
                (o, v) -> o.possessableIconBobAmplitude = v,
                o -> o.possessableIconBobAmplitude
            )
            .add()
            .append(
                new KeyedCodec<>("PossessableIconBobPeriodSeconds", Codec.DOUBLE),
                (o, v) -> o.possessableIconBobPeriodSeconds = v,
                o -> o.possessableIconBobPeriodSeconds
            )
            .add()
            .build();

    private int minPlayers = 2;
    private int minGhosts = 1;
    private int humansPerExtraGhost = 4;
    private int roomsPerHuman = 1;
    private int roundDurationSeconds = 480;
    private int ghostMaxMana = 100;
    private int plateManaCost = 25;
    private int candleManaCost = 20;
    private int statueManaCost = 15;
    private int bushManaCost = 40;
    private int bushSnapdragonDurationSeconds = 10;
    private float bushSnapdragonDamage = 12f;
    private int bushSnapdragonPoisonDurationSeconds = 12;
    private int hiveManaCost = 35;
    private int hiveBeeCount = 6;
    private float hiveBeeSpeed = 5.0f;
    private float hiveBeeHitRadius = 1.2f;
    private int hiveBeeMaxDurationSeconds = 8;
    private float hiveHitDamage = 2.0f;
    private int hivePoisonDurationSeconds = 8;
    private int cocoonManaCost = 25;
    private float cocoonBurstRadius = 4.5f;
    private float cocoonDamage = 12f;
    private int cocoonSlowDurationSeconds = 4;
    private float cocoonSlowSpeedMultiplier = 0.5f;
    private int skullManaCost = 30;
    private int watcherManaCost = 20;
    private float watcherFeatherDamage = 6f;
    private int watcherFeatherCount = 3;
    private int watcherFeatherShotDelayTicks = 8;
    private int watcherBurstCooldownTicks = 24;
    private float skullDamage = 20f;
    private float skullSpeed = 6.0f;
    private float skullHitRadius = 1.0f;
    private int skullMaxDurationSeconds = 10;
    private int barrelManaCost = 50;
    private int barrelFoodTornadoDurationSeconds = 10;
    private float barrelFoodTornadoSpeed = 5.0f;
    private float barrelCornDamage = 3.0f;
    private int barrelCornShotIntervalTicks = 20;
    private float statueDamage = 30f;
    private int statueSwingDamageDelayTicks = 18;
    private int statueSwingDurationTicks = 40;
    private float statueSwingHitRadius = 3.0f;
    private float candleFireRadius = 4.5f;
    private int manaPickupAmount = 25;
    private int manaPickupRespawnSeconds = 30;
    private float plateDamage = 25f;
    private int countdownSeconds = 5;
    @Nonnull
    private String defaultArenaId = "mansion_v1";
    private double slothPortraitTrackRange = 14.0;
    private double slothPortraitHalfFovWidth = 4.0;
    private int slothPortraitPoseCount = 11;
    /** Set to e.g. ["Kitchen", "Garden"] to force room order; use [] for random finished-room shuffle. */
    @Nonnull
    private String[] debugForceRoomChain = new String[0];
    private int postRoundSeconds = 15;
    private double platePossessableIconHeight = 0.35;
    private double candlePossessableIconHeight = 0.85;
    private double statuePossessableIconHeight = 2.85;
    private double bushPossessableIconHeight = 1.05;
    private double hivePossessableIconHeight = 0.55;
    private double cocoonPossessableIconHeight = 0.75;
    private double watcherPossessableIconHeight = 1.1;
    private double barrelPossessableIconHeight = 1.2;
    private double skullPossessableIconHeight = 0.18;
    private float possessableIconScale = 1.0f;
    private double possessableIconBobAmplitude = 0.06;
    private double possessableIconBobPeriodSeconds = 2.8;

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMinGhosts() {
        return minGhosts;
    }

    public int getHumansPerExtraGhost() {
        return humansPerExtraGhost;
    }

    public int getRoomsPerHuman() {
        return roomsPerHuman;
    }

    public int getRoundDurationSeconds() {
        return roundDurationSeconds;
    }

    public int getGhostMaxMana() {
        return ghostMaxMana;
    }

    public int getPlateManaCost() {
        return plateManaCost;
    }

    public int getCandleManaCost() {
        return candleManaCost;
    }

    public int getStatueManaCost() {
        return statueManaCost;
    }

    public int getBushManaCost() {
        return bushManaCost;
    }

    public int getBushSnapdragonDurationSeconds() {
        return bushSnapdragonDurationSeconds;
    }

    public float getBushSnapdragonDamage() {
        return bushSnapdragonDamage;
    }

    public int getBushSnapdragonPoisonDurationSeconds() {
        return bushSnapdragonPoisonDurationSeconds;
    }

    public int getHiveManaCost() {
        return hiveManaCost;
    }

    public int getHiveBeeCount() {
        return hiveBeeCount;
    }

    public float getHiveBeeSpeed() {
        return hiveBeeSpeed;
    }

    public float getHiveBeeHitRadius() {
        return hiveBeeHitRadius;
    }

    public int getHiveBeeMaxDurationSeconds() {
        return hiveBeeMaxDurationSeconds;
    }

    public float getHiveHitDamage() {
        return hiveHitDamage;
    }

    public int getHivePoisonDurationSeconds() {
        return hivePoisonDurationSeconds;
    }

    public int getCocoonManaCost() {
        return cocoonManaCost;
    }

    public float getCocoonBurstRadius() {
        return cocoonBurstRadius;
    }

    public float getCocoonDamage() {
        return cocoonDamage;
    }

    public int getCocoonSlowDurationSeconds() {
        return cocoonSlowDurationSeconds;
    }

    public float getCocoonSlowSpeedMultiplier() {
        return cocoonSlowSpeedMultiplier;
    }

    public int getSkullManaCost() {
        return skullManaCost;
    }

    public int getWatcherManaCost() {
        return watcherManaCost;
    }

    public float getWatcherFeatherDamage() {
        return watcherFeatherDamage;
    }

    public int getWatcherFeatherCount() {
        return watcherFeatherCount;
    }

    public int getWatcherFeatherShotDelayTicks() {
        return watcherFeatherShotDelayTicks;
    }

    public int getWatcherBurstCooldownTicks() {
        return watcherBurstCooldownTicks;
    }

    public float getSkullDamage() {
        return skullDamage;
    }

    public float getSkullSpeed() {
        return skullSpeed;
    }

    public float getSkullHitRadius() {
        return skullHitRadius;
    }

    public int getSkullMaxDurationSeconds() {
        return skullMaxDurationSeconds;
    }

    public int getBarrelManaCost() {
        return barrelManaCost;
    }

    public int getBarrelFoodTornadoDurationSeconds() {
        return barrelFoodTornadoDurationSeconds;
    }

    public float getBarrelFoodTornadoSpeed() {
        return barrelFoodTornadoSpeed;
    }

    public float getBarrelCornDamage() {
        return barrelCornDamage;
    }

    public int getBarrelCornShotIntervalTicks() {
        return barrelCornShotIntervalTicks;
    }

    public float getStatueDamage() {
        return statueDamage;
    }

    public int getStatueSwingDamageDelayTicks() {
        return statueSwingDamageDelayTicks;
    }

    public int getStatueSwingDurationTicks() {
        return statueSwingDurationTicks;
    }

    public float getStatueSwingHitRadius() {
        return statueSwingHitRadius;
    }

    public float getCandleFireRadius() {
        return candleFireRadius;
    }

    public int getManaPickupAmount() {
        return manaPickupAmount;
    }

    public int getManaPickupRespawnSeconds() {
        return manaPickupRespawnSeconds;
    }

    public float getPlateDamage() {
        return plateDamage;
    }

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    @Nonnull
    public String getDefaultArenaId() {
        return defaultArenaId;
    }

    public double getSlothPortraitTrackRange() {
        return slothPortraitTrackRange;
    }

    public double getSlothPortraitHalfFovWidth() {
        return slothPortraitHalfFovWidth;
    }

    public int getSlothPortraitPoseCount() {
        return slothPortraitPoseCount;
    }

    @Nonnull
    public List<String> getDebugForceRoomChain() {
        if (debugForceRoomChain.length == 0) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(Arrays.asList(debugForceRoomChain));
    }

    public int getPostRoundSeconds() {
        return postRoundSeconds;
    }

    public double getPlatePossessableIconHeight() {
        return platePossessableIconHeight;
    }

    public double getCandlePossessableIconHeight() {
        return candlePossessableIconHeight;
    }

    public double getStatuePossessableIconHeight() {
        return statuePossessableIconHeight;
    }

    public double getBushPossessableIconHeight() {
        return bushPossessableIconHeight;
    }

    public double getHivePossessableIconHeight() {
        return hivePossessableIconHeight;
    }

    public double getCocoonPossessableIconHeight() {
        return cocoonPossessableIconHeight;
    }

    public double getWatcherPossessableIconHeight() {
        return watcherPossessableIconHeight;
    }

    public double getSkullPossessableIconHeight() {
        return skullPossessableIconHeight;
    }

    public float getPossessableIconScale() {
        return possessableIconScale;
    }

    public double getPossessableIconBobAmplitude() {
        return possessableIconBobAmplitude;
    }

    public double getPossessableIconBobPeriodSeconds() {
        return possessableIconBobPeriodSeconds;
    }

    public double getPossessableIconHeight(@Nonnull String typeId) {
        return switch (typeId) {
            case "candle" -> candlePossessableIconHeight;
            case "statue" -> statuePossessableIconHeight;
            case "bush" -> bushPossessableIconHeight;
            case "hive" -> hivePossessableIconHeight;
            case "cocoon" -> cocoonPossessableIconHeight;
            case "watcher" -> watcherPossessableIconHeight;
            case "skull" -> skullPossessableIconHeight;
            case "barrel" -> barrelPossessableIconHeight;
            default -> platePossessableIconHeight;
        };
    }
}
