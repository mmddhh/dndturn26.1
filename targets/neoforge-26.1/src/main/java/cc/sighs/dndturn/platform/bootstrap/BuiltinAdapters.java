package cc.sighs.dndturn.platform.bootstrap;

import cc.sighs.dndturn.platform.server.ai.GroundMovementPort;
import cc.sighs.dndturn.platform.server.ai.FlyingMovementPort;
import cc.sighs.dndturn.platform.server.ai.MovementPorts;

import cc.sighs.dndturn.platform.server.action.VanillaBehaviors;
import cc.sighs.dndturn.platform.server.builtin.ai.VanillaAiFamilies;
import cc.sighs.dndturn.platform.server.builtin.creeper.CreeperAbilities;
import cc.sighs.dndturn.platform.server.builtin.evoker.EvokerAbilities;
import cc.sighs.dndturn.platform.server.builtin.ghast.GhastAbilities;
import cc.sighs.dndturn.platform.server.builtin.melee.VanillaMeleeAdapters;
import cc.sighs.dndturn.platform.server.builtin.mob.ContentMobActions;
import cc.sighs.dndturn.platform.server.builtin.skeleton.SkeletonFamily;

/** Composition root installs builtins through the same registration contracts as extensions. */
public final class BuiltinAdapters {
    private static boolean installed;
    private BuiltinAdapters() {}
    public static synchronized void install() {
        if (installed) return;
        VanillaMeleeAdapters.register();
        VanillaBehaviors.register();
        CreeperAbilities.register();
        GhastAbilities.register();
        EvokerAbilities.register();
        ContentMobActions.register();
        SkeletonFamily.register();
        VanillaAiFamilies.register();
        MovementPorts.register(new MovementPorts.Registration(
                "dndturn:ground_navigation", 1, 0, new GroundMovementPort()));
        MovementPorts.register(new MovementPorts.Registration(
                "dndturn:flying_navigation", 1, 10, new FlyingMovementPort()));
        installed = true;
    }
}
