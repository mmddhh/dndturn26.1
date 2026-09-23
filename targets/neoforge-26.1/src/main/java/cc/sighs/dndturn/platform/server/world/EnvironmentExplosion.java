package cc.sighs.dndturn.platform.server.world;

import cc.sighs.dndturn.platform.server.control.WorldOutcomePolicy;
import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** A synchronous bridge for one vanilla TNT explosion, never a general inherited action permit. */
public final class EnvironmentExplosion {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private record Frame(PrimedTnt source, UUID step) {}
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private EnvironmentExplosion() {}

    /** .84 fires Detonate before entity damage/knockback and before block/fire iteration. */
    public static void onDetonate(ExplosionEvent.Detonate event) {
        Frame frame = CURRENT.get();
        if (frame == null || event.getExplosion().getDirectSourceEntity() != frame.source()
            || !(event.getLevel() instanceof ServerLevel level)) return;
        EncounterRuntime service = ServerRuntime.existingEncounter(level.getServer());
        if (service == null) {
            event.getAffectedEntities().clear(); event.getAffectedBlocks().clear(); return;
        }
        int entities = event.getAffectedEntities().size(), blocks = event.getAffectedBlocks().size();
        event.getAffectedEntities().removeIf(entity -> !WorldOutcomePolicy.explosionImpact(
            new WorldOutcomePolicy.EnvironmentImpact(frame.step().equals(service.environmentStepFor(frame.source())),
                service.isEntityInsidePausedRegion(entity), frame.step().equals(service.environmentStepFor(entity)))).allowed());
        event.getAffectedBlocks().removeIf(pos -> !WorldOutcomePolicy.explosionImpact(
            new WorldOutcomePolicy.EnvironmentImpact(frame.step().equals(service.environmentStepFor(frame.source())),
                service.isBlockSimulationPaused(level, pos), false)).allowed());
        int rejectedEntities = entities - event.getAffectedEntities().size();
        int rejectedBlocks = blocks - event.getAffectedBlocks().size();
        if (rejectedEntities != 0 || rejectedBlocks != 0)
            LOGGER.warn("ENVIRONMENT_DOMAIN_MISMATCH step={} source={} rejectedEntities={} rejectedBlocks={}",
                frame.step(), frame.source().getUUID(), rejectedEntities, rejectedBlocks);
    }

    public static void run(PrimedTnt source, Runnable explosion) {
        Frame previous = CURRENT.get();
        try {
            EncounterRuntime service = source.level() instanceof ServerLevel level
                ? ServerRuntime.existingEncounter(level.getServer()) : null;
            UUID step = service == null || !(source instanceof PrimedTnt) ? null : service.environmentStepFor(source);
            if (step == null) CURRENT.remove();
            else CURRENT.set(new Frame(source, step));
            explosion.run();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static boolean allows(LivingEntity target, DamageSource damage) {
        Frame frame = CURRENT.get();
        if (frame == null || damage.getDirectEntity() != frame.source()
            || !damage.is(DamageTypeTags.IS_EXPLOSION) || target.level() != frame.source().level()
            || !(target.level() instanceof ServerLevel level)) return false;
        EncounterRuntime service = ServerRuntime.existingEncounter(level.getServer());
        return service != null && WorldOutcomePolicy.explosionDamage(new WorldOutcomePolicy.EnvironmentImpact(
            frame.step().equals(service.environmentStepFor(frame.source())), service.isEntityInsidePausedRegion(target),
            frame.step().equals(service.environmentStepFor(target)))).allowed();
    }
}
