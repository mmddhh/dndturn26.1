package cc.sighs.dndturn.domain.fact;

import java.util.Set;

/** Native input units remain distinct from derived DND statistics. */
public final class ActorFacts {
    private ActorFacts() {}
    public static final FactKey<Double> ATTACK_BASE = number("minecraft:attack_damage/base");
    public static final FactKey<Double> ATTACK_EFFECTIVE = number("minecraft:attack_damage/effective");
    public static final FactKey<Double> ARMOR_BASE = number("minecraft:armor/base");
    public static final FactKey<Double> ARMOR_EFFECTIVE = number("minecraft:armor/effective");
    public static final FactKey<Double> TOUGHNESS_BASE = number("minecraft:armor_toughness/base");
    public static final FactKey<Double> TOUGHNESS_EFFECTIVE = number("minecraft:armor_toughness/effective");
    public static final FactKey<Double> SPEED_BASE = number("minecraft:movement_speed/base");
    public static final FactKey<Double> SPEED_EFFECTIVE = number("minecraft:movement_speed/effective");
    public static final FactKey<Double> FOLLOW_BASE = number("minecraft:follow_range/base");
    public static final FactKey<Double> FOLLOW_EFFECTIVE = number("minecraft:follow_range/effective");
    public static final FactKey<Double> WIDTH = number("minecraft:body_width");
    public static final FactKey<Double> HEIGHT = number("minecraft:body_height");
    public static final FactKey<String> POSE = new FactKey<>("minecraft:pose", String.class);
    public static final FactKey<Boolean> HOLDING_SHIELD = new FactKey<>("minecraft:holding_shield", Boolean.class);
    public static final FactKey<Double> ARMOR_CLASS = number("dndturn:armor_class");
    public static final FactKey<Double> DAMAGE_REDUCTION = number("dndturn:damage_reduction");
    public static final ReadContract BODY = new ReadContract(Set.of(WIDTH, HEIGHT, POSE));
    public static final ReadContract DECISION = new ReadContract(Set.of(ATTACK_BASE, ATTACK_EFFECTIVE,
            ARMOR_BASE, ARMOR_EFFECTIVE, TOUGHNESS_BASE, TOUGHNESS_EFFECTIVE, SPEED_BASE, SPEED_EFFECTIVE,
            FOLLOW_BASE, FOLLOW_EFFECTIVE, WIDTH, HEIGHT, POSE, HOLDING_SHIELD));
    private static FactKey<Double> number(String id) { return new FactKey<>(id, Double.class); }
}
