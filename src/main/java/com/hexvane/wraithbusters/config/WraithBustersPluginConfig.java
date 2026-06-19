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
    private int hiveManaCost = 35;
    private int hiveBeeCount = 6;
    private float hiveBeeSpeed = 5.0f;
    private float hiveBeeHitRadius = 1.2f;
    private int hiveBeeMaxDurationSeconds = 8;
    private float hiveHitDamage = 2.0f;
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
}
