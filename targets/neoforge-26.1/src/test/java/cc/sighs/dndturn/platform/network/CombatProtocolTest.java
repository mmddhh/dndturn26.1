package cc.sighs.dndturn.platform.network;

import cc.sighs.dndturn.domain.action.ActionIntent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CombatProtocolTest {
    @Test
    void wireRoundTripPreservesAuthorityAndRejectsIncompleteQueries() {
        var buffer = io.netty.buffer.Unpooled.buffer();
        try {
            UUID id = UUID.randomUUID();
            var member = new EncounterProtocol.MemberNotice(id, "participant", 17,
                3, true, false, 19, false, true);
            var state = new EncounterProtocol.EncounterState(id, id, 2, true, 8,
                cc.sighs.dndturn.domain.encounter.EncounterPhase.ENVIRONMENT, 2, null, 3, 19, false, true, 7, false, null,
                List.of(member), 67, true, 8, 30, null, List.of());
            EncounterProtocol.EncounterState.STREAM_CODEC.encode(buffer, state);
            assertTrue(state.equals(EncounterProtocol.EncounterState.STREAM_CODEC.decode(buffer))
                    && !buffer.isReadable(), "roster, qualification, resources or history count lost on wire");
            buffer.clear();
            var self = new ActionIntent.Target(ActionIntent.TargetKind.SELF, "minecraft:overworld", null, null, -1, 0, 0, 0);
            var query = new ActionProtocol.Query(id, id, id, self, ActionIntent.Hand.MAIN_HAND, 3, 42, null);
            ActionProtocol.Query.CODEC.encode(buffer, query);
            assertTrue(query.equals(ActionProtocol.Query.CODEC.decode(buffer)), "action selection lost on wire");
            buffer.clear();
            ActionProtocol.Query.CODEC.encode(buffer, query);
            var text = net.minecraft.network.codec.ByteBufCodecs.stringUtf8(32768);
            var rawQuery = com.google.gson.JsonParser.parseString(text.decode(buffer)).getAsJsonObject();
            rawQuery.remove("slot");
            text.encode(buffer, rawQuery.toString());
            assertThrows(IllegalArgumentException.class, () -> ActionProtocol.Query.CODEC.decode(buffer),
                "incomplete query silently selected a slot");
            buffer.clear();
            var consent = new EncounterProtocol.ConsentState(id, 5, id, 3, 400, 100,
                true, List.of(new EncounterProtocol.ConsentMember(id, "participant", true)), "waiting");
            EncounterProtocol.ConsentState.STREAM_CODEC.encode(buffer, consent);
            assertTrue(consent.equals(EncounterProtocol.ConsentState.STREAM_CODEC.decode(buffer))
                && !buffer.isReadable(), "consent deadline or roster lost on wire");
            buffer.clear();
            var response = new EncounterProtocol.ConsentReply(id, id, UUID.randomUUID(), id, 3, true);
            EncounterProtocol.ConsentReply.STREAM_CODEC.encode(buffer, response);
            assertTrue(response.equals(EncounterProtocol.ConsentReply.STREAM_CODEC.decode(buffer))
                && !buffer.isReadable(), "consent identity or revision lost on wire");
        } finally { buffer.release(); }
    }
}
