package cc.sighs.dndturn.platform.server.actor;

import cc.sighs.dndturn.application.inspection.InspectionPolicy;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import java.util.*;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Demand-driven native providers. Values are copied, never the AttributeMap or equipment objects. */
public final class MinecraftFactProviders {
    private static final Map<FactKey<?>, Function<LiveActorContext, ?>> PROVIDERS = new LinkedHashMap<>();
    private static boolean frozen;
    static {
        register(InspectionPolicy.NAME, actor -> actor.body().getName().getString(256));
        register(InspectionPolicy.TYPE, actor -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(actor.body().getType()).toString());
        attribute(ActorFacts.ATTACK_BASE, ActorFacts.ATTACK_EFFECTIVE, Attributes.ATTACK_DAMAGE);
        attribute(ActorFacts.ARMOR_BASE, ActorFacts.ARMOR_EFFECTIVE, Attributes.ARMOR);
        attribute(ActorFacts.TOUGHNESS_BASE, ActorFacts.TOUGHNESS_EFFECTIVE, Attributes.ARMOR_TOUGHNESS);
        attribute(ActorFacts.SPEED_BASE, ActorFacts.SPEED_EFFECTIVE, Attributes.MOVEMENT_SPEED);
        attribute(ActorFacts.FOLLOW_BASE, ActorFacts.FOLLOW_EFFECTIVE, Attributes.FOLLOW_RANGE);
        register(ActorFacts.WIDTH, actor -> (double) actor.body().getBbWidth());
        register(ActorFacts.HEIGHT, actor -> (double) actor.body().getBbHeight());
        register(ActorFacts.POSE, actor -> actor.body().getPose().name());
        register(ActorFacts.HOLDING_SHIELD, actor -> actor.body().getMainHandItem().is(net.minecraft.world.item.Items.SHIELD)
                || actor.body().getOffhandItem().is(net.minecraft.world.item.Items.SHIELD));
    }
    private MinecraftFactProviders() {}
    /** Null means the exact native fact is unsupported for this actor. No fallback value is synthesized. */
    public static synchronized <T> void register(FactKey<T> key, Function<LiveActorContext, T> provider) {
        Objects.requireNonNull(key); Objects.requireNonNull(provider);
        if (frozen) throw new IllegalStateException("native fact registry frozen");
        if (PROVIDERS.size() >= 128 || PROVIDERS.keySet().stream().anyMatch(k -> k.id().equals(key.id())))
            throw new IllegalArgumentException("duplicate fact provider or capacity exceeded");
        PROVIDERS.put(key, provider);
    }
    public static synchronized void freeze() { frozen = true; }
    public static synchronized boolean owns(FactKey<?> key) { return PROVIDERS.containsKey(key); }
    private static void attribute(FactKey<Double> base, FactKey<Double> effective, Holder<Attribute> type) {
        register(base, actor -> {
            var attribute = actor.body().getAttribute(type);
            return attribute == null ? null : attribute.getBaseValue();
        });
        register(effective, actor -> {
            var attribute = actor.body().getAttribute(type);
            return attribute == null ? null : attribute.getValue();
        });
    }
    public static FactSlice capture(LiveActorContext actor, ReadContract contract) {
        actor.verifyCurrent();
        var values = new LinkedHashMap<FactKey<?>, Object>();
        var missing = new LinkedHashMap<FactKey<?>, FactSlice.Missing>();
        for (var key : contract.keys()) {
            var provider = PROVIDERS.get(key);
            Object value = provider == null ? null : provider.apply(actor);
            if (value == null) missing.put(key, FactSlice.Missing.UNSUPPORTED);
            else values.put(key, value);
        }
        return new FactSlice(values, missing);
    }
}
