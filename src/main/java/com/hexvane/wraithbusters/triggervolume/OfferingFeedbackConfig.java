package com.hexvane.wraithbusters.triggervolume;

import com.hexvane.wraithbusters.WraithBustersConstants;
import javax.annotation.Nonnull;

public record OfferingFeedbackConfig(
    @Nonnull String insertParticleSystem,
    float insertParticleDuration,
    @Nonnull String insertSoundEvent,
    @Nonnull String rejectParticleSystem,
    float rejectParticleDuration,
    @Nonnull String rejectSoundEvent,
    @Nonnull String successParticleSystem,
    float successParticleDuration,
    @Nonnull String successSoundEvent,
    float soundVolume
) {
    @Nonnull
    public static OfferingFeedbackConfig defaults() {
        return new OfferingFeedbackConfig(
            "",
            WraithBustersConstants.OFFERING_INSERT_PARTICLE_DURATION_SEC,
            WraithBustersConstants.OFFERING_INSERT_SOUND_EVENT,
            WraithBustersConstants.OFFERING_REJECT_PARTICLE,
            WraithBustersConstants.OFFERING_FEEDBACK_PARTICLE_DURATION_SEC,
            WraithBustersConstants.PUZZLE_FAIL_SOUND_EVENT,
            WraithBustersConstants.OFFERING_SUCCESS_PARTICLE,
            WraithBustersConstants.OFFERING_FEEDBACK_PARTICLE_DURATION_SEC,
            WraithBustersConstants.PUZZLE_SUCCESS_SOUND_EVENT,
            WraithBustersConstants.OFFERING_DEFAULT_SOUND_VOLUME
        );
    }

    @Nonnull
    public String resolveInsertSoundEvent() {
        return insertSoundEvent.isBlank()
            ? WraithBustersConstants.OFFERING_INSERT_SOUND_EVENT
            : insertSoundEvent;
    }

    @Nonnull
    public String resolveRejectParticleSystem() {
        return rejectParticleSystem.isBlank()
            ? WraithBustersConstants.OFFERING_REJECT_PARTICLE
            : rejectParticleSystem;
    }

    @Nonnull
    public String resolveRejectSoundEvent() {
        return rejectSoundEvent.isBlank()
            ? WraithBustersConstants.PUZZLE_FAIL_SOUND_EVENT
            : rejectSoundEvent;
    }

    @Nonnull
    public String resolveSuccessParticleSystem() {
        return successParticleSystem.isBlank()
            ? WraithBustersConstants.OFFERING_SUCCESS_PARTICLE
            : successParticleSystem;
    }

    @Nonnull
    public String resolveSuccessSoundEvent() {
        return successSoundEvent.isBlank()
            ? WraithBustersConstants.PUZZLE_SUCCESS_SOUND_EVENT
            : successSoundEvent;
    }

    public float resolveInsertParticleDuration() {
        return insertParticleDuration > 0f
            ? insertParticleDuration
            : WraithBustersConstants.OFFERING_INSERT_PARTICLE_DURATION_SEC;
    }

    public float resolveRejectParticleDuration() {
        return rejectParticleDuration > 0f
            ? rejectParticleDuration
            : WraithBustersConstants.OFFERING_FEEDBACK_PARTICLE_DURATION_SEC;
    }

    public float resolveSuccessParticleDuration() {
        return successParticleDuration > 0f
            ? successParticleDuration
            : WraithBustersConstants.OFFERING_FEEDBACK_PARTICLE_DURATION_SEC;
    }

    public float resolveSoundVolume() {
        return soundVolume > 0f ? soundVolume : WraithBustersConstants.OFFERING_DEFAULT_SOUND_VOLUME;
    }

    public boolean hasInsertParticle() {
        return !insertParticleSystem.isBlank();
    }

    public boolean hasInsertSound() {
        return !insertSoundEvent.equalsIgnoreCase("None");
    }
}
