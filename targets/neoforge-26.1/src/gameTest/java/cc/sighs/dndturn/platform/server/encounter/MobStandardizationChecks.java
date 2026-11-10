package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.compat.ArcheryExtension;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.ai.AiPlanner;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.gametest.TestPlayers;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.action.ActionTestAccess;
import cc.sighs.dndturn.platform.server.action.EquippedRanged;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.ai.AiTestAccess;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** Real native launch through shared discovery/AI/PLAN and the normal environment projectile owner. */
public final class MobStandardizationChecks {
    public static void ghast(GameTestHelper h) { ghast(h, "hit"); }
    public static void ghastMiss(GameTestHelper h) { ghast(h, "miss"); }
    public static void ghastDeath(GameTestHelper h) { ghast(h, "death"); }
    public static void ghastAbsorption(GameTestHelper h) { ghast(h, "absorption"); }
    public static void ghastShield(GameTestHelper h) { ghast(h, "shield"); }
    public static void ghastCancel(GameTestHelper h) { ghast(h, "cancel"); }
    private static void ghast(GameTestHelper h, String mode) {
        var level = h.getLevel(); var base = new BlockPos(220001 + List.of("hit","miss","death","absorption","cancel","shield").indexOf(mode)*1000, 185, 1);
        var forced = new HashSet<Long>();
        for (int x = (base.getX()-24)>>4; x <= (base.getX()+24)>>4; x++) for (int z=-2; z<=2; z++) {
            level.getChunk(x,z); if (level.setChunkForced(x,z,true)) forced.add(ChunkPos.pack(x,z));
        }
        level.waitForEntities(ChunkPos.containing(base),1);
        var player = TestPlayers.survival(h); player.setPos(base.getX()+.5, base.getY(), .5); player.connection.resetPosition();
        var ghast = EntityType.GHAST.create(level, EntitySpawnReason.COMMAND);
        ghast.setPos(player.getX()+12, player.getY(), player.getZ()); ghast.setNoAi(true); ghast.setNoGravity(true); level.addFreshEntity(ghast);
        var boundary = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
        boundary.setPos(player.getX()+6, player.getY(), player.getZ()); boundary.setNoAi(true); boundary.setNoGravity(true); level.addFreshEntity(boundary);
        var corner = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
        corner.setPos(player.getX()+5, player.getY(), player.getZ()+5); corner.setNoAi(true); corner.setNoGravity(true); level.addFreshEntity(corner);
        player.setNoGravity(true);
        var service = ServerRuntime.encounters(level.getServer());
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> cancel = event -> {
            if (mode.equals("cancel") && event.getEntity() == boundary) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
        Runnable cleanup = () -> {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
            var id = service.encounterOf(player.getUUID()); if (id != null) service.lifecycle().stop(id);
            ghast.discard(); boundary.discard(); corner.discard(); level.getServer().getPlayerList().remove(player); player.discard();
            for (long key : forced) { var chunk=ChunkPos.unpack(key); level.setChunkForced(chunk.x(),chunk.z(),false); } forced.clear();
        };
        h.runAtTickTime(299, cleanup);
        h.startSequence().thenIdle(20).thenExecute(() -> {
            ghast.setTarget(player); service.lifecycle().requestStart(player, UUID.randomUUID());
        }).thenWaitUntil(() -> {
            var id = service.encounterOf(ghast.getUUID()); h.assertTrue(id != null, "Ghast not discovered");
            var state = service.state(id);
            if (player.getUUID().equals(state.current())) service.combat().endTurn(id,player,UUID.randomUUID(),state.version());
            h.assertTrue(ghast.getUUID().equals(service.state(id).current()), "waiting for Ghast turn");
        }).thenExecute(() -> {
            var id = service.encounterOf(ghast.getUUID());
            service.useAttackRandomForGameTest(id, new Random(1) { int calls; @Override public int nextInt(int bound) { return mode.equals("miss") && calls++ == 0 ? 0 : 14; } });
            var actor = service.actorStates().context(ghast); var state = service.state(id);
            var target = new ActionIntent.Target(ActionIntent.TargetKind.ENTITY, level.dimension().identifier().toString(), player.getUUID(), null, -1, 0, 0, 0);
            var option = cc.sighs.dndturn.platform.server.ability.AbilityQueries.forActor(actor).discover(actor,state,target).stream()
                .filter(o -> o.binding().id().equals("dndturn:ghast_fireball")).findFirst().orElseThrow();
            var operation = UUID.randomUUID(); var intent = option.binding().invocation(target);
            if (mode.equals("death")) player.setHealth(1);
            if (mode.equals("absorption")) { player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(20); player.setAbsorptionAmount(20); }
            if (mode.equals("shield")) {
                var world = io.github.fatalitaya.combatnexus.combat.api.CombatAccess.find((io.github.fatalitaya.combatnexus.combat.api.WorldLookup)level).orElseThrow();
                var source = new io.github.fatalitaya.combatnexus.combat.api.CombatSource(io.github.fatalitaya.combatnexus.api.id.NexusId.parse("dndturn:fixture"),Optional.empty(),Optional.empty());
                var definition = new io.github.fatalitaya.combatnexus.combat.shield.api.ShieldDefinition(20,0,Set.of(),Set.of(),Set.of(),0,0,
                    io.github.fatalitaya.combatnexus.combat.shield.api.ShieldDefinition.Stacking.REPLACE_SAME_SOURCE);
                h.assertTrue(world.shields().grant(UUID.randomUUID(),player.getUUID(),new io.github.fatalitaya.combatnexus.combat.shield.api.ShieldGrant(source,definition,20,OptionalLong.empty())).applied(),"shield grant rejected");
            }
            float before = player.getHealth();
            service.actions().submit(actor,service.generation(),id,operation,state.version(),intent,false);
            var checkpoint = service.abilityCheckpoint(operation);
            h.assertTrue(checkpoint != null && checkpoint.damageSteps().size() == (mode.equals("death") ? 2 : 3), "Ghast did not retain independent steps: " + checkpoint);
            var expected = mode.equals("cancel") ? OperationRecord.Outcome.REJECTED : OperationRecord.Outcome.COMPLETED;
            h.assertTrue(checkpoint.damageSteps().stream().allMatch(r -> r.outcome()==(r.target().equals(boundary.getUUID()) ? expected : OperationRecord.Outcome.COMPLETED)), "Ghast application failed: " + checkpoint.damageSteps());
            float expectedHealth = mode.equals("death") ? 0 : (mode.equals("absorption") || mode.equals("shield")) ? before : before-(mode.equals("miss") ? 5 : 11);
            float boundaryHealth = mode.equals("cancel") ? 10 : 5;
            h.assertTrue(player.getHealth()==expectedHealth && boundary.getHealth()==boundaryHealth && corner.getHealth()==10,
                "two-stage/spherical application differs: player="+player.getHealth()+" boundary="+boundary.getHealth()+" corner="+corner.getHealth());
            if (mode.equals("absorption")) h.assertTrue(player.getAbsorptionAmount()==9
                && checkpoint.damageSteps().stream().mapToDouble(r -> r.absorptionLoss()).sum()==11,"absorption receipt differs");
            if (mode.equals("shield")) h.assertTrue(checkpoint.damageSteps().stream().mapToDouble(r -> r.shieldLoss()).sum()==11,"full-shield absorption not retained");
            h.assertTrue(checkpoint.damageSteps().stream().anyMatch(r -> r.channel().equals("combatnexus:fire"))
                && checkpoint.damageSteps().stream().filter(r -> r.channel().equals("dndturn:force")).count()==(mode.equals("death") ? 1 : 2),"typed channels missing");
            if (!mode.equals("death")) {
                float after = player.getHealth();
                service.actions().submit(actor,service.generation(),id,operation,state.version(),intent,false);
                h.assertTrue(player.getHealth()==after && boundary.getHealth()==boundaryHealth,"retry replayed numerical damage");
                service.persistIfChanged();
                var saved = level.getServer().getDataStorage().computeIfAbsent(cc.sighs.dndturn.platform.server.persistence.CombatSavedData.TYPE).envelope();
                h.assertTrue(saved.abilities().get(operation).damageSteps().equals(checkpoint.damageSteps()), "saved application evidence changed");
                h.assertTrue(saved.encounterContent().get(id).content().equals(
                    cc.sighs.dndturn.platform.server.character.EncounterContentCache.server().find(id).content()), "typed catalog roundtrip changed definitions");
                cc.sighs.dndturn.platform.server.persistence.CombatRecoveryCandidate.validate(saved, new Random(0));
                var missingContent = new cc.sighs.dndturn.platform.server.persistence.CombatPersistenceEnvelope(
                    saved.rules(), saved.cumulativeServerTicks(), saved.nextSessionSequence(), saved.sessionSequences(),
                    saved.capturedSettings(), saved.projectileOrigins(), saved.projectileDomains(), saved.pendingProjectileAttacks(),
                    saved.pendingMerges(), saved.startReceipts(), saved.exitReceipts(), saved.leases(), saved.quarantinedProjectiles(),
                    saved.exitAuthorizations(), saved.abilities(), saved.mergeBindings(), saved.projectileAbilities(),
                    saved.recoveryAudits(), saved.participantEffects(), saved.turnSettlements(), saved.processes(), Map.of());
                boolean missingRejected = false;
                try { cc.sighs.dndturn.platform.server.persistence.CombatRecoveryCandidate.validate(missingContent, new Random(0)); }
                catch (IllegalArgumentException expectedMissing) { missingRejected = expectedMissing.getMessage().contains("content binding"); }
                h.assertTrue(missingRejected, "recovery accepted missing content and could recapture latest definitions");
            }
        }).thenExecute(cleanup).thenExecute(() -> {
            if (mode.equals("hit")) contentDamage(h, false); else h.succeed();
        });
    }
    // The existing numerical seam now also protects generic typed damage, AI selection and retry evidence.
    private static void contentDamage(GameTestHelper h, boolean area) {
        var level = h.getLevel(); var base = new BlockPos(area ? 227001 : 226001, 185, 1);
        var forced = new HashSet<Long>();
        for (int x = (base.getX()-24)>>4; x <= (base.getX()+24)>>4; x++) for (int z=-2; z<=2; z++) {
            level.getChunk(x,z); if (level.setChunkForced(x,z,true)) forced.add(ChunkPos.pack(x,z));
        }
        level.waitForEntities(ChunkPos.containing(base),1);
        var player = TestPlayers.survival(h);
        player.setPos(base.getX()+.5, base.getY(), .5); player.connection.resetPosition(); player.setNoGravity(true);
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        player.setHealth(200);
        Mob mob = area ? EntityType.WARDEN.create(level, EntitySpawnReason.COMMAND) : EntityType.BLAZE.create(level, EntitySpawnReason.COMMAND);
        mob.setPos(player.getX()+(area ? 10 : 2), player.getY(), player.getZ());
        mob.setNoAi(true); mob.setNoGravity(true); level.addFreshEntity(mob);
        var boundary = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
        boundary.setPos(player.getX()+1, player.getY(), player.getZ()); boundary.setNoAi(true); boundary.setNoGravity(true); level.addFreshEntity(boundary);
        var corner = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
        corner.setPos(player.getX()+1, player.getY(), player.getZ()+1); corner.setNoAi(true); corner.setNoGravity(true); level.addFreshEntity(corner);
        var service = ServerRuntime.encounters(level.getServer());
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> cancel = e -> {
            if (e.getEntity() == boundary) e.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
        Runnable cleanup = () -> {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
            var id = service.encounterOf(player.getUUID()); if (id != null) service.lifecycle().stop(id);
            mob.discard(); boundary.discard(); corner.discard(); level.getServer().getPlayerList().remove(player); player.discard();
            for (long key : forced) { var chunk=ChunkPos.unpack(key); level.setChunkForced(chunk.x(),chunk.z(),false); } forced.clear();
        };
        h.runAtTickTime(298, cleanup);
        h.startSequence().thenIdle(20).thenExecute(() -> {
            if (mob instanceof net.minecraft.world.entity.monster.warden.Warden warden) warden.setAttackTarget(player);
            else mob.setTarget(player);
            service.lifecycle().requestStart(player, UUID.randomUUID());
        }).thenWaitUntil(() -> {
            var id = service.encounterOf(mob.getUUID()); h.assertTrue(id != null, "content mob not admitted");
            var state = service.state(id);
            if (player.getUUID().equals(state.current())) service.combat().endTurn(id,player,UUID.randomUUID(),state.version());
            h.assertTrue(mob.getUUID().equals(service.state(id).current()), "waiting for content turn");
        }).thenExecute(() -> {
            var id = service.encounterOf(mob.getUUID()); var state = service.state(id);
            service.useAttackRandomForGameTest(id, new Random(1) { @Override public int nextInt(int bound) { return Math.min(14, bound-1); } });
            var actor = service.actorStates().context(mob);
            var pinned = cc.sighs.dndturn.platform.server.character.EncounterContentCache.server().find(id);
            var species = pinned.contentFor(mob.getUUID()).requireMob(area ? "minecraft:warden" : "minecraft:blaze");
            int expectedSave = species.saveBonuses().getOrDefault(cc.sighs.dndturn.domain.character.Ability.CON,
                species.abilities().modifier(cc.sighs.dndturn.domain.character.Ability.CON));
            h.assertTrue(cc.sighs.dndturn.platform.server.character.SaveService.bonus(mob,
                cc.sighs.dndturn.domain.character.Ability.CON,pinned)==expectedSave,
                "generic provider identity erased captured species saving throws");
            var ai = AiTestAccess.capture(actor,state,null,ActionTestAccess.authority(service.actions()),service.mobDecisionFacts());
            var decision = AiPlanner.decide(ai);
            h.assertTrue(decision instanceof AiPlanner.Propose proposal && proposal.intent().behaviorId().equals("dndturn:content_action"),
                "content AI failed to propose captured action: " + decision);
            var intent = ((AiPlanner.Propose) decision).intent();
            h.assertTrue(intent.parameters().get("action").equals(area ? "1" : "0"), "AI chose wrong content action");
            if (!area) {
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(20);
                player.setAbsorptionAmount(20);
            }
            var operation = UUID.randomUUID();
            service.actions().submit(actor,service.generation(),id,operation,state.version(),intent,false);
            var checkpoint = service.abilityCheckpoint(operation);
            h.assertTrue(checkpoint != null && checkpoint.damageSteps().size() == 2,
                "content lost target/type steps: " + checkpoint);
            h.assertTrue(!service.state(id).members().get(mob.getUUID()).action(), "content damage did not charge one action");
            h.assertTrue(boundary.getHealth()==10 && corner.getHealth()==10, "area cancellation or spherical boundary failed");
            h.assertTrue(player.getHealth()==(area ? 180 : 200), "content health differs: " + checkpoint.damageSteps());
            if (area) h.assertTrue(checkpoint.damageSteps().stream().anyMatch(r -> r.target().equals(boundary.getUUID())
                    && r.outcome()==OperationRecord.Outcome.REJECTED), "cancelled area target reported success");
            else h.assertTrue(player.getAbsorptionAmount()==14 && checkpoint.damageSteps().stream().mapToDouble(r -> r.absorptionLoss()).sum()==6
                    && checkpoint.damageSteps().stream().map(r -> r.channel()).collect(java.util.stream.Collectors.toSet())
                        .equals(Set.of("dndturn:bludgeoning","combatnexus:fire")), "typed absorption receipt differs");
            float health = player.getHealth(), absorption = player.getAbsorptionAmount();
            service.actions().submit(actor,service.generation(),id,operation,state.version(),intent,false);
            h.assertTrue(player.getHealth()==health && player.getAbsorptionAmount()==absorption, "content retry repeated application");
            service.persistIfChanged();
            var saved = level.getServer().getDataStorage().computeIfAbsent(cc.sighs.dndturn.platform.server.persistence.CombatSavedData.TYPE).envelope();
            h.assertTrue(saved.abilities().get(operation).damageSteps().equals(checkpoint.damageSteps()), "content checkpoint lost typed evidence");
        }).thenExecute(cleanup).thenExecute(() -> { if (area) h.succeed(); else contentDamage(h, true); });
    }
    public static void run(GameTestHelper h) { run(h, false, false, false, false); }
    public static void held(GameTestHelper h) { run(h, true, false, false, false); }
    public static void external(GameTestHelper h) { run(h, false, true, false, false); }
    public static void miss(GameTestHelper h) { run(h, false, false, true, false); }
    public static void automatic(GameTestHelper h) { run(h, false, false, false, true); }
    private static void run(GameTestHelper h, boolean held, boolean external, boolean miss, boolean automatic) {
        var level = h.getLevel(); var base = new BlockPos(automatic ? 216001 : miss ? 215001 : external ? 212001 : held ? 211001 : 210001, 185, 1);
        var forced = new HashSet<Long>();
        for (int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++) for(int z=-2;z<=2;z++) {
            level.getChunk(x,z); if(level.setChunkForced(x,z,true)) forced.add(ChunkPos.pack(x,z));
        }
        level.waitForEntities(ChunkPos.containing(base),1);
        for(var pos:BlockPos.betweenClosed(base.offset(-7,-1,-7),base.offset(7,4,7)))
            level.setBlockAndUpdate(pos,pos.getY()<base.getY() || pos.getY()==base.getY()+4 ? Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        var player=TestPlayers.survival(h);
        player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        player.connection.resetPosition();
        Mob mob=external ? EntityType.COW.create(level,EntitySpawnReason.COMMAND) : EntityType.SKELETON.create(level,EntitySpawnReason.COMMAND);
        if(external) mob.addTag(ArcheryExtension.GROUP);
        mob.setPos(base.getX()+2.5,base.getY(),base.getZ()+.5); mob.setNoAi(true); mob.setPersistenceRequired();
        mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
        if(held) mob.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.ARROW,7));
        level.addFreshEntity(mob);
        var service=ServerRuntime.encounters(level.getServer());
        boolean mobTarget = !held && !external && !miss && !automatic;
        Mob opponent = mobTarget ? EntityType.HUSK.create(level, EntitySpawnReason.COMMAND) : null;
        if (opponent != null) {
            opponent.setPos(base.getX() + .5, base.getY(), base.getZ() + 2.5);
            opponent.setNoAi(true); opponent.setPersistenceRequired(); level.addFreshEntity(opponent);
            mob.setTarget(opponent);
            h.assertTrue(service.encounterOf(mob.getUUID()) == null && service.encounterOf(opponent.getUUID()) == null,
                "ordinary world Mob aggression created an encounter");
            mob.setTarget(null);
        }
        LivingEntity selectedTarget = opponent == null ? player : opponent;
        Runnable cleanup=()->{
            UUID id=service.encounterOf(player.getUUID()); if(id!=null) service.lifecycle().stop(id);
            mob.discard(); player.discard(); if (opponent != null) opponent.discard();
            for(long key:forced){var c=ChunkPos.unpack(key);level.setChunkForced(c.x(),c.z(),false);} forced.clear();
        };
        h.runAtTickTime(299,cleanup);
        UUID[] operation={null}, encounter={null};
        h.startSequence().thenIdle(20).thenExecute(()->{
            // The field admits a mob with an actual directed lock; presence alone is not enough.
            mob.setTarget(player);
            service.lifecycle().requestStart(player,UUID.randomUUID());
        }).thenWaitUntil(()->{
            UUID id=service.encounterOf(mob.getUUID());h.assertTrue(id!=null,"Skeleton not discovered");
            var state=service.state(id);
            if(player.getUUID().equals(state.current())) service.combat().endTurn(id,player,UUID.randomUUID(),state.version());
            state=service.state(id);
            h.assertTrue(mob.getUUID().equals(state.current()) && state.members().get(mob.getUUID()).action(),"waiting for Skeleton turn");
        }).thenExecute(()->{
            var actor=service.actorStates().context(mob);var id=service.encounterOf(actor.id()); encounter[0]=id;
            if (opponent != null) {
                java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent> cancel = event -> {
                    if (event.getEntity() == mob && event.getNewAboutToBeSetTarget() == opponent) event.setCanceled(true);
                };
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
                try {
                    mob.setTarget(opponent);
                    h.assertTrue(mob.getTarget() == player && service.encounterOf(opponent.getUUID()) == null,
                        "canceled native target admitted a neutral mob");
                } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel); }
                mob.setTarget(opponent);
                h.assertTrue(id.equals(service.encounterOf(opponent.getUUID())), "committed Mob target did not join");
                long relationVersion = service.state(id).version();
                mob.setTarget(opponent);
                h.assertTrue(service.state(id).version() == relationVersion, "same native target changed rule revision");
                service.useAttackRandomForGameTest(id, new Random(1) { @Override public int nextInt(int bound) { return 14; } });
            }
            var state=service.state(id);var target=new ActionIntent.Target(ActionIntent.TargetKind.ENTITY,
                    level.dimension().identifier().toString(),selectedTarget.getUUID(),null,-1,0,0,0);
            var before=service.actorStates().state(actor.id());
            h.assertTrue(before.runtime().effects().values().stream().anyMatch(e->e.definition().equals(external
                    ? ArcheryExtension.EFFECT : AbilityAdapterRegistry.effects().require("dndturn:native_archery", 1))),"group effect not installed");
            if(automatic) { mob.setNoAi(false); return; }
            var found=cc.sighs.dndturn.platform.server.ability.AbilityQueries.forActor(actor).discover(actor,state,target).stream().filter(o->o.binding().id().equals(EquippedRanged.ID)).findFirst().orElseThrow();
            h.assertTrue(found.availability()==cc.sighs.dndturn.platform.server.ability.AbilityQueries.Availability.EXECUTABLE,"bow unavailable: "+found);
            h.assertTrue(before.equals(service.actorStates().state(actor.id())),"discovery mutated effect state");
            mob.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.TIPPED_ARROW));
            var unsupported=cc.sighs.dndturn.platform.server.ability.AbilityQueries.forActor(actor).discover(actor,state,target).stream().filter(o->o.binding().id().equals(EquippedRanged.ID)).findFirst().orElseThrow();
            h.assertTrue(unsupported.availability()==cc.sighs.dndturn.platform.server.ability.AbilityQueries.Availability.RULE_BLOCKED,"special ammunition silently authorized");
            mob.setItemSlot(EquipmentSlot.OFFHAND,held?new ItemStack(Items.ARROW,7):ItemStack.EMPTY);
            found=cc.sighs.dndturn.platform.server.ability.AbilityQueries.forActor(actor).discover(actor,state,target).stream().filter(o->o.binding().id().equals(EquippedRanged.ID)).findFirst().orElseThrow();
            var intent=found.binding().invocation(target);
            h.assertTrue(intent.source().kind()==GrantEvidence.Kind.EQUIPMENT && intent.source().grant()!=null,"bow lost equipment/effect evidence");
            h.assertTrue(intent.source().dependencies().get("ammunition").startsWith(held?"OFF_HAND:":"native:"),"incorrect ammunition source");
            var ai=AiTestAccess.capture(actor,state,null,ActionTestAccess.authority(service.actions()), service.mobDecisionFacts());
            h.assertTrue(AiPlanner.decide(ai) instanceof AiPlanner.Propose p && p.intent().behaviorId().equals(EquippedRanged.ID)
                    && p.intent().target().entity().equals(selectedTarget.getUUID()),"shared planner did not choose the committed ranged target: " + AiPlanner.decide(ai) + " preference=" + ai.definition().targetPreference());
            // Source replacement is rejected before any action or native side effect.
            mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STICK));
            try {ActionTestAccess.validate(service.actions(), mob,intent,state);throw new AssertionError("stale bow accepted");}
            catch(IllegalStateException expected) {}
            h.assertTrue(service.state(id).members().get(actor.id()).action(),"source rejection consumed action");
            mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
            try {ActionTestAccess.validate(service.actions(), mob,intent,state);throw new AssertionError("equal replacement reused old equipment generation");}
            catch(IllegalStateException expected) {}
            intent=cc.sighs.dndturn.platform.server.ability.AbilityQueries.forActor(actor).discover(actor,state,target).stream().filter(o->o.binding().id().equals(EquippedRanged.ID))
                    .findFirst().orElseThrow().binding().invocation(target);
            operation[0]=UUID.randomUUID();
            if(miss) service.useAttackRandomForGameTest(id,new Random(1){@Override public int nextInt(int bound){return 0;}});
            service.actions().submit(actor,service.generation(),id,operation[0],state.version(),intent,false);
            var results=service.lifecycle().results(id,0,128).results();
            var result=results.stream().filter(r->r.snapshot().operationId().equals(operation[0])).findFirst().orElseThrow();
            h.assertTrue(result.outcome()==OperationRecord.Outcome.COMPLETED,"native launch failed: "+results);
            var checkpoint=service.abilityCheckpoint(operation[0]);
            h.assertTrue(checkpoint.invocation().source().equals(intent.source()) && checkpoint.observed().spawned().size()==1,
                    "checkpoint lost policy/source/native spawn evidence");
            h.assertTrue(checkpoint.before().items().equals(checkpoint.observed().items()),"zero native item consumption not observed");
            h.assertTrue(!service.state(id).members().get(actor.id()).action(),"launch did not spend action");
            h.assertTrue(mob.getMainHandItem().getDamageValue()==0 && (!held || mob.getOffhandItem().getCount()==7),"native effect consumed equipment");
            var arrows=level.getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,mob.getBoundingBox().inflate(8));
            h.assertTrue(arrows.size()==1,"native launch did not create one arrow");
            int count=results.size();
            service.actions().submit(actor,service.generation(),id,operation[0],state.version(),intent,false);
            h.assertTrue(service.lifecycle().results(id,0,128).results().size()==count,"duplicate launch repeated");
            var effects=service.actorStates().state(actor.id());
            service.actorStates().command(actor,new ActorStates.Command(UUID.randomUUID(),actor.id(),effects.revision(),
                    List.of(new ActorStates.Remove(intent.source().grant()))));
            h.assertTrue(AbilityAdapterRegistry.facts(EquippedRanged.ID).checkedSources(actor).isEmpty(),"revoked effect still grants bow");
            service.combat().endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
        }).thenWaitUntil(()->{
            var id=encounter[0];var state=service.state(id);
            if(automatic && operation[0]==null) {
                var root=service.lifecycle().results(id,0,128).results().stream().filter(r->r.snapshot().intent()!=null
                        && r.snapshot().intent().behaviorId().equals(EquippedRanged.ID) && r.terminal()).findFirst();
                h.assertTrue(root.isPresent(),"waiting for automatic registered AI shot");
                h.assertTrue(root.get().outcome()==OperationRecord.Outcome.COMPLETED,"automatic AI shot failed: "+root);
                operation[0]=root.get().snapshot().operationId();
                h.assertTrue(!state.members().get(mob.getUUID()).action(),"automatic shot did not consume action");
            }
            var projectile=service.lifecycle().results(id,0,128).results().stream().filter(r->r.damageTrace()!=null
                    && r.snapshot().source()!=null && !r.snapshot().source().equals(mob.getUUID())).findFirst();
            if(projectile.isEmpty() && state.current()!=null && state.phase()!=EncounterPhase.ENVIRONMENT
                    && (!automatic || player.getUUID().equals(state.current())))
                service.combat().endCurrentTurn(id,UUID.randomUUID(),state.version());
            h.assertTrue(projectile.isPresent(),"waiting for native arrow contact and tactical result");
            h.assertTrue(projectile.get().snapshot().target().equals(selectedTarget.getUUID()), "projectile retargeted");
            if (opponent != null) h.assertTrue(projectile.get().actualDamage() > 0 && opponent.getHealth() < opponent.getMaxHealth(),
                "Mob arrow did not cause observed damage");
            if(miss) h.assertTrue(!projectile.get().damageTrace().hit() && projectile.get().actualDamage()==0,"natural one did not preserve miss");
            System.out.println("MOB_STANDARDIZATION held="+held+" outcome="+projectile.get().outcome()+" trace="+projectile.get().damageTrace());
        }).thenWaitUntil(() -> {
            if (opponent == null) return;
            var id = encounter[0]; var state = service.state(id);
            if (!opponent.getUUID().equals(state.current()) && state.phase() != EncounterPhase.ENVIRONMENT)
                service.combat().endCurrentTurn(id, UUID.randomUUID(), state.version());
            h.assertTrue(opponent.getUUID().equals(service.state(id).current()), "waiting for Mob retaliation turn");
        }).thenExecute(() -> {
            if (opponent == null) return;
            var id = encounter[0];
            opponent.setPos(mob.getX() - 1, mob.getY(), mob.getZ());
            opponent.setTarget(mob);
            var actor = service.actorStates().context(opponent);
            var target = new ActionIntent.Target(ActionIntent.TargetKind.ENTITY, level.dimension().identifier().toString(),
                mob.getUUID(), null, -1, 0, 0, 0);
            var state = service.state(id);
            var option = cc.sighs.dndturn.platform.server.ability.AbilityQueries.forActor(actor).discover(actor, state, target).stream()
                .filter(o -> o.binding().id().equals("dndturn:intrinsic_melee")).findFirst().orElseThrow();
            h.assertTrue(option.availability() == cc.sighs.dndturn.platform.server.ability.AbilityQueries.Availability.EXECUTABLE,
                "Mob melee retaliation unavailable: " + option);
            var intent = option.binding().invocation(target); UUID operationId = UUID.randomUUID();
            float health = mob.getHealth();
            service.actions().submit(actor, service.generation(), id, operationId, state.version(), intent, false);
            h.assertTrue(mob.getHealth() < health && !service.state(id).members().get(opponent.getUUID()).action(),
                "Mob melee failed to apply damage and consume one action");
            float after = mob.getHealth();
            service.actions().submit(actor, service.generation(), id, operationId, state.version(), intent, false);
            h.assertTrue(mob.getHealth() == after, "duplicate Mob melee applied damage twice");
        }).thenExecute(()->{
            service.lifecycle().stop(encounter[0]);
            h.assertTrue(service.actorStates().state(mob.getUUID()).runtime().effects().values().stream()
                    .noneMatch(e->e.definition().equals(external?ArcheryExtension.EFFECT:AbilityAdapterRegistry.effects().require("dndturn:native_archery", 1))),
                    "encounter exit retained group-owned archery effect");
            cleanup.run();
        }).thenSucceed();
    }
}
