package cc.sighs.dndturn;

import cc.sighs.dndturn.client.ClientCombatState;
import cc.sighs.dndturn.client.CombatControls;
import cc.sighs.dndturn.combat.CombatNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@Mod(value = DNDTurnNeoForge261.MOD_ID, dist = Dist.CLIENT)
public final class DNDTurnNeoForge261Client {
    public DNDTurnNeoForge261Client(IEventBus modBus) {
        modBus.addListener(DNDTurnNeoForge261Client::registerPayloads);
        modBus.addListener(CombatControls::registerKeys);
        modBus.addListener(CombatControls::registerHud);
        NeoForge.EVENT_BUS.addListener(ClientCombatState::onClientTick);
        NeoForge.EVENT_BUS.addListener(CombatControls::onClientTick);
        NeoForge.EVENT_BUS.addListener(CombatControls::onKeyInput);
    }

    private static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(cc.sighs.dndturn.combat.TacticalNetwork.Options.TYPE, cc.sighs.dndturn.client.ClientTacticalPlan::receiveOptions);
        event.register(cc.sighs.dndturn.combat.TacticalNetwork.Projection.TYPE, cc.sighs.dndturn.client.ClientTacticalPlan::receive);
        event.register(CombatNetwork.BodyState.TYPE, ClientCombatState::receive);
        event.register(CombatNetwork.EntitySimulation.TYPE, cc.sighs.dndturn.client.ClientEntitySimulation::receive);
        event.register(CombatNetwork.EncounterState.TYPE, ClientCombatState::receiveEncounter);
        event.register(CombatNetwork.ResultNotice.TYPE, ClientCombatState::receiveResult);
        event.register(CombatNetwork.IntentStatus.TYPE, ClientCombatState::receiveIntentStatus);
        event.register(CombatNetwork.ConsentState.TYPE, ClientCombatState::receiveConsent);
    }
}
