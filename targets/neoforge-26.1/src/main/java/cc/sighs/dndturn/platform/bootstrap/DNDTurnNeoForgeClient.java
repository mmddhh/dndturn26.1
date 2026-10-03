package cc.sighs.dndturn.platform.bootstrap;

import cc.sighs.dndturn.platform.client.action.ClientTacticalPlan;
import cc.sighs.dndturn.platform.client.input.CombatControls;
import cc.sighs.dndturn.platform.client.presentation.ClientPresentation;
import cc.sighs.dndturn.platform.client.presentation.PresentationAdapters;
import cc.sighs.dndturn.platform.client.render.ActionPreviewRenderer;
// DNDTURN-TEMP-BOUNDARY-VIZ (also see the listener and payload registration below)
import cc.sighs.dndturn.platform.client.render.RegionBoundaryRenderer;
import cc.sighs.dndturn.platform.client.simulation.ClientEntitySimulation;
import cc.sighs.dndturn.platform.client.state.ClientCombatState;
import cc.sighs.dndturn.platform.client.state.ClientInspection;
import cc.sighs.dndturn.platform.client.ui.TacticalOverlay;
import cc.sighs.dndturn.platform.network.ActionProtocol;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import cc.sighs.dndturn.platform.network.InspectionProtocol;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = DNDTurnNeoForge.MOD_ID, dist = Dist.CLIENT)
public final class DNDTurnNeoForgeClient {
    public DNDTurnNeoForgeClient(IEventBus modBus) {
        modBus.addListener(DNDTurnNeoForgeClient::registerPayloads);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) ->
            event.enqueueWork(PresentationAdapters::freeze));
        modBus.addListener(CombatControls::registerKeys);
        modBus.addListener(CombatControls::registerHud);
        modBus.addListener(ClientPresentation::register);
        NeoForge.EVENT_BUS.addListener(ClientCombatState::onClientTick);
        NeoForge.EVENT_BUS.addListener(ActionPreviewRenderer::extract);
        NeoForge.EVENT_BUS.addListener(RegionBoundaryRenderer::extract); // DNDTURN-TEMP-BOUNDARY-VIZ
        NeoForge.EVENT_BUS.addListener(ClientPresentation::tick);
        NeoForge.EVENT_BUS.addListener(ClientPresentation::leave);
        NeoForge.EVENT_BUS.addListener(CombatControls::onClientTick);
        NeoForge.EVENT_BUS.addListener(CombatControls::onKeyInput);
        NeoForge.EVENT_BUS.addListener(TacticalOverlay::hideVanillaHotbar);
    }

    private static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(InspectionProtocol.Reply.TYPE, ClientInspection::receive);
        event.register(ActionProtocol.Options.TYPE, ClientTacticalPlan::receiveOptions);
        event.register(ActionProtocol.Projection.TYPE, ClientTacticalPlan::receive);
        event.register(EncounterProtocol.BodyState.TYPE, ClientCombatState::receive);
        event.register(EncounterProtocol.EntitySimulation.TYPE, ClientEntitySimulation::receive);
        event.register(EncounterProtocol.TacticalSwing.TYPE, ClientPresentation::receiveSwing);
        event.register(EncounterProtocol.EncounterState.TYPE, ClientCombatState::receiveEncounter);
        event.register(EncounterProtocol.IntentStatus.TYPE, ClientCombatState::receiveIntentStatus);
        event.register(EncounterProtocol.ConsentState.TYPE, ClientCombatState::receiveConsent);
        // DNDTURN-TEMP-BOUNDARY-VIZ: client handler for the boundary debug payload.
        event.register(cc.sighs.dndturn.platform.network.RegionBoundaryProtocol.Boundary.TYPE,
            RegionBoundaryRenderer::receive);
    }
}
