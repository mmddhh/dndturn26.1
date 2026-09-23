package cc.sighs.dndturn.platform.server.actor;

import cc.sighs.dndturn.domain.actor.ActorDefinition;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;

/** Native matching is confined to this capture boundary. Equal priorities are conflicts. */
public final class ActorDefinitions {
    public interface Provider {
        String id();
        int priority();
        boolean matches(LiveActorContext actor);
        ActorDefinition definition(LiveActorContext actor);
    }
    private static final Map<String, Provider> providers = new LinkedHashMap<>();
    private static boolean frozen;
    private ActorDefinitions() {}
    public static synchronized void register(Provider provider) {
        Objects.requireNonNull(provider); FactKey.requireId(provider.id());
        if (frozen || providers.size() >= 128 || providers.containsKey(provider.id()))
            throw new IllegalStateException("actor definition registration conflict or closed");
        providers.put(provider.id(), provider);
    }
    public static synchronized void freeze() { frozen = true; }
    public static boolean hasProviders() { return !providers.isEmpty(); }
    public static ActorDefinition capture(LiveActorContext actor) {
        actor.verifyCurrent(); Provider selected = null; boolean conflict = false;
        for (var candidate : providers.values()) if (candidate.matches(actor)) {
            if (selected == null || candidate.priority() > selected.priority()) { selected = candidate; conflict = false; }
            else if (candidate.priority() == selected.priority()) conflict = true;
        }
        if (conflict) throw new IllegalStateException("ambiguous actor definition");
        if (selected != null) {
            var definition = Objects.requireNonNull(selected.definition(actor));
            if (definition.defaults().keys().stream().anyMatch(MinecraftFactProviders::owns))
                throw new IllegalArgumentException("actor definition cannot replace native-owned facts");
            return definition;
        }
        return new ActorDefinition(BuiltInRegistries.ENTITY_TYPE.getKey(actor.body().getType()).toString(),
                1, Set.of(), new FactSlice(Map.of(), Map.of()));
    }
}
