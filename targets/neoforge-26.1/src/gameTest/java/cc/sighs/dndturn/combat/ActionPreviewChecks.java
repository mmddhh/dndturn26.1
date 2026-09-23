package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class ActionPreviewChecks {
    public static void run(GameTestHelper h) {
        var level = h.getLevel(); var anchor = h.absolutePos(new BlockPos(94001, 151, 1));
        // Keep both target positions in one entity section; forced chunk loading does not
        // synchronously install a newly entered section's entity lookup during this callback.
        var base = new BlockPos((anchor.getX() >> 4) * 16 + 1, anchor.getY(), (anchor.getZ() >> 4) * 16 + 1);
        var priorDifficulty = level.getDifficulty();
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        Set<Long> forced = new HashSet<>();
        for (int x = (base.getX()-24)>>4; x <= (base.getX()+24)>>4; x++) for (int z = (base.getZ()-24)>>4; z <= (base.getZ()+24)>>4; z++) {
            level.getChunk(x,z); if (level.setChunkForced(x,z,true)) forced.add(net.minecraft.world.level.ChunkPos.pack(x,z));
        }
        for (var p : BlockPos.betweenClosed(base.offset(-8,-1,-8), base.offset(8,5,8)))
            level.setBlockAndUpdate(p, p.getY()<base.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        var player = cc.sighs.dndturn.gametest.TestPlayers.survival(h);
        player.setPos(base.getX()+.5, base.getY(), base.getZ()+.5); player.setOnGround(true);
        checkFlatRoutes(h, player, base);
        var mob = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        mob.setPos(base.getX()+5.5, base.getY(), base.getZ()+.5); mob.setNoAi(true); mob.setPersistenceRequired();
        mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        h.assertTrue(level.addFreshEntity(mob), "preview fixture spawn failed");
        var service = ServerCombatService.forServer(level.getServer());
        Runnable cleanup = () -> {
            var id = service.encounterOf(player.getUUID()); if (id != null) service.stop(id); mob.discard();
            for (long k : forced) { var c = net.minecraft.world.level.ChunkPos.unpack(k); level.setChunkForced(c.x(),c.z(),false); } forced.clear();
            level.getServer().setDifficulty(priorDifficulty, true);
        };
        h.runAtTickTime(299, cleanup);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(level.getEntity(mob.getUUID()) == mob
            && level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(8)).contains(mob),
            "waiting for preview fixture entity registration: found=" + (level.getEntity(mob.getUUID()) == mob)
                + " alive=" + mob.isAlive() + " mob=" + mob.position() + " player=" + player.position())).thenExecute(() -> { try {
            service.requestStart(player, UUID.randomUUID()); var id = service.encounterOf(player.getUUID());
            for (int i=0;i<service.state(id).members().size() && !player.getUUID().equals(service.state(id).current());i++)
                service.endCurrentTurn(id, UUID.randomUUID(), service.state(id).version());
            var target = new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY, level.dimension().identifier().toString(), mob.getUUID(), null, -1,0,0,0);
            var before = service.state(id); var posBefore = player.position();
            var moveFinder=new PreviewPathfinder(player,before.region(),()->{});
            var longMove=moveFinder.directTo(player.position().add(-7,0,0));
            h.assertTrue(longMove!=null,"budget preview fixture path unavailable");
            var limited=MovementPreviewBudget.limit(player.position(),longMove,20,
                player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
            h.assertTrue(limited.exceeded() && !limited.route().isEmpty(),"over-budget move was not truncated");
            var moveTarget=new TacticalIntent.Target(TacticalIntent.TargetKind.GROUND,level.dimension().identifier().toString(),
                null,PreviewPathfinder.key(limited.stop()),-1,0,0,0);
            var moveIntent=new TacticalIntent("dndturn:move",1,TacticalIntent.Capability.MOVE,moveTarget,AbilitySource.basic())
                .withApproach(new TacticalIntent.Approach(UUID.randomUUID(),0,PreviewPathfinder.value(limited.stop())));
            var acceptedMove=service.tacticalActions().selectedPreviewPath(player,moveIntent,before);
            h.assertTrue(acceptedMove.getLast().feet().equals(moveIntent.approach().feet()),"server changed gray endpoint");
            h.assertTrue(moveFinder.revalidate(acceptedMove),"budget endpoint bypassed path validation");
            var feet=new Vec3(base.getX()+4.15,base.getY(),base.getZ()+.65);
            var ability=TacticalCapabilities.all().stream().filter(c -> c.id().equals("dndturn:intrinsic_melee")).findFirst().orElseThrow();
            var selectedIntent=new TacticalIntent(ability.id(),ability.version(),ability.cost(),target,ability.source(new TacticalActor(player),TacticalIntent.Hand.MAIN_HAND))
                .withApproach(new TacticalIntent.Approach(UUID.randomUUID(),0,PreviewPathfinder.value(feet)));
            var selectedRoute=service.tacticalActions().selectedPreviewPath(player,selectedIntent,service.state(id));
            h.assertTrue(!selectedRoute.isEmpty() && selectedRoute.getLast().feet().equals(PreviewPathfinder.value(feet)), "mouse endpoint snapped to grid centre");
            h.assertTrue(player.position().equals(posBefore) && service.state(id).version()==before.version()
                && service.state(id).members().get(player.getUUID()).equals(before.members().get(player.getUUID())), "read-only planning mutated actor or resources");
            int[] probes={0};
            var planner = new PreviewPathfinder(player, service.state(id).region(), () -> probes[0]++);
            planner.destination(feet);
            int slices=0; while (!planner.advance(1) && slices++<1024) {}
            h.assertTrue(planner.finishAt(feet)!=null && slices<12 && probes[0]<1000,"flat A* expanded an excessive area: "+slices+" / "+probes[0]);
            System.out.println("action_preview flat A*: slices="+slices+", charged probes="+probes[0]);
            var empty=new PreviewPathfinder(player,service.state(id).region(),()->{throw new AssertionError("empty goals performed world work");});
            empty.begin(Set.of()); h.assertTrue(empty.advance(16) && empty.result()==null,"empty goal search did not finish immediately");
            var sameCell=new PreviewPathfinder(player,service.state(id).region(),()->{});
            var exact=player.position().add(.2,0,.1); sameCell.destination(exact); while (!sameCell.advance(1)) {}
            h.assertTrue(sameCell.finishAt(exact).getLast().feet().equals(PreviewPathfinder.value(exact)),"same-cell mouse movement vanished");
            var buffer=io.netty.buffer.Unpooled.buffer();
            try {
                var wire=new TacticalNetwork.Request(service.generation(),id,UUID.randomUUID(),before.version(),selectedIntent,false);
                TacticalNetwork.Request.CODEC.encode(buffer,wire);
                h.assertTrue(wire.equals(TacticalNetwork.Request.CODEC.decode(buffer)),"submission lost fractional position or target");
            } finally { buffer.release(); }
            var slab=base.offset(0,0,2); level.setBlockAndUpdate(slab,Blocks.STONE_SLAB.defaultBlockState());
            var slabProbe=new PreviewPathfinder(player,service.state(id).region(),()->{});
            Vec3 surface=slabProbe.surfaceAt(new Vec3(slab.getX()+.23,slab.getY()+.5,slab.getZ()+.71));
            h.assertTrue(surface!=null && Math.abs(surface.y-(slab.getY()+.5))<1e-8,"slab mouse height rounded to grid");
            var obstruction=BlockPos.containing(feet); level.setBlockAndUpdate(obstruction,Blocks.STONE.defaultBlockState());
            service.tacticalActions().request(player,new TacticalNetwork.Request(service.generation(),id,UUID.randomUUID(),service.state(id).version(),selectedIntent,false));
            h.assertTrue(!service.tacticalActions().running(player.getUUID()) && service.state(id).members().get(player.getUUID()).action(),"untrusted obstructed endpoint accepted or charged");
            level.setBlockAndUpdate(obstruction,Blocks.AIR.defaultBlockState());
            service.stop(id);
            var bow = new ItemStack(Items.BOW); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,bow);
            var source = AbilitySource.equipment(TacticalIntent.Hand.MAIN_HAND, new TacticalIntent.ItemReference(player.getInventory().getSelectedSlot(),TacticalItems.revision(player,bow)));
            var intent = new TacticalIntent("dndturn:bow",1,TacticalIntent.Capability.ATTACK,target,source);
            var flight = BowPreview.predict(player,intent,player.position(),()->{});
            h.assertTrue(flight.points().size()>1, "empty bow flight");
            var arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(level,player,new ItemStack(Items.ARROW),bow.copy());
            var aim = PlayerBehavior.plannedAim(player,intent); arrow.shootFromRotation(player,aim.pitch(),aim.yaw(),0,3,0);
            // Test the real arrow physics directly; no substitute player body driver is installed.
            for (int i=1;i<flight.points().size() && i<=2;i++) {
                arrow.tick(); h.assertTrue(arrow.position().distanceToSqr(PreviewPathfinder.vector(flight.points().get(i)))<1e-7,
                    "nominal arrow differs at step "+i+": "+arrow.position()+" / "+flight.points().get(i));
            }
            arrow.discard();
            for (String behavior : List.of("dndturn:egg","dndturn:experience_bottle","dndturn:splash_potion")) {
                var item = switch(behavior) { case "dndturn:egg" -> Items.EGG; case "dndturn:experience_bottle" -> Items.EXPERIENCE_BOTTLE; default -> Items.SPLASH_POTION; };
                net.minecraft.world.entity.projectile.Projectile thrown = switch(behavior) {
                    case "dndturn:egg" -> new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg(level,player,new ItemStack(item));
                    case "dndturn:experience_bottle" -> new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle(level,player,new ItemStack(item));
                    default -> new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion(level,player,new ItemStack(item));
                };
                var profile=ProjectileProfiles.get(behavior);
                var prediction=BowPreview.predict(player,mob,mob.getBoundingBox().getCenter(),player.position(),behavior,()->{});
                thrown.shootFromRotation(player,aim.pitch(),aim.yaw(),profile.pitchOffset(),(float)profile.speed(),0);
                h.assertTrue(prediction.points().size()>2,"throwable preview ended before comparison");
                for(int step=1;step<=2;step++) {
                    thrown.tick();
                    h.assertTrue(thrown.position().distanceToSqr(PreviewPathfinder.vector(prediction.points().get(step)))<1e-7,
                        behavior+" differs from native physics at "+step+": "+thrown.position()+" vs "+prediction.points().get(step));
                }
                thrown.discard();
            }
            level.setBlockAndUpdate(base.offset(2,1,0),Blocks.STONE.defaultBlockState());
            var blocked = BowPreview.predict(player,intent,player.position(),()->{});
            h.assertTrue(blocked.status().equals("BLOCK") && blocked.contact()!=null, "bow preview missed wall: "+blocked.status());
            level.setBlockAndUpdate(base.offset(2,1,0),Blocks.AIR.defaultBlockState());
            mob.setPos(base.getX()+12.5,base.getY(),base.getZ()+.5);
            for (var p : BlockPos.betweenClosed(base.offset(1,0,-1),base.offset(4,2,1)))
                level.setBlockAndUpdate(p,Blocks.WATER.defaultBlockState());
            var wetFlight = BowPreview.predict(player,intent,player.position(),()->{});
            var wetArrow = new net.minecraft.world.entity.projectile.arrow.Arrow(level,player,new ItemStack(Items.ARROW),bow.copy());
            var wetAim = PlayerBehavior.plannedAim(player,intent); wetArrow.shootFromRotation(player,wetAim.pitch(),wetAim.yaw(),0,3,0);
            h.assertTrue(wetFlight.points().size()>4,"water trajectory ended early");
            for (int i=1;i<=4;i++) {
                wetArrow.tick(); h.assertTrue(wetArrow.position().distanceToSqr(PreviewPathfinder.vector(wetFlight.points().get(i)))<1e-7,
                    "water entry/exit arrow differs at step "+i+": "+wetArrow.position()+" / "+wetFlight.points().get(i));
            }
            wetArrow.discard();
            cleanup.run(); h.succeed();
        } catch (RuntimeException | AssertionError error) { cleanup.run(); throw error; } });
    }

    private static void checkFlatRoutes(GameTestHelper h, net.minecraft.server.level.ServerPlayer player, BlockPos base) {
        Vec3 start = player.position();
        for (Vec3 offset : List.of(new Vec3(-7.8,0,-3.4), new Vec3(3.4,0,7.8), new Vec3(-6,0,0))) {
            Vec3 goal = start.add(offset);
            var finder = new PreviewPathfinder(player, null, () -> {});
            var direct = finder.directTo(goal);
            h.assertTrue(direct != null && !direct.isEmpty(), "flat direct route rejected: " + offset);
            h.assertTrue(direct.getLast().feet().equals(PreviewPathfinder.value(goal)), "direct route lost exact endpoint");
            h.assertTrue(Math.abs(length(start,direct)-offset.length())<1e-7, "flat route detoured instead of following the straight line");
            h.assertTrue(finder.revalidate(direct), "direct route skipped edge validation");
            finder.destination(goal);
            int slices=0; while (!finder.advance(16) && ++slices<64) {}
            var searched = finder.finishAt(goal);
            h.assertTrue(searched!=null && Math.abs(length(start,searched)-offset.length())<1e-7,
                "search result retained a diagonal/axis dogleg on flat ground");
        }
        var wall=base.offset(-3,0,0);
        for (int y=0;y<3;y++) player.level().setBlockAndUpdate(wall.above(y),Blocks.STONE.defaultBlockState());
        try {
            var finder=new PreviewPathfinder(player,null,()->{});
            Vec3 goal=start.add(-6,0,0);
            h.assertTrue(finder.directTo(goal)==null,"direct path crossed a wall");
            finder.destination(goal);
            int slices=0; while (!finder.advance(16) && ++slices<64) {}
            var detour=finder.finishAt(goal);
            h.assertTrue(detour!=null && finder.revalidate(detour),"wall detour unavailable or unsafe");
            h.assertTrue(length(start,detour)<=4+2*Math.sqrt(2)+1e-7,
                "A* preferred unnecessary diagonals: " + length(start,detour));
        } finally {
            for (int y=0;y<3;y++) player.level().setBlockAndUpdate(wall.above(y),Blocks.AIR.defaultBlockState());
        }
        var hole=base.offset(-3,-1,0);
        player.level().setBlockAndUpdate(hole,Blocks.AIR.defaultBlockState());
        try {
            h.assertTrue(new PreviewPathfinder(player,null,()->{}).directTo(start.add(-6,0,0))==null,
                "direct path ignored missing ground support");
        } finally { player.level().setBlockAndUpdate(hole,Blocks.STONE.defaultBlockState()); }
        h.assertTrue(new PreviewPathfinder(player,null,()->{throw new AssertionError("over-budget route probed world");})
            .directTo(start.add(65,0,0))==null,"direct route exceeded step budget");
    }

    private static double length(Vec3 start, List<TacticalNetwork.PreviewStep> route) {
        double length=0; Vec3 from=start;
        for (var step:route) { Vec3 to=PreviewPathfinder.vector(step.feet()); length+=from.distanceTo(to); from=to; }
        return length;
    }
}
