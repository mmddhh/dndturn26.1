package cc.sighs.dndturn.platform.server.ai;

import cc.sighs.dndturn.domain.actor.ActorDefinition;
import cc.sighs.dndturn.domain.ai.AiAbilitySemantics;
import cc.sighs.dndturn.domain.ai.AiAffordance;
import cc.sighs.dndturn.domain.ai.AiDefinition;
import cc.sighs.dndturn.domain.ai.AiPlanner;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.platform.mixin.server.control.MobGoalEvidenceAccess;
import cc.sighs.dndturn.platform.server.action.EquippedRanged;
import java.util.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;

/** First-use definition bake. Cache keys contain cold compatibility identity, never equipment/perception.
 * Native classes remain here, never in the planner or AI runtime state. */
public final class AiDefinitions {
    private record ActorKey(String id, int version) {}
    private record BakeKey(ActorKey actor, Class<?> implementation, Class<?> navigation) {}
    private static final Map<ActorKey, AiDefinition> explicit = new HashMap<>();
    private static final Map<BakeKey, AiDefinition> baked = new HashMap<>();
    private static boolean frozen;
    public record Family(String id, int version, int priority, java.util.function.Predicate<Mob> matches,
                         AiDefinition definition) {
        public Family { FactKey.requireId(id); Objects.requireNonNull(matches); Objects.requireNonNull(definition);
            if (version < 1 || definition.provenance().source() != AiDefinition.Source.AUDITED_NATIVE_FAMILY)
                throw new IllegalArgumentException("audited AI family evidence required"); }
    }
    private static final Map<String, Family> families = new LinkedHashMap<>();
    public static synchronized void registerFamily(Family family) {
        if (frozen || families.size() >= 128 || families.putIfAbsent(family.id(), family) != null)
            throw new IllegalStateException("AI family registration closed or duplicate");
        baked.clear();
    }
    public static final AiAbilitySemantics SEMANTICS = new AiAbilitySemantics();
    static {
        SEMANTICS.register(new AiAbilitySemantics.Key("dndturn:intrinsic_melee", 1),
            Set.of(AiAffordance.DAMAGE, AiAffordance.MELEE, AiAffordance.SINGLE_TARGET, AiAffordance.RESOURCE_CHEAP));
        SEMANTICS.register(new AiAbilitySemantics.Key(EquippedRanged.ID, 1),
            Set.of(AiAffordance.DAMAGE, AiAffordance.RANGED, AiAffordance.SINGLE_TARGET));
    }
    private AiDefinitions() {}
    public record Compatibility(AiDefinition definition, List<String> structuralCandidates, boolean truncated) {
        public Compatibility { structuralCandidates = List.copyOf(structuralCandidates); }
    }
    /** Pure bounded structural diagnostics; never calls Goal canUse/start/tick or grants an ability. */
    public static Compatibility inspect(ActorDefinition actor, Mob mob) {
        var access = (MobGoalEvidenceAccess)mob;
        var candidates = new ArrayList<String>();
        boolean truncated = false;
        for (var selector : List.of(access.dndturn$goals(), access.dndturn$targets())) {
            for (var wrapped : selector.getAvailableGoals()) {
                if (candidates.size() == 32) { truncated = true; break; }
                String type = wrapped.getGoal().getClass().getName();
                candidates.add("STRUCTURAL_CANDIDATE:" + wrapped.getPriority() + ":" + type.substring(0, Math.min(type.length(), 180)));
            }
        }
        return new Compatibility(definition(actor, mob), candidates, truncated);
    }
    public static synchronized void register(String actorId, int actorVersion, AiDefinition definition) {
        FactKey.requireId(actorId);
        if (frozen || actorVersion < 1 || explicit.size() >= 128) throw new IllegalStateException("AI registration closed");
        if (definition.provenance().source() != AiDefinition.Source.EXPLICIT_PROVIDER)
            throw new IllegalArgumentException("explicit provider provenance required");
        if (explicit.putIfAbsent(new ActorKey(actorId, actorVersion), definition) != null)
            throw new IllegalArgumentException("duplicate actor AI binding");
    }
    public static synchronized void freeze() { SEMANTICS.freeze(); AiPlanner.freeze(); frozen = true; }
    public static synchronized AiDefinition definition(ActorDefinition actor, Mob mob) {
        var key = new ActorKey(actor.id(), actor.version());
        var provided = explicit.get(key);
        if (provided != null) return provided;
        var cacheKey = new BakeKey(key, mob.getClass(), mob.getNavigation().getClass());
        var existing = baked.get(cacheKey);
        if (existing != null) return existing;
        if (baked.size() >= 4096) throw new IllegalStateException("AI definition cache bound");
        Family selected = null;
        boolean conflict = false;
        for (var family : families.values()) if (family.matches().test(mob)) {
            if (selected == null || family.priority() > selected.priority()) { selected = family; conflict = false; }
            else if (family.priority() == selected.priority()) conflict = true;
        }
        if (conflict) throw new IllegalStateException("ambiguous audited AI family");
        if (selected != null) { baked.put(cacheKey, selected.definition()); return selected.definition(); }
        boolean movement = MovementPorts.find(mob) != null;
        var source = AiDefinition.Source.GENERIC_FALLBACK;
        var preference = mob instanceof net.minecraft.world.entity.monster.Enemy
                ? AiDefinition.TargetPreference.VISIBLE_PLAYERS : AiDefinition.TargetPreference.COMMITTED_OR_HOSTILE_PLAYERS;
        String id = "dndturn:generic_combat";
        var definition = new AiDefinition(id, 1, "dndturn:shared_ground", Set.of("BOUNDED_PROPOSALS"), preference,
            movement ? AiDefinition.Positioning.MOVEMENT_OPPORTUNITY : AiDefinition.Positioning.STATIONARY,
            AiDefinition.ResourcePreference.CONSERVATIVE, AiDefinition.RiskPreference.AUDITED_OPPORTUNITIES_ONLY, ActorFacts.DECISION,
            new AiDefinition.Provenance(source, "dndturn:native_ai_translation", 1,
                "Minecraft 26.1 / NeoForge 26.1.2.84; default native facts and shared abilities; navigation checked independently"));
        baked.put(cacheKey, definition);
        return definition;
    }
}
