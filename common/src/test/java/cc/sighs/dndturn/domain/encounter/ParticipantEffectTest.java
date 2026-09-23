package cc.sighs.dndturn.domain.encounter;

import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.encounter.time.RoundDuration;
import cc.sighs.dndturn.domain.encounter.time.RoundTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParticipantEffectTest {
    @Test void roundsKeepTheirCapturedBaseAndInfinityDoesNotExpire() {
        for(int ticks:new int[]{30,17}) {
            var duration=RoundDuration.capture(new RoundTime(ticks),ticks+1);
            assertEquals(2,duration.rounds());
            assertEquals(ticks,duration.endTurn().remainingTicks());
            assertFalse(duration.endTurn().endTurn().active());
            assertEquals(0,duration.endTurn().endTurn().endTurn().nativeTicks());
            var forever=RoundDuration.capture(new RoundTime(ticks),-1);
            assertEquals(forever,forever.endTurn()); assertEquals(-1,forever.nativeTicks());
        }
        var max=RoundDuration.capture(new RoundTime(30),Integer.MAX_VALUE);
        assertEquals(2147483670L,max.remainingTicks()); assertEquals(Integer.MAX_VALUE,max.nativeTicks());
        assertEquals(2147483640,max.endTurn().nativeTicks());
        assertThrows(IllegalArgumentException.class,()->RoundDuration.capture(new RoundTime(30),-2));
    }
    @Test void endTurnCanAuthorizeOnlyItsOwnerAndDuplicateBoundaryDoesNotAdvanceAgain() {
        var engine=new EncounterAuthority(new Random(40),30,30);
        UUID id=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
        var region=EncounterRegion.generate("test:world",new EncounterRegion.Discovery(-4,-4,-4,4,4,4),
            List.of(new EncounterRegion.Anchor(a,new EncounterRegion.Point(0,0,0))),3,1);
        engine.beginCandidate(id,region,Set.of(a,b)); var view=engine.stateView(id);
        UUID owner=view.current(),other=owner.equals(a)?b:a, op=UUID.randomUUID();
        var root=new OperationRecord.Snapshot(op,null,id,owner,owner,null,1,view.version(),null,null,OperationRecord.Kind.END_TURN);
        assertTrue(engine.beginOperation(root));
        long before=engine.stateView(id).version();
        assertThrows(IllegalArgumentException.class,()->engine.issueOutcomePermit(new EncounterAuthority.OutcomePermit(UUID.randomUUID(),id,op,owner,
            Set.of(other),Set.of(EncounterPhase.CANDIDATE),1,0)));
        assertEquals(before,engine.stateView(id).version());
        UUID permit=UUID.randomUUID();
        engine.issueOutcomePermit(new EncounterAuthority.OutcomePermit(permit,id,op,owner,Set.of(owner),Set.of(EncounterPhase.CANDIDATE),1,0));
        UUID damage=UUID.randomUUID();
        var child=new OperationRecord.Snapshot(damage,op,id,owner,owner,owner,1,engine.stateView(id).version(),null,null,OperationRecord.Kind.DAMAGE);
        assertTrue(engine.beginOperation(child,permit));
        assertThrows(IllegalStateException.class,()->engine.publish(id,op,0,OperationRecord.Outcome.COMPLETED,"end",0,0,true));
        engine.publish(id,damage,0,OperationRecord.Outcome.COMPLETED,"poison",0,1.2f,true);
        var end=engine.publish(id,op,0,OperationRecord.Outcome.COMPLETED,"end",0,0,true);
        long version=engine.stateView(id).version();
        assertSame(end,engine.publish(id,op,0,OperationRecord.Outcome.COMPLETED,"end",0,0,true));
        assertEquals(version,engine.stateView(id).version());
        var late=new OperationRecord.Snapshot(UUID.randomUUID(),op,id,owner,owner,owner,2,version,null,null,OperationRecord.Kind.DAMAGE);
        assertFalse(engine.beginOperation(late,permit));
    }
}
