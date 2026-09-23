package cc.sighs.dndturn.platform.server.effect.vanilla;

import net.minecraft.world.entity.Entity;

/** Bounded cross-owner VanillaEffectHost commands; implementations retain thread and identity validation. */
public interface VanillaEffectHost {
    VanillaEffectRoundController participantEffects();
    void participantEffectsChanged();
    void releaseParticipantEffects(Entity entity);
}
