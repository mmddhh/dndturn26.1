package cc.sighs.dndturn.platform.server.fact;

import cc.sighs.dndturn.application.inspection.InspectionPolicy;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import java.util.*;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Demand-driven native providers. Values are copied, never the AttributeMap or equipment objects. */
public final class MinecraftFactProviders {
    private record AttributeRead(Holder<Attribute> type, boolean base) {}
    private static final Map<FactKey<?>, AttributeRead> ATTRIBUTES = new LinkedHashMap<>();
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
        attribute(ActorFacts.MAX_HEALTH_BASE, ActorFacts.MAX_HEALTH, Attributes.MAX_HEALTH);
        register(ActorFacts.HEALTH, actor -> (double) actor.body().getHealth());
        register(ActorFacts.ABSORPTION, actor -> (double) actor.body().getAbsorptionAmount());
        register(ActorFacts.ALIVE, actor -> actor.body().isAlive());
        register(ActorFacts.DIMENSION, actor -> actor.level().dimension().identifier().toString());
        register(ActorFacts.X, actor -> actor.body().getX());
        register(ActorFacts.Y, actor -> actor.body().getY());
        register(ActorFacts.Z, actor -> actor.body().getZ());
        register(ActorFacts.YAW, actor -> (double) actor.body().getYRot());
        register(ActorFacts.PITCH, actor -> (double) actor.body().getXRot());
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
        ATTRIBUTES.put(base, new AttributeRead(type, true)); ATTRIBUTES.put(effective, new AttributeRead(type, false));
        register(base, actor -> {
            var attribute = actor.body().getAttribute(type);
            return attribute == null ? null : attribute.getBaseValue();
        });
        register(effective, actor -> {
            var attribute = actor.body().getAttribute(type);
            return attribute == null ? null : attribute.getValue();
        });
    }
    public static FactSlice captureObserved(LiveActorContext actor, ReadContract contract,
            cc.sighs.dndturn.domain.actor.NativeActorObservation observed) {
        actor.verifyCurrent();
        if (!actor.id().equals(observed.actor()) || !actor.instance().equals(observed.instance()))
            throw new IllegalArgumentException("native observation instance");
        var values = new LinkedHashMap<FactKey<?>, Object>();
        ATTRIBUTES.forEach((key, reader) -> {
            var attribute = observed.attributes().get(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getKey(reader.type().value()).toString());
            if (contract.keys().contains(key) && attribute != null) values.put(key, reader.base() ? attribute.base() : attribute.effective());
        });
        var body = observed.body(); var vitals = observed.vitals(); var transform = observed.transform();
        Map<FactKey<?>, Object> direct = Map.ofEntries(
                Map.entry(InspectionPolicy.NAME, observed.name()), Map.entry(InspectionPolicy.TYPE, observed.type()),
                Map.entry(ActorFacts.HEALTH, vitals.health()), Map.entry(ActorFacts.ABSORPTION, vitals.absorption()), Map.entry(ActorFacts.ALIVE, vitals.alive()),
                Map.entry(ActorFacts.WIDTH, body.width()), Map.entry(ActorFacts.HEIGHT, body.height()), Map.entry(ActorFacts.POSE, body.pose()),
                Map.entry(ActorFacts.DIMENSION, transform.dimension()), Map.entry(ActorFacts.X, transform.x()), Map.entry(ActorFacts.Y, transform.y()),
                Map.entry(ActorFacts.Z, transform.z()), Map.entry(ActorFacts.YAW, transform.yaw()), Map.entry(ActorFacts.PITCH, transform.pitch()));
        direct.forEach((key, value) -> { if (contract.keys().contains(key)) values.put(key, value); });
        var remaining = new HashSet<>(contract.keys()); remaining.removeAll(values.keySet());
        return new FactSlice(values, Map.of()).merge(capture(actor, new ReadContract(remaining)));
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
