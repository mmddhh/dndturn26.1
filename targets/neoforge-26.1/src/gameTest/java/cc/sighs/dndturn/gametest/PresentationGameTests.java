package cc.sighs.dndturn.gametest;

import cc.sighs.dndturn.combat.*;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;

public final class PresentationGameTests {
    private PresentationGameTests() {}
    public static void protocol(GameTestHelper helper) {
        var first = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        var replacement = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        replacement.setUUID(first.getUUID()); replacement.setId(first.getId());
        UUID instance = ((PresentationIdentity)first).dndturn$presentationInstance();
        helper.assertTrue(!instance.equals(((PresentationIdentity)replacement).dndturn$presentationInstance()),
            "entity UUID/runtime ID reuse retained the old instance identity");
        var generation = UUID.randomUUID(); var encounter = UUID.randomUUID(); var operation = UUID.randomUUID();
        for (var cause : PresentationState.Motion.values()) {
            var facts = new PresentationState.Facts(encounter, encounter, "CANDIDATE", true,
                new PresentationState.Use(operation, InteractionHand.OFF_HAND, "complete-stack-value", 7, 13));
            var state = new CombatNetwork.EntitySimulation(generation, 42, first.getUUID(), first.getId(), instance,
                helper.getLevel().dimension().identifier().toString(), true, facts,
                new PresentationState.Movement(operation, 3, 41, cause, .25));
            var buffer = Unpooled.buffer();
            try {
                CombatNetwork.EntitySimulation.STREAM_CODEC.encode(buffer, state);
                helper.assertTrue(state.equals(CombatNetwork.EntitySimulation.STREAM_CODEC.decode(buffer)) && !buffer.isReadable(),
                    "protocol 17 lost ownership, movement or use identity: " + cause);
            } finally { buffer.release(); }
        }
        var swing = new CombatNetwork.TacticalSwing(generation, encounter, first.getUUID(), first.getId(), instance,
            operation, 43, InteractionHand.OFF_HAND, new SwingAnimation(SwingAnimationType.STAB, 11));
        var buffer = Unpooled.buffer();
        try {
            CombatNetwork.TacticalSwing.STREAM_CODEC.encode(buffer, swing);
            helper.assertTrue(swing.equals(CombatNetwork.TacticalSwing.STREAM_CODEC.decode(buffer)), "captured swing did not round trip");
        } finally { buffer.release(); }
        var service = ServerCombatService.forServer(helper.getLevel().getServer());
        helper.assertTrue(service.presentationFacts(first).member() == null
            && service.presentationFacts(first).controller() == null && !service.presentationFacts(first).use().active(),
            "ordinary entity was assigned tactical presentation ownership");
        first.setItemInHand(InteractionHand.OFF_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
        first.startUsingItem(InteractionHand.OFF_HAND);
        UUID useId = ((PresentationUseIdentity)first).dndturn$useIdentity();
        first.startUsingItem(InteractionHand.OFF_HAND);
        helper.assertTrue(useId != null && useId.equals(((PresentationUseIdentity)first).dndturn$useIdentity()), "repeated start changed active use identity");
        first.stopUsingItem();
        helper.assertTrue(((PresentationUseIdentity)first).dndturn$useIdentity() == null, "stop retained use identity");
        first.startUsingItem(InteractionHand.OFF_HAND);
        helper.assertTrue(!useId.equals(((PresentationUseIdentity)first).dndturn$useIdentity()), "new use reused old identity");
        first.stopUsingItem();
        helper.succeed();
    }
}
