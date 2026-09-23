package cc.sighs.dndturn.domain.ability;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import java.util.Set;
import static cc.sighs.dndturn.domain.action.ActionIntent.Capability.*;
import static cc.sighs.dndturn.domain.action.ActionIntent.TargetKind.*;

/** Existing gameplay only. Explicit costs preserve the different attack/use acceptance boundaries. */
public final class BuiltinAbilities {
    private static final AbilityRegistry DEFINITIONS = create();
    private BuiltinAbilities() {}
    public static AbilityRegistry create() {
        var r = new AbilityRegistry();
        add(r, "move", "移动", MOVE, ActionCost.MOVE, GROUND);
        add(r, "melee", "近战攻击", ATTACK, ActionCost.ATTACK, ENTITY);
        add(r, "intrinsic_melee", "空手 / 天生攻击", ATTACK, ActionCost.ATTACK, ENTITY);
        add(r, "block", "方块自身交互", USE_BLOCK, ActionCost.FREE, BLOCK);
        add(r, "place", "放置", PLACE, ActionCost.USE, BLOCK);
        add(r, "tool", "物品对方块使用", USE_ITEM, ActionCost.USE, BLOCK);
        add(r, "brush", "刷取 / 完整使用", USE_ITEM, ActionCost.USE, BLOCK);
        add(r, "spawn_item", "放置实体 / 刷怪蛋", USE_ITEM, ActionCost.USE, BLOCK);
        add(r, "fishing_rod", "瞄准抛竿", USE_ITEM, ActionCost.USE, BLOCK);
        add(r, "reel", "收竿", USE_ITEM, ActionCost.USE, ENTITY);
        add(r, "break", "持续破坏", BREAK, ActionCost.USE, BLOCK);
        add(r, "consume", "自身使用 / 食饮", USE_ITEM, ActionCost.USE, SELF);
        add(r, "equip", "穿戴 / 免费整理", EQUIP, ActionCost.FREE, SELF);
        add(r, "bucket", "桶 / 流体", USE_ITEM, ActionCost.USE, BLOCK);
        add(r, "bottle", "装水", USE_ITEM, ActionCost.USE, BLOCK);
        add(r, "entity_item", "物品对实体使用", USE_ITEM, ActionCost.USE, ENTITY);
        add(r, "bow", "使用 minecraft:bow", ATTACK, ActionCost.ATTACK, ENTITY);
        add(r, "crossbow", "使用 minecraft:crossbow", ATTACK, ActionCost.ATTACK, ENTITY);
        add(r, "snowball", "使用 minecraft:snowball", ATTACK, ActionCost.ATTACK, ENTITY);
        add(r, "egg", "瞄准投掷鸡蛋", USE_ITEM, ActionCost.USE, BLOCK, ENTITY);
        add(r, "experience_bottle", "瞄准投掷经验瓶", USE_ITEM, ActionCost.USE, BLOCK, ENTITY);
        add(r, "splash_potion", "瞄准投掷药水", USE_ITEM, ActionCost.USE, BLOCK, ENTITY);
        return r;
    }
    private static void add(AbilityRegistry r, String path, String label, ActionIntent.Capability kind,
                            ActionCost cost, ActionIntent.TargetKind... targets) {
        String id = "dndturn:" + path;
        var policy = switch (path) {
            case "melee", "bow", "crossbow", "snowball" -> AbilityDefinition.TargetPolicy.NON_PLAYER_LIVING_MEMBER;
            case "intrinsic_melee" -> AbilityDefinition.TargetPolicy.OPPOSITE_PLAYER_LIVING_MEMBER;
            default -> AbilityDefinition.TargetPolicy.NATIVE_INTERACTION;
        };
        r.register(new AbilityDefinition(id, 1, label, kind, Set.of(targets),
                AbilityDefinition.Activation.MANUAL, cost, RuleFacts.AVAILABILITY, id, policy));
    }
    static { DEFINITIONS.freeze(); }
    public static AbilityDefinition require(String id) { return DEFINITIONS.require(id, 1); }
}
