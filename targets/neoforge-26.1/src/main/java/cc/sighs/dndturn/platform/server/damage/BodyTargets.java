package cc.sighs.dndturn.platform.server.damage;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Maps a hit body to a single Actor. Registration neither enrolls a member nor grants an attack. */
public final class BodyTargets {
    public interface Adapter {
        LivingEntity root(Entity hit);
        String part(Entity hit);
        Entity resolve(LivingEntity root, String part);
    }
    public record Registration(String id, int version, Adapter adapter) {
        public Registration { FactKey.requireId(id); Objects.requireNonNull(adapter);
            if (version < 1) throw new IllegalArgumentException("body adapter version"); }
    }
    public record Hit(LivingEntity actor, Entity body, ActionIntent.BodyFacet facet) {}
    private static final Map<String, Registration> adapters = new LinkedHashMap<>();
    private static boolean frozen;
    private BodyTargets() {}
    public static synchronized void register(Registration value) {
        if (frozen || adapters.size() >= 128 || adapters.putIfAbsent(value.id(), value) != null)
            throw new IllegalStateException("body adapter registration closed or duplicate");
    }
    public static synchronized void freeze() { frozen = true; }
    public static synchronized ActionIntent.Target identify(Entity hit) {
        if (!(hit.level() instanceof ServerLevel level) || !level.getServer().isSameThread())
            throw new IllegalStateException("server target resolution required");
        Registration selected = null; LivingEntity root = null;
        for (var value : adapters.values()) {
            var candidate = value.adapter().root(hit);
            if (candidate == null) continue;
            if (selected != null) throw new IllegalStateException("BODY_ADAPTER_CONFLICT");
            selected = value; root = candidate;
        }
        if (selected == null) {
            if (!(hit instanceof LivingEntity living)) throw new IllegalStateException("BODY_TARGET_UNSUPPORTED");
            return new ActionIntent.Target(ActionIntent.TargetKind.ENTITY, level.dimension().identifier().toString(),
                    living.getUUID(), null, -1, 0, 0, 0);
        }
        var facet = new ActionIntent.BodyFacet(selected.id(), selected.version(), selected.adapter().part(hit),
                ((PresentationIdentity)root).dndturn$presentationInstance(),
                ((PresentationIdentity)hit).dndturn$presentationInstance());
        var target = new ActionIntent.Target(ActionIntent.TargetKind.ENTITY, level.dimension().identifier().toString(),
                root.getUUID(), null, -1, 0, 0, 0, facet);
        if (resolve(root, facet).body() != hit) throw new IllegalStateException("body adapter roundtrip mismatch");
        return target;
    }
    public static synchronized Hit resolve(LivingEntity root, ActionIntent.BodyFacet facet) {
        if (!(root.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || level.getEntity(root.getUUID()) != root || root.isRemoved())
            throw new IllegalStateException("body root instance unavailable");
        if (facet == null) return new Hit(root, root, null);
        var registered = adapters.get(facet.adapter());
        if (registered == null || registered.version() != facet.version()
                || !facet.instance().equals(((PresentationIdentity)root).dndturn$presentationInstance()))
            throw new IllegalStateException("BODY_FACET_UNAVAILABLE");
        var body = registered.adapter().resolve(root, facet.part());
        if (body == null || body.level() != level || body.isRemoved() || registered.adapter().root(body) != root
                || !facet.bodyInstance().equals(((PresentationIdentity)body).dndturn$presentationInstance())
                || !facet.part().equals(registered.adapter().part(body))) throw new IllegalStateException("BODY_FACET_REPLACED");
        return new Hit(root, body, facet);
    }
}
