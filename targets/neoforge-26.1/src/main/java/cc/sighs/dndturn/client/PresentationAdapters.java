package cc.sighs.dndturn.client;

import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/** Client-only registration. Model state belongs to channels, never the simulation or core runtime. */
public final class PresentationAdapters {
    public enum Channel { AGE, GAIT, SPECIAL, ATTACK, USE, HURT }
    public enum Port { HUMANOID_HANDS }
    public record Attack(UUID operation, long sequence, net.minecraft.world.InteractionHand hand,
                         net.minecraft.world.item.component.SwingAnimation animation, int elapsed) {}
    public record Input(boolean controlled, float age, double horizontal, String phase,
                        cc.sighs.dndturn.combat.PresentationState.Use use, Attack attack,
                        int hurt, int death) {}
    @FunctionalInterface public interface Frame {
        void render(EntityRenderState target, float weight, float partial);
    }
    public interface State {
        /** Called once per client update. Must not mutate the entity or retain it. */
        Frame update(Entity entity, Input input);
    }
    public record Adapter(String id, int version, Set<Channel> channels, Set<Port> ports,
                          BiPredicate<Entity, Object> matches, Function<Entity, State> create) {
        public Adapter(String id, int version, Set<Channel> channels,
                       BiPredicate<Entity, Object> matches, Function<Entity, State> create) {
            this(id, version, channels, Set.of(), matches, create);
        }
        public Adapter {
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length() > 128 || version < 1)
                throw new IllegalArgumentException("presentation identity");
            channels = Set.copyOf(channels);
            ports = Set.copyOf(ports);
            if (channels.isEmpty()) throw new IllegalArgumentException("presentation channels required");
            Objects.requireNonNull(matches); Objects.requireNonNull(create);
        }
    }
    private static final Map<String, Adapter> adapters = new LinkedHashMap<>();
    private static boolean frozen;
    static { BuiltinPresentation.register(); }
    private PresentationAdapters() {}
    public static synchronized void register(Adapter adapter) {
        if (frozen) throw new IllegalStateException("presentation registry frozen");
        if (adapters.size() >= 64 || adapters.putIfAbsent(adapter.id(), adapter) != null)
            throw new IllegalArgumentException("duplicate or excessive presentation registration: " + adapter.id());
    }
    public static synchronized void freeze() { frozen = true; }
    public static Object renderer(Entity entity) {
        return Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
    }
    public static List<Adapter> resolve(Entity entity) {
        Object renderer = renderer(entity);
        var found = new ArrayList<Adapter>();
        var owned = EnumSet.noneOf(Channel.class);
        for (var adapter : adapters.values()) if (adapter.matches().test(entity, renderer)) {
            if (!Collections.disjoint(owned, adapter.channels()))
                throw new IllegalStateException("presentation channel conflict: " + adapter.id());
            owned.addAll(adapter.channels()); found.add(adapter);
        }
        return List.copyOf(found);
    }
    public static boolean humanoid(Entity entity) {
        return resolve(entity).stream().anyMatch(a -> a.ports().contains(Port.HUMANOID_HANDS));
    }
    public static boolean supported(Entity entity) { return !resolve(entity).isEmpty(); }
}
