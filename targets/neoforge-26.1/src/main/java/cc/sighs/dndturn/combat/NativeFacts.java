package cc.sighs.dndturn.combat;

import java.util.LinkedHashMap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Read-only .84 attribute sampling; never runs collectEquipmentChanges or adds weapon damage twice. */
public final class NativeFacts {
    private NativeFacts() {}
    public static NativeActorFacts capture(TacticalActor actor) {
        actor.verifyCurrent();
        var values = new LinkedHashMap<String, NativeActorFacts.Attribute>();
        read(actor, values, "attack_damage", Attributes.ATTACK_DAMAGE);
        read(actor, values, "armor", Attributes.ARMOR);
        read(actor, values, "armor_toughness", Attributes.ARMOR_TOUGHNESS);
        read(actor, values, "movement_speed", Attributes.MOVEMENT_SPEED);
        read(actor, values, "follow_range", Attributes.FOLLOW_RANGE);
        return new NativeActorFacts(actor.id(), actor.instance(), values, actor.body().getBbWidth(),
            actor.body().getBbHeight(), actor.body().getPose().name(), "dndturn:native_attributes", 1);
    }
    private static void read(TacticalActor actor, java.util.Map<String, NativeActorFacts.Attribute> values,
                             String id, Holder<Attribute> type) {
        var attribute = actor.body().getAttribute(type);
        if (attribute != null) values.put(id, new NativeActorFacts.Attribute(attribute.getBaseValue(), attribute.getValue()));
    }
}
