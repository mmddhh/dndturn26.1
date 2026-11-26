package cc.sighs.dndturn.content.mobs;

import cc.sighs.dndturn.platform.server.ai.MovementPorts;
import cc.sighs.dndturn.content.mobs.server.ai.VanillaAiFamilies;
import cc.sighs.dndturn.content.mobs.server.creeper.CreeperAbilities;
import cc.sighs.dndturn.content.mobs.server.evoker.EvokerAbilities;
import cc.sighs.dndturn.content.mobs.server.evoker.FlyingMovementPort;
import cc.sighs.dndturn.content.mobs.server.ghast.GhastAbilities;
import cc.sighs.dndturn.content.mobs.server.melee.VanillaMeleeAdapters;
import cc.sighs.dndturn.content.mobs.server.mob.ContentMobActions;
import cc.sighs.dndturn.content.mobs.server.skeleton.SkeletonFamily;
import cc.sighs.dndturn.content.mobs.server.creeper.CreeperExplosion;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod("dndturn_mobs")
public final class MobContent {
    public MobContent() {
        VanillaMeleeAdapters.register();
        CreeperAbilities.register();
        GhastAbilities.register();
        EvokerAbilities.register();
        ContentMobActions.register();
        SkeletonFamily.register();
        VanillaAiFamilies.register();
        MovementPorts.register(new MovementPorts.Registration(
                "dndturn:flying_navigation", 1, 10, new FlyingMovementPort()));
        NeoForge.EVENT_BUS.addListener(CreeperExplosion::onDetonate);
    }
}
