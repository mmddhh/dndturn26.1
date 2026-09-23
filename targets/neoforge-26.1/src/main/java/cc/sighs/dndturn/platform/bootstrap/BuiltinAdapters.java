package cc.sighs.dndturn.platform.bootstrap;

import cc.sighs.dndturn.platform.server.ai.GroundMovementPort;
import cc.sighs.dndturn.platform.server.ai.MovementPorts;

import cc.sighs.dndturn.platform.server.action.VanillaBehaviors;
import cc.sighs.dndturn.platform.server.builtin.ai.VanillaAiFamilies;
import cc.sighs.dndturn.platform.server.builtin.creeper.CreeperAbilities;
import cc.sighs.dndturn.platform.server.builtin.melee.VanillaMeleeAdapters;
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
        SkeletonFamily.register();
        VanillaAiFamilies.register();
        MovementPorts.register(new MovementPorts.Registration(
                "dndturn:ground_navigation", 1, 0, new GroundMovementPort()));
        installed = true;
    }
}
