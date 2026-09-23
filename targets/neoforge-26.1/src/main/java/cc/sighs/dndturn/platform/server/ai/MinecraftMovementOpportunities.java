package cc.sighs.dndturn.platform.server.ai;

import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.action.MinecraftCoordinates;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.RandomPos;

/** Audited .84 geometry sampler; emits proposals, never starts Navigation. */
final class MinecraftMovementOpportunities {
    private MinecraftMovementOpportunities() {}
        static ActionIntent movement(LiveActorContext actor, EncounterAuthority.StateView state, OperationRecord.Result previous) {
            if (previous != null) return null;
            var mob = (PathfinderMob)actor.body();
            if (mob.isNoAi() || mob.isPassenger() || mob.hasControllingPassenger() || state.members().get(actor.id()).movementTicks() < 1) return null;
            var origin = mob.blockPosition();
            if (!actor.level().hasChunksAt(origin.offset(-10, -7, -10), origin.offset(10, 7, 10))) return null;
            var path = mob.getNavigation().getPath();
            var end = path == null || mob.getNavigation().isDone() ? null : path.getEndNode();
            var position = end == null ? stroll(actor, state, mob) : new net.minecraft.world.phys.Vec3(end.x + .5, end.y, end.z + .5);
            if (position == null || !state.region().containsPoint(position.x, position.y, position.z)) return null;
            var cell = MinecraftCoordinates.cell(net.minecraft.core.BlockPos.containing(position));
            return new ActionIntent("dndturn:move", 1, ActionIntent.Capability.MOVE,
                new ActionIntent.Target(ActionIntent.TargetKind.GROUND, state.region().dimension(), null, cell, -1, 0, 0, 0), GrantEvidence.basic());
        }
        /** Same bounded ten-candidate sampler as .84 DefaultRandomPos, with decision-owned entropy.
         * Re-evaluation cannot advance the entity's combat/vanilla random stream. */
        private static net.minecraft.world.phys.Vec3 stroll(LiveActorContext actor, EncounterAuthority.StateView state, PathfinderMob mob) {
            long seed = actor.id().getMostSignificantBits() ^ actor.id().getLeastSignificantBits()
                ^ state.id().getMostSignificantBits() ^ Long.rotateLeft(state.round(), 23);
            RandomSource random = RandomSource.create(seed);
            boolean restrict = GoalUtils.mobRestricted(mob, 10);
            return RandomPos.generateRandomPos(mob, () -> {
                var direction = RandomPos.generateRandomDirection(random, 10, 7);
                var pos = RandomPos.generateRandomPosTowardDirection(mob, 10, random, direction);
                if (!actor.level().hasChunkAt(pos) || GoalUtils.isOutsideLimits(pos, mob)
                    || GoalUtils.isRestricted(restrict, mob, pos) || GoalUtils.isNotStable(mob.getNavigation(), pos)
                    || GoalUtils.hasMalus(mob, pos)) return null;
                return pos;
            });
        }
}
