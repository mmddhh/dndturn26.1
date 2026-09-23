package mdh.dndturn.integration;

import net.minecraft.world.entity.LivingEntity;

public interface StatsBridge {

    int dexterityModifier(LivingEntity entity);

    int speedFeet(LivingEntity entity);

    int proficiencyBonus(LivingEntity entity);

    static StatsBridge fallback() {
        return new StatsBridge() {
            @Override
            public int dexterityModifier(LivingEntity entity) {
                return 0;
            }

            @Override
            public int speedFeet(LivingEntity entity) {
                return 30;
            }

            @Override
            public int proficiencyBonus(LivingEntity entity) {
                return 2;
            }
        };
    }
}
