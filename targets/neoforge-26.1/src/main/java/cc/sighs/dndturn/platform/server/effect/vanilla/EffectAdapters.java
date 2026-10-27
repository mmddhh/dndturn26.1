package cc.sighs.dndturn.platform.server.effect.vanilla;

import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;

/** Server-side semantic selection. Selecting an adapter never grants world execution authority. */
public final class EffectAdapters {
    public enum Tick { NONE, NATIVE_ROUND, HUNGER, SATURATION, NEEDS_ADAPTER }
    public record Adapter(String id, int version, Predicate<MobEffect> matches, Set<String> overrides, Tick tick) {
        public Adapter {
            FactKey.requireId(id); Objects.requireNonNull(matches); Objects.requireNonNull(tick);
            overrides = Set.copyOf(overrides); overrides.forEach(FactKey::requireId);
            if (version < 1 || overrides.contains(id)) throw new IllegalArgumentException("effect adapter identity");
        }
    }
    private static final Map<String, Adapter> entries = new LinkedHashMap<>();
    private static boolean frozen;
    private static final Adapter BASIC = new Adapter("dndturn:basic_effect", 1, e -> true, Set.of(), Tick.NONE);
    private static final Adapter UNKNOWN = new Adapter("dndturn:unadapted_effect", 1, e -> true, Set.of(), Tick.NEEDS_ADAPTER);
    static {
        register(new Adapter("dndturn:native_round_effect", 1, e -> e == MobEffects.REGENERATION.value()
                || e == MobEffects.POISON.value() || e == MobEffects.WITHER.value()
                || e == MobEffects.ABSORPTION.value() || e == MobEffects.INSTANT_HEALTH.value() || e == MobEffects.INSTANT_DAMAGE.value(), Set.of(), Tick.NATIVE_ROUND));
        register(new Adapter("dndturn:hunger", 1, e -> e == MobEffects.HUNGER.value(), Set.of(), Tick.HUNGER));
        register(new Adapter("dndturn:saturation", 1, e -> e == MobEffects.SATURATION.value(), Set.of(), Tick.SATURATION));
    }
    private EffectAdapters() {}
    public static synchronized void register(Adapter adapter) {
        Objects.requireNonNull(adapter);
        if (frozen || entries.size() >= 128 || entries.containsKey(adapter.id()))
            throw new IllegalStateException("effect registration closed, full or duplicate");
        entries.put(adapter.id(), adapter);
    }
    public static synchronized void freeze() {
        for (var adapter : entries.values()) for (String overridden : adapter.overrides())
            if (!entries.containsKey(overridden) || entries.get(overridden).overrides().contains(adapter.id()))
                throw new IllegalStateException("invalid effect override: " + adapter.id());
        for (var effect : net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT) resolve(effect);
        frozen = true;
    }
    public static Adapter resolve(MobEffect effect) {
        var matches = entries.values().stream().filter(a -> a.matches().test(effect)).toList();
        var winners = matches.stream().filter(a -> matches.stream().noneMatch(b -> b.overrides().contains(a.id()))).toList();
        if (matches.isEmpty()) return basic(effect) ? BASIC : UNKNOWN;
        if (winners.size() != 1) throw new IllegalStateException("ambiguous effect adapters");
        return winners.getFirst();
    }
    private static boolean basic(MobEffect effect) {
        // Reflection classifies implementations; it never executes callbacks to discover their behavior.
        for (var method : effect.getClass().getMethods()) {
            if (Set.of("applyEffectTick", "applyInstantenousEffect", "shouldApplyEffectTickThisTick",
                    "onEffectStarted", "onEffectAdded", "onMobHurt", "onMobRemoved").contains(method.getName())
                    && method.getDeclaringClass() != MobEffect.class) return false;
        }
        return true;
    }
    public static boolean supported(MobEffectInstance effect) {
        var value=effect.getEffect().value();
        if (resolve(value).tick()==Tick.NEEDS_ADAPTER) return false;
        // A periodic declaration is not authority for hurt/death/start world callbacks.
        for (var method:value.getClass().getMethods())
            if (Set.of("onMobHurt", "onMobRemoved", "onEffectStarted", "onEffectAdded").contains(method.getName())
                && method.getDeclaringClass()!=MobEffect.class
                && !(value==MobEffects.ABSORPTION.value() && method.getName().equals("onEffectStarted"))) return false;
        return true;
    }
    static boolean apply(Adapter adapter, ServerLevel level, LivingEntity actor, MobEffectInstance effect, int ticks) {
        return switch (adapter.tick()) {
            case NONE, NEEDS_ADAPTER -> true;
            case NATIVE_ROUND -> effect.getEffect().value().applyEffectTick(level, actor, effect.getAmplifier());
            case HUNGER -> {
                if (actor instanceof net.minecraft.world.entity.player.Player player)
                    player.causeFoodExhaustion(0.005F * (effect.getAmplifier() + 1) * ticks);
                yield true;
            }
            case SATURATION -> {
                if (actor instanceof net.minecraft.world.entity.player.Player player)
                    player.getFoodData().eat((int)Math.min(20L, (long)(effect.getAmplifier() + 1) * ticks), 1.0F);
                yield true;
            }
        };
    }
}
