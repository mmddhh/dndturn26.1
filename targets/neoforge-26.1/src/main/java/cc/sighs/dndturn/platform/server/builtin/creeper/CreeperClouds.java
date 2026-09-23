package cc.sighs.dndturn.platform.server.builtin.creeper;

import cc.sighs.dndturn.domain.encounter.operation.TriggeredExecutionRecord;
import cc.sighs.dndturn.platform.mixin.server.effect.CreeperCloudAccess;
import cc.sighs.dndturn.platform.observation.VanillaEffectTypes;
import cc.sighs.dndturn.platform.server.control.WorldOutcomePolicy;
import cc.sighs.dndturn.platform.server.effect.TriggeredAbilities;
import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Native cloud lifetime with persisted causal identity; unknown executions remain quarantined. */
public final class CreeperClouds {
    private static final String OPERATION = "dndturn:creeper_operation", ACTOR = "dndturn:creeper_actor", DOMAIN = "dndturn:creeper_domain";
    private CreeperClouds() {}
    static void bind(AreaEffectCloud cloud, TriggeredAbilities.Context context) {
        var tag = cloud.getPersistentData();
        tag.putString(OPERATION, context.emission().operation().toString());
        tag.putString(ACTOR, context.actor().id().toString());
        tag.putString(DOMAIN, context.operation().encounterId().toString());
    }
    static boolean tagged(Entity entity) {
        return entity instanceof AreaEffectCloud && !entity.getPersistentData().getStringOr(OPERATION, "").isEmpty();
    }
    static UUID domain(Entity entity) { return UUID.fromString(entity.getPersistentData().getStringOr(DOMAIN, "")); }
    static boolean confirmed(Entity cloud, EncounterRuntime service) {
        try {
            var body = (AreaEffectCloud)cloud;
            if (!(body instanceof AreaEffectCloud) || !Float.isFinite(body.getRadius()) || body.getRadius() > 2.5F
                    || body.getRadius() < 0 || body.getDuration() < 0 || body.getDuration() > 300 || body.getWaitTime() != 10
                    || body.getDurationOnUse() != 0 || body.getRadiusOnUse() != -.5F
                    || body.getRadiusPerTick() != -2.5F / 300) return false;
            int count = 0;
            for (var effect : ((CreeperCloudAccess)body).dndturn$potionContents().getAllEffects())
                if (++count > 32 || !VanillaEffectTypes.supported(effect)) return false;
            var tag = cloud.getPersistentData();
            UUID actor = UUID.fromString(tag.getStringOr(ACTOR, "")), operation = UUID.fromString(tag.getStringOr(OPERATION, ""));
            var invocation = service.actorStates().invocation(operation);
            return invocation != null && invocation.event().actor().equals(actor)
                    && service.worldOutcomes().canonicalOutcomeEncounter(domain(cloud)).equals(service.worldOutcomes().canonicalOutcomeEncounter(invocation.encounter()))
                    && invocation.status() == TriggeredExecutionRecord.Status.COMPLETED && invocation.observation() != null
                    && invocation.observation().spawns().stream().anyMatch(s -> s.entity().equals(cloud.getUUID()));
        } catch (RuntimeException unavailable) { return false; }
    }
    public static List<LivingEntity> targets(AreaEffectCloud cloud, List<LivingEntity> candidates) {
        if (!(cloud.level() instanceof ServerLevel level)) return candidates;
        var service = ServerRuntime.existingEncounter(level.getServer());
        if (service == null) return tagged(cloud) ? List.of() : candidates;
        if (!tagged(cloud)) return candidates.stream().filter(e -> WorldOutcomePolicy.cloudTarget(
            new WorldOutcomePolicy.CloudTargetFacts(true, false, false, false, service.isEntityInsidePausedRegion(e))).allowed()).toList();
        if (candidates.size() > 256 || !confirmed(cloud, service) || service.controlFacts().cloudSimulationPaused(cloud)) return List.of();
        return candidates.stream().filter(e -> service.controlFacts().cloudTargetAllowed(cloud, e)).toList();
    }
}
