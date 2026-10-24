package cc.sighs.dndturn.platform.client.state;

import cc.sighs.dndturn.application.projection.EncounterProjectionOrder;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import java.util.UUID;

/** Client prediction gate; the server remains authoritative. */
public final class ClientCombatState {
    private static long consentSequence = -1;
    private static boolean bodyPaused;
    private static boolean activeInputControlled;
    private static boolean movementAllowed;
    private static UUID bodyGeneration;
    private static long bodySequence = -1;
    private static EncounterProtocol.EncounterState encounter;
    private static EncounterProtocol.EncounterState lastEncounter;
    private static EncounterProtocol.IntentStatus latestStatus;
    private ClientCombatState() {}





    private static EncounterProjectionOrder.Stamp stamp(EncounterProtocol.EncounterState state) {
        return new EncounterProjectionOrder.Stamp(state.generation(), state.encounterId(),
            state.sessionSequence(), state.version(), state.active(), state.projectionRevision());
    }

    public static boolean applyConsentState(EncounterProtocol.ConsentState state) {

            if (state.sequence() <= consentSequence
                || bodyGeneration != null && !bodyGeneration.equals(state.generation())
                || encounter != null && !encounter.generation().equals(state.generation())) return false;
            consentSequence = state.sequence();

        return true;
    }

    public static boolean applyBodyState(EncounterProtocol.BodyState state) {


            if (bodyGeneration != null && bodyGeneration.equals(state.generation())
                && state.sequence() <= bodySequence) return false;
            if (encounter != null && !encounter.generation().equals(state.generation())) return false;
            bodyGeneration = state.generation();
            bodySequence = state.sequence();
            bodyPaused = state.bodyPaused();
            activeInputControlled = state.activeInputControlled();
            movementAllowed = state.movementAllowed();

        return true;
    }

    public static EncounterProjectionOrder.Decision applyEncounterState(EncounterProtocol.EncounterState state) {


            var decision = EncounterProjectionOrder.decide(
                lastEncounter == null ? null : stamp(lastEncounter), stamp(state));
            if (decision == EncounterProjectionOrder.Decision.IGNORE) return EncounterProjectionOrder.Decision.IGNORE;
            if (decision == EncounterProjectionOrder.Decision.ACCEPT_RESET_RESULTS) {
                latestStatus = null;
            }
            lastEncounter = state;
            encounter = state.active() ? state : null;


        return decision;
    }

    public static EncounterProtocol.EncounterState encounter() { return encounter; }
    public static EncounterProtocol.IntentStatus latestStatus() { return latestStatus; }

    public static boolean applyIntentStatus(EncounterProtocol.IntentStatus status) {


            if (status.replay()) {
                if (bodyGeneration != null && !bodyGeneration.equals(status.generation())
                    || encounter != null && !encounter.generation().equals(status.generation())
                    || encounter == null && lastEncounter != null
                        && !lastEncounter.generation().equals(status.generation())) return false;
                latestStatus = status;
                return true;
            }
            if (encounter != null && !encounter.generation().equals(status.generation())) return false;
            if (status.encounterId() != null && encounter != null
                && !encounter.encounterId().equals(status.encounterId())) return false;
            if (status.encounterId() != null && encounter == null && lastEncounter != null
                && (!lastEncounter.generation().equals(status.generation())
                    || !lastEncounter.encounterId().equals(status.encounterId()))) return false;
            latestStatus = status;


        return true;
    }

    public static boolean movementAllowed() { return movementAllowed; }

    public static boolean holdBodySubsystemsDuringMovement() {
        return bodyPaused && movementAllowed;
    }

    public static boolean gameplayPaused() {
        return activeInputControlled || bodyPaused || encounter != null && encounter.active();
    }

    public static boolean bodySimulationPaused() { return bodyPaused && !movementAllowed; }




    public static void reset() {
        consentSequence = -1; bodyPaused = false; activeInputControlled = false; movementAllowed = false;
        bodyGeneration = null; bodySequence = -1; encounter = null; lastEncounter = null; latestStatus = null;
    }
}
