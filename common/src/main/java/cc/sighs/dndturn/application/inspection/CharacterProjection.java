package cc.sighs.dndturn.application.inspection;

import cc.sighs.dndturn.domain.actor.NativeActorObservation;
import cc.sighs.dndturn.domain.encounter.StateView;
import cc.sighs.dndturn.domain.ability.ProcessState;
import cc.sighs.dndturn.domain.effect.EffectSnapshot;
import java.util.*;

/** Private, paged character projection. No client-selected fact contract or mutable rule state. */
public final class CharacterProjection {
    public enum Section { OVERVIEW, ATTRIBUTES, EQUIPMENT, INVENTORY, EFFECTS, ABILITIES, RESOURCES, ENCOUNTER }
    public static final int PAGE_SIZE = 24;
    public record CombatContext(StateView state, List<UUID> hostile, List<ProcessState> processes,
                                boolean pendingActionDebt, int baseMovementTicks, int environmentBudget) {
        public CombatContext { hostile = List.copyOf(hostile); processes = List.copyOf(processes); }
    }
    private CharacterProjection() {}
    public static ActorViews.InspectionView project(ActorViews.CharacterSheet sheet, UUID observer, CombatContext context, Section section, int page) {
        if (!sheet.actor().actor().equals(observer)) throw new SecurityException("private character data");
        if (page < 0 || page > 1023) throw new IllegalArgumentException("character page");
        var rows = new Rows(); var actor = sheet.actor(); var body = sheet.nativeState();
        var encounter = context.state(); var hostile = context.hostile(); var processes = context.processes();
        var member = encounter == null ? null : encounter.members().get(actor.actor());
        switch (section) {
            case OVERVIEW -> {
                rows.add("name", body.name()); rows.add("type", body.type());
                rows.add("definition", actor.definition().id() + " @" + actor.definition().version());
                rows.add("groups", String.join(", ", new TreeSet<>(actor.definition().groups())));
                rows.add("creature_type", actor.definition().classification().creatureType());
                rows.add("size", actor.definition().classification().size());
                rows.add("tags", String.join(", ", new TreeSet<>(actor.definition().classification().tags())));
                rows.add("actor", actor.actor()); rows.add("instance", actor.instance());
                rows.add("health", body.vitals().health() + " / " + body.vitals().maximum());
                rows.add("absorption", body.vitals().absorption()); rows.add("alive", body.vitals().alive());
                var p = body.transform();
                rows.add("position", p.dimension() + " [" + p.x() + ", " + p.y() + ", " + p.z() + "]");
                rows.add("orientation", p.yaw() + " / " + p.pitch());
                rows.add("body", body.body().width() + " × " + body.body().height() + " | " + body.body().pose());
                rows.add("eye", body.body().eyeHeight()); rows.add("ground", body.body().onGround());
                rows.add("water", body.body().inWater()); rows.add("fire", body.body().onFire());
                rows.add("controller", body.controller());
                if (sheet.behavior() != null) {
                    rows.add("behavior", sheet.behavior().id() + " @" + sheet.behavior().version());
                    rows.add("behavior_source", sheet.behavior().provenance().source() + " | " + sheet.behavior().provenance().provider());
                }
                rows.add("rules", actor.revision().ruleset()); rows.add("revision", actor.revision().actorState());
                rows.add("unsupported", "ABILITY_SCORES / PROFICIENCIES / CONCENTRATION / RESISTANCE_TABLE");
            }
            case ATTRIBUTES -> {
                rows.add("coverage", body.attributeCoverage());
                new TreeMap<>(body.attributes()).forEach((id, value) -> {
                    rows.add("attribute", id + " | " + value.base() + " → " + value.effective());
                    if (!value.complete()) rows.add("limited", id);
                    for (var modifier : value.modifiers()) {
                        rows.add("modifier", modifier.id() + " | " + modifier.operation() + " " + modifier.amount());
                        if (modifier.equipment() != null) rows.add("equipment_match", modifier.equipment().slot() + " | " + modifier.equipment().item());
                        else if (modifier.nativeEffect() != null) rows.add("effect_match", modifier.nativeEffect());
                        else rows.add("unknown_source", modifier.id());
                    }
                });
                new TreeMap<>(sheet.derivations()).forEach((id, value) -> {
                    rows.add("derived", id + " | (" + value.base() + " + " + value.addition() + ") × " + value.multiplier() + " → " + value.value());
                    for (var contribution : value.contributions()) {
                        rows.add("contribution", contribution.source() + " | " + contribution.operator() + " " + contribution.operand() + " | n=" + contribution.scale());
                        if (contribution.effect() != null) rows.add("effect_instance", contribution.effect());
                        if (contribution.grant() != null) rows.add("grant", contribution.grant().kind() + " | " + contribution.grant().provider() + " | " + contribution.grant().grant());
                    }
                });
                actor.facts().keys().stream().filter(key -> !key.id().startsWith("minecraft:") && !sheet.derivations().containsKey(key.id()))
                        .sorted(Comparator.comparing(key -> key.id())).forEach(key -> {
                            try { rows.add("rule_fact", key.id() + " = " + actor.facts().read(new cc.sighs.dndturn.domain.fact.ReadContract(Set.of(key)), key)); }
                            catch (cc.sighs.dndturn.domain.fact.FactSlice.MissingFact unknown) { rows.add("rule_fact", key.id() + " = " + unknown.reason()); }
                        });
            }
            case EQUIPMENT -> { rows.add("selection", body.selectedSlot()); body.equipment().forEach(item -> item(rows, item)); }
            case INVENTORY -> { rows.add("coverage", body.inventoryCoverage()); body.inventory().forEach(item -> item(rows, item)); }
            case EFFECTS -> {
                for (var effect : actor.effects()) {
                    rows.add("effect", effect.effect() + " | rank=" + effect.rank() + " | stacks=" + effect.stacks());
                    if (effect instanceof EffectSnapshot.Tactical tactical) {
                        rows.add("duration", tactical.remaining() + " | " + tactical.clock());
                        rows.add("effect_instance", tactical.instance());
                        rows.add("source", tactical.sourceActor() + " | " + tactical.sourceAbility());
                    } else if (effect instanceof EffectSnapshot.Vanilla nativeEffect) {
                        rows.add("native_duration", effect.remaining());
                        rows.add("native_effect_support", nativeEffect.support());
                        if (nativeEffect.support() != EffectSnapshot.NativeSupport.NATIVE)
                            rows.add("remaining_rounds", nativeEffect.remainingRounds());
                    }
                }
            }
            case ABILITIES -> {
                for (var binding : actor.abilities()) {
                    rows.add("ability", binding.definition().label() + " | " + binding.id() + " @" + binding.version());
                    rows.add("activation", binding.definition().activation() + " | " + binding.definition().cost().resource() + " | " + binding.definition().cost().commitPoint());
                    var source = binding.source();
                    rows.add("source", binding.grant().owner() + " | " + source.kind() + " | " + Objects.toString(source.provider(), ""));
                    if (source.grant() != null) rows.add("grant", source.grant() + " | r=" + source.revision());
                    if (source.item() != null) rows.add("selection", source.hand() + " | slot=" + source.item().slot() + " | " + source.item().revision());
                }
                sheet.persistent().grants().values().stream().sorted(Comparator.comparing(g -> g.id())).forEach(grant -> {
                    rows.add("learned", grant.ability() + " @" + grant.version() + " | " + grant.origin());
                    boolean prepared = !grant.requiresPreparation() || sheet.persistent().prepared().contains(grant.id());
                    rows.add("prepared", prepared);
                    rows.add("availability", !prepared ? "NOT_PREPARED" : actor.abilities().stream().anyMatch(b -> grant.id().equals(b.source().grant()))
                            ? "BOUND" : "DEFINITION_UNAVAILABLE");
                });
            }
            case RESOURCES -> {
                rows.add("health", body.vitals().health() + " / " + body.vitals().maximum()); rows.add("absorption", body.vitals().absorption());
                new TreeMap<>(sheet.persistent().resources()).forEach((id, amount) -> rows.add("actor_resource", id + " = " + amount));
                new TreeMap<>(actor.definition().resources()).forEach((id, descriptor) -> {
                    rows.add("resource_definition", id + " | " + descriptor.unit());
                    if (descriptor.capacityFact() != null) {
                        var key = new cc.sighs.dndturn.domain.fact.FactKey<>(descriptor.capacityFact(), Double.class);
                        try { rows.add("capacity", id + " = " + actor.facts().read(new cc.sighs.dndturn.domain.fact.ReadContract(Set.of(key)), key)); }
                        catch (cc.sighs.dndturn.domain.fact.FactSlice.MissingFact unknown) { rows.add("capacity", id + " = " + unknown.reason()); }
                    }
                    rows.add("refresh_rule", descriptor.refreshRule());
                });
                if (member != null) {
                    rows.add("action", member.action()); rows.add("reaction", member.reaction()); rows.add("movement", member.movementTicks());
                    rows.add("base_movement", context.baseMovementTicks()); rows.add("action_debt", context.pendingActionDebt());
                }
                if (sheet.persistent().resources().keySet().stream().anyMatch(id -> !actor.definition().resources().containsKey(id)))
                    rows.add("capacity_unknown", "UNKNOWN");
            }
            case ENCOUNTER -> {
                if (member == null) rows.add("membership", "NONE");
                else {
                    rows.add("encounter", encounter.id()); rows.add("phase", encounter.phase()); rows.add("round", encounter.round());
                    rows.add("initiative", member.initiative() + " | tie=" + member.tieBreak()); rows.add("eligible", member.eligibleRound());
                    rows.add("current", encounter.current()); rows.add("dodging", member.dodging()); rows.add("disengaged", member.disengaged());
                    rows.add("environment", encounter.environment().id() + " | " + encounter.environmentRemaining());
                    rows.add("environment_budget", context.environmentBudget());
                    rows.add("action_debt", context.pendingActionDebt());
                    if (encounter.region() != null) {
                        rows.add("field", encounter.region().dimension() + " | r=" + encounter.region().radius());
                        for (var anchor : encounter.region().anchors()) rows.add("anchor", anchor.entityId() + " | " + anchor.center());
                    }
                    for (UUID id : encounter.order()) rows.add("turn_order", id);
                    for (UUID id : hostile) rows.add("hostile", id);
                }
                for (var process : processes) {
                    rows.add("process", process.definition().id() + " | " + process.id());
                    rows.add("process_state", process.phase() + " | " + process.steps() + " | " + process.definition().clock());
                    rows.add("process_control", String.join(", ", new TreeSet<>(process.definition().controls())));
                    rows.add("process_release", process.release() + " | cancel=" + process.cancellationRequested());
                }
            }
        }
        if (rows.values.isEmpty()) rows.add("empty", "—");
        var pages = new ArrayList<List<InspectionPolicy.Field>>();
        var current = new ArrayList<InspectionPolicy.Field>(); int bytes = 0;
        for (var field : rows.values) {
            int size = 8 + field.id().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    + field.value().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (current.size() == PAGE_SIZE || bytes + size > 12000) {
                pages.add(List.copyOf(current)); current.clear(); bytes = 0;
            }
            current.add(field); bytes += size;
        }
        if (!current.isEmpty()) pages.add(List.copyOf(current));
        if (page >= pages.size()) throw new IllegalArgumentException("character page no longer exists");
        return new ActorViews.InspectionView(actor.actor(), actor.instance(), actor.revision().capture(),
                pages.get(page), pages.size());
    }
    private static void item(Rows rows, NativeActorObservation.Item item) {
        rows.add("item", item.address() + " | " + item.name() + " ×" + item.count() + " | " + item.id());
        if (item.maximumDamage() > 0) rows.add("durability", Math.max(0, item.maximumDamage() - item.damage()) + " / " + item.maximumDamage());
    }
    private static final class Rows {
        final List<InspectionPolicy.Field> values = new ArrayList<>();
        void add(String name, Object value) {
            if (values.size() >= 8192) return;
            if (values.size() == 8191) { name = "limited"; value = "DETAIL_BUDGET"; }
            String text = Objects.toString(value, "UNKNOWN");
            if (text.length() > 256) text = text.substring(0, 255) + "…";
            values.add(new InspectionPolicy.Field("dndturn:sheet/" + name + "/" + values.size(), text));
        }
    }
}
