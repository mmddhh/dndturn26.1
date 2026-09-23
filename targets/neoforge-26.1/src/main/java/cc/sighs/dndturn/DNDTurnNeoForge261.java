package cc.sighs.dndturn;

import cc.sighs.dndturn.combat.CombatCommands;
import cc.sighs.dndturn.combat.CombatNetwork;
import cc.sighs.dndturn.combat.MinecraftCombatRuntime;
import cc.sighs.dndturn.combat.TacticalDamageContext;
import cc.sighs.dndturn.gametest.LocalTimeGameTests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(DNDTurnNeoForge261.MOD_ID)
public final class DNDTurnNeoForge261 {
    public static final String MOD_ID = "dndturn";

    public DNDTurnNeoForge261(IEventBus modBus) {
        modBus.addListener(CombatNetwork::register);
        modBus.addListener(cc.sighs.dndturn.combat.CombatIntentHandler::register);
        LocalTimeGameTests.TEST_FUNCTIONS.register(modBus);
        NeoForge.EVENT_BUS.addListener(CombatCommands::register);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onEntityTick);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onServerTick);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onLogout);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onStartTracking);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onStopTracking);
        NeoForge.EVENT_BUS.addListener(MinecraftCombatRuntime::onDeath);
        NeoForge.EVENT_BUS.addListener(TacticalDamageContext::onIncoming);
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

