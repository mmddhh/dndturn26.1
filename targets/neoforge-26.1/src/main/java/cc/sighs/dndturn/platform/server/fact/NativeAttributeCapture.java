package cc.sighs.dndturn.platform.server.fact;

import cc.sighs.dndturn.domain.actor.NativeAttributeObservation;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Bounded explanation capture. Missing attribution never changes the observed effective value. */
public final class NativeAttributeCapture {
    private NativeAttributeCapture() {}
    private static final class ScanLimit extends RuntimeException {
        ScanLimit() { super(null, null, false, false); }
    }
    public static final class Budget {
        private int remaining = 2048;
        private void visit() { if (--remaining < 0) throw new ScanLimit(); }
    }
    public static NativeAttributeObservation capture(LiveActorContext actor, Holder<Attribute> type, Budget budget) {
        actor.verifyCurrent();
        var attribute = actor.body().getAttribute(type);
        if (attribute == null) return null;
        double base = attribute.getBaseValue(), effective = attribute.getValue();
        var active = attribute.getModifiers();
        // Do not sort or copy an unbounded third-party collection.
        if (active.size() > 64) return new NativeAttributeObservation(base, effective, List.of(), false);
        var modifiers = active.stream().sorted(Comparator.comparing(m -> m.id().toString())).toList();
        var matches = new HashMap<AttributeModifier, List<NativeAttributeObservation.EquipmentMatch>>();
        var effects = new HashMap<AttributeModifier, List<String>>();
        int[] examined = {0};
        boolean complete = true;
        try {
            for (var slot : EquipmentSlot.values()) {
                var stack = actor.body().getItemBySlot(slot);
                if (stack.isEmpty() || stack.isBroken()) continue;
                stack.forEachModifier(slot, (key, modifier) -> {
                    budget.visit();
                    if (++examined[0] > 256) throw new ScanLimit();
                    if (key.equals(type) && modifiers.contains(modifier))
                        matches.computeIfAbsent(modifier, unused -> new ArrayList<>()).add(
                                new NativeAttributeObservation.EquipmentMatch(slot.getName(),
                                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
                });
            }
            int effectCount = 0;
            for (var effect : actor.body().getActiveEffects()) {
                if (++effectCount > 256) throw new ScanLimit();
                effect.getEffect().value().createModifiers(effect.getAmplifier(), (key, modifier) -> {
                    budget.visit();
                    if (++examined[0] > 256) throw new ScanLimit();
                    if (key.equals(type) && modifiers.contains(modifier))
                        effects.computeIfAbsent(modifier, unused -> new ArrayList<>()).add(
                                BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString());
                });
            }
        } catch (ScanLimit limit) {
            complete = false;
            matches.clear(); // A later declaration could make any earlier match ambiguous.
            effects.clear();
        }
        var result = new ArrayList<NativeAttributeObservation.Modifier>();
        for (var modifier : modifiers) {
            var candidates = matches.getOrDefault(modifier, List.of());
            var effectCandidates = effects.getOrDefault(modifier, List.of());
            boolean unique = candidates.size() + effectCandidates.size() == 1;
            result.add(new NativeAttributeObservation.Modifier(modifier.id().toString(),
                    NativeAttributeObservation.Operation.valueOf(modifier.operation().name()), modifier.amount(),
                    unique && candidates.size() == 1 ? candidates.getFirst() : null,
                    unique && effectCandidates.size() == 1 ? effectCandidates.getFirst() : null));
        }
        return new NativeAttributeObservation(base, effective, result, complete);
    }
}
