package cc.sighs.dndturn.application.inspection;

import cc.sighs.dndturn.domain.ability.AbilityBinding;
import cc.sighs.dndturn.domain.ability.AbilityRegistry;
import cc.sighs.dndturn.domain.actor.ActorPersistentState;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.ReadContract;
import java.util.*;

/** Read projections only. Visibility is supplied by a server-authorized observer policy. */
public final class ActorViews {
    private ActorViews() {}
    public record CharacterSheet(UUID actor, ActorSnapshot.Revision revision, FactSlice stats, Map<String, Double> resources) {
        public CharacterSheet { resources = Map.copyOf(resources); }
    }
    public record Spell(UUID grant, String ability, int version, String origin, boolean prepared, String unavailable) {}
    public record Spellbook(UUID actor, long revision, List<Spell> learned, List<AbilityBinding> available) {
        public Spellbook { learned = List.copyOf(learned); available = List.copyOf(available); }
    }
    public record Inspection(UUID actor, FactSlice visible) {}
    /** Filtered wire-safe view; the existing trusted FactSlice API remains source-compatible. */
    public record InspectionView(UUID actor, UUID instance, UUID capture, List<InspectionPolicy.Field> fields) {
        public InspectionView {
            Objects.requireNonNull(actor); Objects.requireNonNull(instance); Objects.requireNonNull(capture);
            fields = List.copyOf(fields);
            if (fields.size() > 32 || fields.stream().map(InspectionPolicy.Field::id).distinct().count() != fields.size())
                throw new IllegalArgumentException("inspection fields");
        }
    }
    public static CharacterSheet character(ActorSnapshot snapshot, ActorStates.State state, ReadContract visible) {
        if (snapshot.revision().actorState() != state.revision()) throw new IllegalArgumentException("mixed actor projection");
        return new CharacterSheet(snapshot.actor(), snapshot.revision(), snapshot.facts().restrict(visible), state.persistent().resources());
    }
    public static Spellbook spellbook(ActorSnapshot snapshot, ActorStates.State state, AbilityRegistry registry) {
        if (snapshot.revision().actorState() != state.revision()) throw new IllegalArgumentException("mixed actor projection");
        var entries = new ArrayList<Spell>();
        state.persistent().grants().values().stream().sorted(Comparator.comparing(ActorPersistentState.Learned::id)).forEach(grant -> {
            boolean prepared = !grant.requiresPreparation() || state.persistent().prepared().contains(grant.id());
            String reason = registry.find(grant.ability(), grant.version()) == null ? "DEFINITION_UNAVAILABLE" : prepared ? "" : "NOT_PREPARED";
            entries.add(new Spell(grant.id(), grant.ability(), grant.version(), grant.origin(), prepared, reason));
        });
        return new Spellbook(snapshot.actor(), state.revision(), entries, snapshot.abilities());
    }
    public static Inspection inspect(ActorSnapshot snapshot, ReadContract authorizedVisibleFacts) {
        return new Inspection(snapshot.actor(), snapshot.facts().restrict(authorizedVisibleFacts));
    }
}
