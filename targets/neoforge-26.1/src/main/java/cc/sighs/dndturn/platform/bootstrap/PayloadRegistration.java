package cc.sighs.dndturn.platform.bootstrap;
import cc.sighs.dndturn.platform.network.*;
import static cc.sighs.dndturn.platform.network.EncounterProtocol.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public final class PayloadRegistration {
 private PayloadRegistration() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("dndturn");
        event.registrar("dndturn").playToClient(InspectionProtocol.Reply.TYPE, InspectionProtocol.Reply.CODEC);
        registrar.playToClient(ActionProtocol.Options.TYPE, ActionProtocol.Options.CODEC);
        registrar.playToClient(ActionProtocol.Projection.TYPE, ActionProtocol.Projection.CODEC);
        registrar.playToClient(BodyState.TYPE, BodyState.STREAM_CODEC);
        registrar.playToClient(EntitySimulation.TYPE, EntitySimulation.STREAM_CODEC);
        registrar.playToClient(TacticalSwing.TYPE, TacticalSwing.STREAM_CODEC);
        registrar.playToClient(EncounterState.TYPE, EncounterState.STREAM_CODEC);
        registrar.playToClient(IntentStatus.TYPE, IntentStatus.STREAM_CODEC);
        registrar.playToClient(ConsentState.TYPE, ConsentState.STREAM_CODEC);
    }
}
