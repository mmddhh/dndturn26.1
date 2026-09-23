package cc.sighs.dndturn.platform.mixin.server.control;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface MobGoalEvidenceAccess {
    @Accessor("goalSelector") GoalSelector dndturn$goals();
    @Accessor("targetSelector") GoalSelector dndturn$targets();
}
