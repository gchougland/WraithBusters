package com.hexvane.wraithbusters.triggervolume;

import com.hexvane.wraithbusters.WraithBustersConstants;
import com.hexvane.wraithbusters.game.GameRegistry;
import com.hexvane.wraithbusters.game.GameSession;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerContext;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3dUtil;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Arrays;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public final class WraithBustersOfferingEffect extends TriggerEffect {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Vector3d DEFAULT_EJECT_VELOCITY = new Vector3d(0.0, 4.0, 0.0);

    @Nonnull
    public static final BuilderCodec<WraithBustersOfferingEffect> CODEC = BuilderCodec.builder(
            WraithBustersOfferingEffect.class, WraithBustersOfferingEffect::new, BASE_CODEC
        )
        .append(new KeyedCodec<>("RoomId", Codec.STRING), (e, v) -> e.roomId = v, e -> e.roomId)
        .add()
        .append(
            new KeyedCodec<>("Mode", new EnumCodec<>(OfferingMode.class, EnumCodec.EnumStyle.LEGACY), false),
            (e, v) -> e.mode = v,
            e -> e.mode
        )
        .add()
        .append(
            new KeyedCodec<>("RequiredItems", Codec.STRING_ARRAY),
            (e, v) -> e.requiredItems = v != null ? v : new String[0],
            e -> e.requiredItems
        )
        .add()
        .append(
            new KeyedCodec<>("SlotCapacity", Codec.INTEGER, false),
            (e, v) -> e.slotCapacity = v != null ? v : 3,
            e -> e.slotCapacity
        )
        .add()
        .append(
            new KeyedCodec<>("EjectVelocity", Vector3dUtil.CODEC, false),
            (e, v) -> e.ejectVelocity = v,
            e -> e.ejectVelocity
        )
        .add()
        .append(
            new KeyedCodec<>("InsertParticleSystem", Codec.STRING, false),
            (e, v) -> e.insertParticleSystem = v,
            e -> e.insertParticleSystem
        )
        .add()
        .append(
            new KeyedCodec<>("InsertParticleDuration", Codec.FLOAT, false),
            (e, v) -> e.insertParticleDuration = v != null
                ? v
                : WraithBustersConstants.OFFERING_INSERT_PARTICLE_DURATION_SEC,
            e -> e.insertParticleDuration
        )
        .add()
        .append(
            new KeyedCodec<>("InsertSoundEvent", Codec.STRING, false),
            (e, v) -> e.insertSoundEvent = v,
            e -> e.insertSoundEvent
        )
        .add()
        .append(
            new KeyedCodec<>("SoundVolume", Codec.FLOAT, false),
            (e, v) -> e.soundVolume = v != null ? v : WraithBustersConstants.OFFERING_DEFAULT_SOUND_VOLUME,
            e -> e.soundVolume
        )
        .add()
        .append(
            new KeyedCodec<>("RejectParticleSystem", Codec.STRING, false),
            (e, v) -> e.rejectParticleSystem = v,
            e -> e.rejectParticleSystem
        )
        .add()
        .append(
            new KeyedCodec<>("RejectParticleDuration", Codec.FLOAT, false),
            (e, v) -> e.rejectParticleDuration = v != null
                ? v
                : WraithBustersConstants.OFFERING_FEEDBACK_PARTICLE_DURATION_SEC,
            e -> e.rejectParticleDuration
        )
        .add()
        .append(
            new KeyedCodec<>("RejectSoundEvent", Codec.STRING, false),
            (e, v) -> e.rejectSoundEvent = v,
            e -> e.rejectSoundEvent
        )
        .add()
        .append(
            new KeyedCodec<>("SuccessParticleSystem", Codec.STRING, false),
            (e, v) -> e.successParticleSystem = v,
            e -> e.successParticleSystem
        )
        .add()
        .append(
            new KeyedCodec<>("SuccessParticleDuration", Codec.FLOAT, false),
            (e, v) -> e.successParticleDuration = v != null
                ? v
                : WraithBustersConstants.OFFERING_FEEDBACK_PARTICLE_DURATION_SEC,
            e -> e.successParticleDuration
        )
        .add()
        .append(
            new KeyedCodec<>("SuccessSoundEvent", Codec.STRING, false),
            (e, v) -> e.successSoundEvent = v,
            e -> e.successSoundEvent
        )
        .add()
        .build();

    @Nonnull
    private String roomId = "";
    @Nonnull
    private OfferingMode mode = OfferingMode.UNORDERED;
    @Nonnull
    private String[] requiredItems = new String[0];
    private int slotCapacity = 3;
    @Nonnull
    private Vector3d ejectVelocity = new Vector3d(DEFAULT_EJECT_VELOCITY);
    @Nonnull
    private String insertParticleSystem = "";
    private float insertParticleDuration = WraithBustersConstants.OFFERING_INSERT_PARTICLE_DURATION_SEC;
    @Nonnull
    private String insertSoundEvent = "";
    private float soundVolume = WraithBustersConstants.OFFERING_DEFAULT_SOUND_VOLUME;
    @Nonnull
    private String rejectParticleSystem = "";
    private float rejectParticleDuration = WraithBustersConstants.OFFERING_FEEDBACK_PARTICLE_DURATION_SEC;
    @Nonnull
    private String rejectSoundEvent = "";
    @Nonnull
    private String successParticleSystem = "";
    private float successParticleDuration = WraithBustersConstants.OFFERING_FEEDBACK_PARTICLE_DURATION_SEC;
    @Nonnull
    private String successSoundEvent = "";

    public boolean isProperlyConfigured() {
        return !roomId.isBlank() && requiredItems.length > 0;
    }

    @Override
    public void execute(@Nonnull TriggerContext context) {
        Ref<EntityStore> entityRef = context.getEntityRef();
        Store<EntityStore> store = context.getStore();
        World world = store.getExternalData().getWorld();

        if (roomId.isBlank() || requiredItems.length == 0) {
            OfferingPlayerNotify.worldPlayers(world, "server.wraithbusters.puzzle.offering.misconfigured");
            return;
        }
        if (!entityRef.isValid() || store.getComponent(entityRef, ItemComponent.getComponentType()) == null) {
            LOGGER.atInfo().log(
                "Offering effect ignored on volume %s: entity invalid or not an item drop",
                context.getVolume().getId()
            );
            return;
        }

        GameSession session = GameRegistry.get().resolveSessionForWorld(world);
        if (session == null) {
            OfferingPlayerNotify.worldPlayers(world, "server.wraithbusters.puzzle.offering.noSession");
            LOGGER.atInfo().log(
                "Offering effect on volume %s ignored: no WraithBusters session for world %s",
                context.getVolume().getId(),
                world.getWorldConfig().getUuid()
            );
            return;
        }

        int capacity = slotCapacity > 0 ? slotCapacity : requiredItems.length;
        Vector3d volumePos = context.getVolume().getPosition();
        Vector3d ejectPosition = new Vector3d(volumePos.x, volumePos.y + 0.5, volumePos.z);
        Vector3d velocity = ejectVelocity != null ? new Vector3d(ejectVelocity) : new Vector3d(DEFAULT_EJECT_VELOCITY);
        OfferingFeedbackConfig feedback = new OfferingFeedbackConfig(
            insertParticleSystem,
            insertParticleDuration,
            insertSoundEvent,
            rejectParticleSystem,
            rejectParticleDuration,
            rejectSoundEvent,
            successParticleSystem,
            successParticleDuration,
            successSoundEvent,
            soundVolume
        );
        OfferingPuzzleService.handleInsert(
            session,
            world,
            store,
            entityRef,
            context.getVolume(),
            roomId,
            mode != null ? mode : OfferingMode.UNORDERED,
            Arrays.copyOf(requiredItems, requiredItems.length),
            capacity,
            ejectPosition,
            velocity,
            feedback
        );
    }
}
