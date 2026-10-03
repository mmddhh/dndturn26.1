package cc.sighs.dndturn.platform.bootstrap;

import cc.sighs.dndturn.platform.server.ability.NativeObservations;
import cc.sighs.dndturn.platform.server.damage.BodyTargets;

import cc.sighs.dndturn.platform.network.EncounterProtocol;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.ai.MobTurnStrategies;
import cc.sighs.dndturn.platform.server.builtin.creeper.CreeperExplosion;
import cc.sighs.dndturn.platform.server.damage.MeleeAdapters;
import cc.sighs.dndturn.platform.server.damage.TacticalDamageContext;
import cc.sighs.dndturn.platform.server.network.CombatIntentHandler;
import cc.sighs.dndturn.platform.server.runtime.MinecraftCombatRuntime;
import cc.sighs.dndturn.platform.server.world.EnvironmentExplosion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(DNDTurnNeoForge.MOD_ID)
public final class DNDTurnNeoForge {
    public static final String MOD_ID = "dndturn";

    public DNDTurnNeoForge(IEventBus modBus) {
        BuiltinAdapters.install();
        modBus.addListener(EncounterProtocol::register);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
            event.enqueueWork(() -> {
                AbilityAdapterRegistry.freeze();
                MobTurnStrategies.freeze();
                MeleeAdapters.server().freeze();
                BodyTargets.freeze();
                NativeObservations.REGISTRY.freeze();
            }));
        modBus.addListener(CombatIntentHandler::register);
        NeoForge.EVENT_BUS.addListener(TacticalCommands::register);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onServerTick);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onLogout);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onStartTracking);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onStopTracking);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onDeath);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onPlayerAttack);
        NeoForge.EVENT_BUS.addListener(TacticalDamageContext::onIncoming);
        NeoForge.EVENT_BUS.addListener(EnvironmentExplosion::onDetonate);
        NeoForge.EVENT_BUS.addListener(CreeperExplosion::onDetonate);
        NeoForge.EVENT_BUS.addListener(TacticalDamageContext::onKnockback);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onEntityLeaveLevel);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onServerStopping);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onServerStopped);
    }
}

