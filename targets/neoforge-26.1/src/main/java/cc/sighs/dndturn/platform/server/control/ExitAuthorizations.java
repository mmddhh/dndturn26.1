package cc.sighs.dndturn.platform.server.control;

import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import java.util.*;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Current regional exemptions. Historical receipts never grant runtime authority. */
public final class ExitAuthorizations {
    public record Grant(UUID owner, UUID encounter, UUID operation, long membershipVersion, String dimension) {
        public Grant {
            Objects.requireNonNull(owner); Objects.requireNonNull(encounter);
            Objects.requireNonNull(operation); Objects.requireNonNull(dimension);
            if (membershipVersion < 0 || dimension.isBlank()) throw new IllegalArgumentException("invalid exit authorization");
        }
    }
    private final Map<UUID, Grant> grants = new HashMap<>();
    private final Map<UUID, UUID> instances = new HashMap<>();
    private static UUID instance(Entity entity) {
        if (!(entity instanceof PresentationIdentity identity))
            throw new IllegalStateException("required entity instance bridge missing");
        return identity.dndturn$presentationInstance();
    }

    public void grant(ServerPlayer player, UUID encounter, UUID operation, long version) {
        grants.put(player.getUUID(), new Grant(player.getUUID(), encounter, operation, version,
            player.level().dimension().identifier().toString()));
        instances.put(player.getUUID(), instance(player));
    }
    public void revoke(UUID owner) { grants.remove(owner); instances.remove(owner); }
    public void restore(List<Grant> saved) {
        for (Grant grant : saved) if (grants.putIfAbsent(grant.owner(), grant) != null)
            throw new IllegalArgumentException("duplicate exit authorization");
    }
    public List<Grant> snapshot() { return List.copyOf(grants.values()); }
    public void reconcile(EncounterAuthority engine, Function<UUID, Entity> resolve) {
        for (Grant grant : List.copyOf(grants.values())) {
            UUID domain = engine.canonicalEncounterId(grant.encounter());
            Entity current = resolve.apply(grant.owner());
            boolean replaced = current != null && instances.containsKey(grant.owner())
                && !instances.get(grant.owner()).equals(instance(current));
            if (!engine.encounterIds().contains(domain) || engine.encounterOf(grant.owner()) != null
                || current != null && (!current.isAlive()
                    || !grant.dimension().equals(current.level().dimension().identifier().toString())
                    || replaced)) {
                revoke(grant.owner());
                continue;
            }
            if (current != null) instances.putIfAbsent(grant.owner(), instance(current));
            if (!domain.equals(grant.encounter())) grants.put(grant.owner(), new Grant(grant.owner(), domain,
                grant.operation(), grant.membershipVersion(), grant.dimension()));
        }
    }
    public boolean permits(Entity entity, UUID encounter) {
        Grant grant = grants.get(entity.getUUID());
        return entity instanceof ServerPlayer && grant != null && grant.encounter().equals(encounter)
            && Objects.equals(instances.get(entity.getUUID()), instance(entity)) && entity.isAlive()
            && grant.dimension().equals(entity.level().dimension().identifier().toString());
    }
}
