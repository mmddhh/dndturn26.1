package cc.sighs.dndturn.domain.fact;

import java.util.Set;

public final class RuleFacts {
    private RuleFacts() {}
    public static final FactKey<Boolean> SOURCE_VALID = new FactKey<>("dndturn:source_valid", Boolean.class);
    public static final FactKey<String> NATIVE_REJECTION = new FactKey<>("dndturn:native_rejection", String.class);
    public static final FactKey<Boolean> IN_REACH = new FactKey<>("dndturn:in_reach", Boolean.class);
    public static final FactKey<Boolean> ACTOR_PLAYER = new FactKey<>("dndturn:actor_player", Boolean.class);
    public static final FactKey<Boolean> TARGET_PLAYER = new FactKey<>("dndturn:target_player", Boolean.class);
    public static final FactKey<Boolean> TARGET_LIVING = new FactKey<>("dndturn:target_living_alive", Boolean.class);
    public static final FactKey<Boolean> DIMENSION_MATCH = new FactKey<>("dndturn:dimension_match", Boolean.class);
    public static final FactKey<Boolean> TARGET_LOADED = new FactKey<>("dndturn:target_loaded", Boolean.class);
    public static final FactKey<Boolean> TARGET_DOMAIN = new FactKey<>("dndturn:target_domain", Boolean.class);
    public static final FactKey<Double> ARMOR = new FactKey<>("dndturn:target_armor", Double.class);
    public static final FactKey<Double> TOUGHNESS = new FactKey<>("dndturn:target_toughness", Double.class);
    public static final FactKey<Boolean> SHIELD = new FactKey<>("dndturn:target_holding_shield", Boolean.class);
    public static final FactKey<Double> RESOLVED_AC = new FactKey<>("dndturn:target_armor_class", Double.class);
    public static final FactKey<Double> RESOLVED_REDUCTION = new FactKey<>("dndturn:target_damage_reduction", Double.class);
    public static final ReadContract AVAILABILITY = new ReadContract(Set.of(SOURCE_VALID, NATIVE_REJECTION, IN_REACH,
            ACTOR_PLAYER, TARGET_PLAYER, TARGET_LIVING, DIMENSION_MATCH, TARGET_LOADED, TARGET_DOMAIN));
    public static final ReadContract DEFENSE = new ReadContract(Set.of(ARMOR, TOUGHNESS, SHIELD, RESOLVED_AC, RESOLVED_REDUCTION));
}
