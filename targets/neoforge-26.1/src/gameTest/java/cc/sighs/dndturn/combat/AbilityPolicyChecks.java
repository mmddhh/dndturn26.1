package cc.sighs.dndturn.combat;

import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Independent registration: sharing an intrinsic provider must not imply punch effects. */
public final class AbilityPolicyChecks {
    private static AbilitySource statusGrant;
    private AbilityPolicyChecks() {}
    public static void register() {
        TacticalCapabilities.register(new TacticalBehavior("test:status_attack", "Status test attack",
            TacticalIntent.Capability.ATTACK, Set.of(TacticalIntent.TargetKind.ENTITY)) {
            public AbilitySource source(TacticalActor actor, TacticalIntent.Hand hand) {
                return statusGrant != null && statusGrant.actor().equals(actor.id())
                    && statusGrant.instance().equals(actor.instance()) ? statusGrant : null;
            }
            public String unavailable(TacticalActor actor, TacticalIntent intent, CombatEngine.StateView state) { return null; }
            public boolean canExecute(TacticalActor actor, TacticalIntent intent, Vec3 feet) { return true; }
            public void start(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution) {
                throw new IllegalStateException("source-lifetime fixture has no world driver");
            }
            public void tick(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution) {
                throw new IllegalStateException("source-lifetime fixture has no world driver");
            }
        });
        TacticalCapabilities.register(new TacticalBehavior("test:natural_no_push", "Natural test attack",
            TacticalIntent.Capability.ATTACK, Set.of(TacticalIntent.TargetKind.ENTITY)) {
            private final IntrinsicMelee reach = new IntrinsicMelee();
            public AbilitySource source(TacticalActor actor, TacticalIntent.Hand hand) { return reach.source(actor, hand); }
            public String unavailable(TacticalActor actor, TacticalIntent intent, CombatEngine.StateView state) {
                return reach.unavailable(actor, intent, state);
            }
            public boolean canExecute(TacticalActor actor, TacticalIntent intent, Vec3 feet) { return reach.canExecute(actor, intent, feet); }
            public MeleeEffects meleeEffects(TacticalActor actor, boolean configuredKnockback) {
                return new MeleeEffects(2, false, false, false);
            }
            public void start(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution) {
                actions.service.attackPlan(actor, execution.snapshot().target(), UUID.randomUUID(), execution.snapshot().operationId(),
                    result -> actions.finish(actor.body(), execution, result.outcome(), result.reason()));
            }
            public void tick(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution) {
                throw new IllegalStateException("synchronous test ability already terminal");
            }
        });
    }
    public static void verify(GameTestHelper helper, ServerPlayer player, CombatEngine.StateView state) {
        verifyBudget(helper);
        var actor = new TacticalActor(player);
        int slot = player.getInventory().getSelectedSlot();
        var position = player.position();
        var bindings = TacticalCapabilities.discover(actor, state, null);
        var punch = bindings.stream().filter(o -> o.binding().id().equals("dndturn:intrinsic_melee")).findFirst().orElseThrow();
        var other = bindings.stream().filter(o -> o.binding().id().equals("test:natural_no_push")).findFirst().orElseThrow();
        verifyTargetSelection(helper, punch.binding());
        helper.assertTrue(punch.binding().source().equals(other.binding().source())
            && punch.binding().source().item() == null && punch.binding().source().hand() == null,
            "natural abilities did not retain independent no-item bindings");
        var definitions = TacticalCapabilities.all();
        var a = definitions.stream().filter(b -> b.id().equals(punch.binding().id())).findFirst().orElseThrow().meleeEffects(actor, false);
        var b = definitions.stream().filter(c -> c.id().equals(other.binding().id())).findFirst().orElseThrow().meleeEffects(actor, true);
        helper.assertTrue(a.knockback() && !b.knockback() && !a.heldItemHooks() && !b.heldItemHooks(),
            "source kind overrode the registered melee effect policy");
        helper.assertTrue(player.getInventory().getSelectedSlot() == slot && player.position().equals(position),
            "actor discovery changed equipment or position");
        var status = TacticalCapabilities.all().stream().filter(c -> c.id().equals("test:status_attack")).findFirst().orElseThrow();
        var target = new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY, state.region().dimension(),
            UUID.randomUUID(), null, -1, 0, 0, 0);
        try {
            statusGrant = AbilitySource.status("test:grant", 1, actor.id(), actor.instance(), UUID.randomUUID());
            var intent = new TacticalIntent(status.id(), status.version(), status.cost(), target, statusGrant);
            status.validate(actor, intent, state);
            statusGrant = null;
            expectRevoked(helper, status, actor, intent, state);
            statusGrant = AbilitySource.status("test:grant", 1, actor.id(), actor.instance(), UUID.randomUUID());
            expectRevoked(helper, status, actor, intent, state);
            statusGrant = AbilitySource.status("test:grant", 1, actor.id(), UUID.randomUUID(), intent.source().grant());
            expectRevoked(helper, status, actor, intent, state);
        } finally { statusGrant = null; }
    }
    private static void verifyTargetSelection(GameTestHelper helper, TacticalCapabilities.Binding binding) {
        var usable = new TacticalCapabilities.Discovered(binding, TacticalCapabilities.Availability.APPROACH_REQUIRED, ActionFailure.Details.NONE);
        var blocked = new TacticalCapabilities.Discovered(binding, TacticalCapabilities.Availability.RULE_BLOCKED, ActionFailure.Details.REJECTED);
        var neutral = new MobTurnStrategies.PerceivedTarget(UUID.randomUUID(), true, false, false, true, true, 9, java.util.List.of(usable));
        var immune = new MobTurnStrategies.PerceivedTarget(UUID.randomUUID(), true, false, true, true, false, 1, java.util.List.of(usable));
        var hidden = new MobTurnStrategies.PerceivedTarget(UUID.randomUUID(), false, false, true, true, true, 1, java.util.List.of(usable));
        var unsupported = new MobTurnStrategies.PerceivedTarget(UUID.randomUUID(), true, false, true, true, true, 2, java.util.List.of(blocked));
        helper.assertTrue(MobTurnStrategies.ZombieAttack.selectTarget(java.util.List.of(immune, hidden, unsupported, neutral)) == neutral,
            "ineligible nearer target hid a visible neutral acquisition candidate");
        var committed = new MobTurnStrategies.PerceivedTarget(UUID.randomUUID(), false, true, true, true, true, 16, java.util.List.of(usable));
        helper.assertTrue(MobTurnStrategies.ZombieAttack.selectTarget(java.util.List.of(neutral, committed)) == committed,
            "decision lost the existing target preference");
        helper.assertTrue(MobTurnStrategies.ZombieAttack.selectTarget(java.util.List.of(immune, hidden, unsupported)) == null,
            "decision acquired an imperceptible, unattackable or unavailable target");
    }
    private static void verifyBudget(GameTestHelper helper) {
        var budget=new AbilityWorkBudget();UUID session=UUID.randomUUID(),actor=UUID.randomUUID();
        for(int i=0;i<16;i++)helper.assertTrue(budget.take(1,actor,session,AbilityWorkBudget.Work.QUERY),"actor query budget too small");
        helper.assertTrue(!budget.take(1,actor,session,AbilityWorkBudget.Work.QUERY),"actor query cap absent");
        for(int a=0;a<3;a++) {
            UUID next=UUID.randomUUID();
            for(int i=0;i<16;i++)helper.assertTrue(budget.take(1,next,session,AbilityWorkBudget.Work.QUERY),"session budget prematurely exhausted");
        }
        helper.assertTrue(!budget.take(1,UUID.randomUUID(),session,AbilityWorkBudget.Work.QUERY),"session cap absent");
        for(int n=0;n<12;n++) {
            UUID otherSession=UUID.randomUUID(),otherActor=UUID.randomUUID();
            for(int i=0;i<16;i++)helper.assertTrue(budget.take(1,otherActor,otherSession,AbilityWorkBudget.Work.QUERY),"global budget prematurely exhausted");
        }
        helper.assertTrue(!budget.take(1,UUID.randomUUID(),UUID.randomUUID(),AbilityWorkBudget.Work.QUERY),"global cap absent");
        try {budget.require(1,actor,session,AbilityWorkBudget.Work.QUERY);throw new AssertionError("missing deferred result");}
        catch(ActionFailure expected){helper.assertTrue(expected.details().code()==ActionFailure.Code.EVALUATION_DEFERRED,"budget forged gameplay rejection");}
        helper.assertTrue(budget.take(2,actor,session,AbilityWorkBudget.Work.QUERY),"new tick did not restore computation budget");
        helper.assertTrue(budget.counters().get(AbilityWorkBudget.Work.QUERY).consumed()==257,"work accounting mismatch");
    }
    private static void expectRevoked(GameTestHelper helper, TacticalBehavior behavior, TacticalActor actor,
                                      TacticalIntent intent, CombatEngine.StateView state) {
        boolean rejected = false;
        try { behavior.validate(actor, intent, state); } catch (ActionFailure expected) { rejected = true; }
        helper.assertTrue(rejected, "revoked/replaced status grant was accepted");
    }
}
