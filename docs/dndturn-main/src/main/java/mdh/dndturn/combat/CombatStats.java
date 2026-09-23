package mdh.dndturn.combat;

import mdh.dndturn.integration.StatsBridge;
import net.minecraft.world.entity.LivingEntity;

public final class CombatStats {

    private static StatsBridge bridge = StatsBridge.fallback();

    private CombatStats() {
    }

    public static void setBridge(StatsBridge newBridge) {
        bridge = newBridge;
    }

    public static StatsBridge bridge() {
        return bridge;
    }

    public static int dexterityModifier(LivingEntity entity) {
        return bridge.dexterityModifier(entity);
    }

    public static int speedFeet(LivingEntity entity) {
        return bridge.speedFeet(entity);
    }

    public static int proficiencyBonus(LivingEntity entity) {
        return bridge.proficiencyBonus(entity);
    }

    public static int movementCells(LivingEntity entity) {
        return Math.max(1, speedFeet(entity) / 5);
    }
}
