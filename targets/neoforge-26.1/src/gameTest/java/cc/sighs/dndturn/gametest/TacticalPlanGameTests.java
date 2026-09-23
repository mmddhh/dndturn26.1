package cc.sighs.dndturn.gametest;

import cc.sighs.dndturn.combat.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class TacticalPlanGameTests {
    private TacticalPlanGameTests() {}
    public static void interactions(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos base = helper.absolutePos(new BlockPos(45001, 121, 1));
        var level = helper.getLevel();
        Set<Long> forced = new HashSet<>();
        for (int x = (base.getX() - 24) >> 4; x <= (base.getX() + 24) >> 4; x++)
            for (int z = (base.getZ() - 24) >> 4; z <= (base.getZ() + 24) >> 4; z++) {
                level.getChunk(x, z);
                if (level.setChunkForced(x, z, true)) forced.add(net.minecraft.world.level.ChunkPos.pack(x, z));
            }
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-5, -1, -5), base.offset(5, -1, 5))) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-5, 0, -5), base.offset(5, 5, 5))) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        player.setPos(base.getX() + .5, base.getY(), base.getZ() + .5);
        player.setNoGravity(true);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var service = ServerCombatService.forServer(level.getServer());
        Runnable cleanup = () -> {
            service.leave(player.getUUID());
            for (long packed : forced) {
                var c = net.minecraft.world.level.ChunkPos.unpack(packed); level.setChunkForced(c.x(), c.z(), false);
            }
            forced.clear();
        };
        helper.runAtTickTime(199, cleanup);
        try {
            service.requestStart(player, UUID.randomUUID());
            UUID id = service.encounterOf(player.getUUID());
            BlockPos target = base.offset(1, 0, 0);
            level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
            player.getInventory().setItem(player.getInventory().getSelectedSlot(), new ItemStack(Items.DIRT, 8));
            level.setBlockAndUpdate(target, Blocks.LEVER.defaultBlockState());
            var free = request(player, service, id, TacticalIntent.Capability.USE_BLOCK, target);
            service.tacticalActions().request(player, free);
            helper.assertTrue(level.getBlockState(target.above()).isAir() && player.getMainHandItem().getCount() == 8,
                "free block interaction fell back to held block placement");
            helper.assertTrue(service.state(id).members().get(player.getUUID()).action(), "unhandled free interaction spent action");
            level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
            var place = request(player, service, id, TacticalIntent.Capability.PLACE, target);
            service.tacticalActions().request(player, place);
            helper.assertTrue(level.getBlockState(target.above()).is(Blocks.DIRT) && player.getMainHandItem().getCount() == 7,
                "paid placement did not execute through vanilla: " + service.results(id, 0, 64).results());
            helper.assertTrue(!service.state(id).members().get(player.getUUID()).action(), "accepted placement did not spend action");
            service.tacticalActions().request(player, place);
            helper.assertTrue(player.getMainHandItem().getCount() == 7, "duplicate plan repeated placement");
            BlockPos chest = base.offset(-1, 0, 0);
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            var events = ((cc.sighs.dndturn.mixin.BlockEventsAccessor)level).dndturn$blockEvents();
            // A stale signal from before regional suspension must not overwrite a newer close.
            events.add(new net.minecraft.world.level.BlockEventData(chest, Blocks.CHEST, 1, 1));
            helper.assertTrue(ChestPresentation.accepts(level, chest, Blocks.CHEST, 1), "normal chest lid was not classified");
            service.tacticalActions().request(player, request(player, service, id, TacticalIntent.Capability.USE_BLOCK, chest));
            helper.assertTrue(service.tacticalActions().mayUseContainer(player), "free chest did not grant bounded container access after action spent");
            player.closeContainer();
            helper.assertTrue(events.stream().noneMatch(e -> e.pos().equals(chest) && e.paramA() == 1),
                "closed chest retained a stale queued lid target");
            level.blockEvent(chest, Blocks.CHEST, 2, 1);
            helper.assertTrue(events.stream().anyMatch(e -> e.pos().equals(chest) && e.paramA() == 2),
                "unclassified event bypassed the simulation queue");
            helper.assertTrue(!ChestPresentation.accepts(level, chest, Blocks.PISTON, 1)
                && !ChestPresentation.accepts(level, chest, Blocks.TRAPPED_CHEST, 1),
                "simulation block was classified as a lid signal");
            service.stop(id);
            // Same target and final aim from opposing initial headings: observation must
            // include the actual bed head, not the neighbor selected by the old heading.
            BlockPos bedSupport = base.offset(1, -1, 2);
            for (float yaw : new float[] {0, 90, 180, -90}) {
                service.requestStart(player, UUID.randomUUID());
                UUID bedEncounter = service.encounterOf(player.getUUID());
                player.setYRot(yaw);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.RED_BED, 2));
                var bedRequest = request(player, service, bedEncounter, TacticalIntent.Capability.PLACE, bedSupport);
                float beforeYaw = player.getYRot();
                var aim = PlayerBehavior.plannedAim(player, bedRequest.intent());
                helper.assertTrue(player.getYRot() == beforeYaw, "pure aim query changed body");
                service.tacticalActions().request(player, bedRequest);
                BlockPos foot = bedSupport.above();
                helper.assertTrue(level.getBlockState(foot).is(Blocks.RED_BED), "bed plan failed for initial heading " + yaw);
                BlockPos head = foot.relative(level.getBlockState(foot).getValue(net.minecraft.world.level.block.BedBlock.FACING));
                var evidence = service.abilityCheckpoint(bedRequest.operation());
                Set<GridCell> actualWrites = Set.of(new GridCell(foot.getX(), foot.getY(), foot.getZ()),
                    new GridCell(head.getX(), head.getY(), head.getZ()));
                helper.assertTrue(evidence != null && evidence.before() != null && evidence.observed() != null
                    && evidence.before().blocks().stream().map(AbilityCheckpoint.Block::cell).collect(java.util.stream.Collectors.toSet()).equals(actualWrites)
                    && evidence.observed().blocks().stream().map(AbilityCheckpoint.Block::cell).collect(java.util.stream.Collectors.toSet()).equals(actualWrites),
                    "bed before/after did not cover actual writes");
                helper.assertTrue(player.getYRot() == aim.yaw() && player.getMainHandItem().getCount() == 1,
                    "bed execution changed prepared heading or consumption");
                service.tacticalActions().request(player, bedRequest);
                helper.assertTrue(player.getMainHandItem().getCount() == 1, "bed retry executed twice");
                service.stop(bedEncounter);
                level.setBlockAndUpdate(foot, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(head, Blocks.AIR.defaultBlockState());
            }
            // The same prepared observation contract also covers vertical multi-block writes
            // and replacement in the clicked cell, rather than its adjacent face.
            BlockPos support = base.offset(1, -1, 1);
            for (var placed : List.of(Items.OAK_DOOR, Items.SUNFLOWER, Items.TALL_GRASS, Items.DIRT)) {
                service.requestStart(player, UUID.randomUUID());
                UUID placementEncounter = service.encounterOf(player.getUUID());
                level.setBlockAndUpdate(support, Blocks.GRASS_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(support.above(), placed == Items.DIRT
                    ? Blocks.SHORT_GRASS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(support.above(2), Blocks.AIR.defaultBlockState());
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(placed, 2));
                var placement = request(player, service, placementEncounter, TacticalIntent.Capability.PLACE,
                    placed == Items.DIRT ? support.above() : support);
                service.tacticalActions().request(player, placement);
                if (placed == Items.SUNFLOWER) {
                    // .84 registers sunflower as TallFlowerBlock, outside the exact audited
                    // DoublePlantBlock class contract. Retain the rejection counterexample.
                    helper.assertTrue(level.getBlockState(support.above()).isAir()
                        && level.getBlockState(support.above(2)).isAir() && player.getMainHandItem().getCount() == 2
                        && service.state(placementEncounter).members().get(player.getUUID()).action(),
                        "unsupported plant override wrote world or consumed resources");
                    service.stop(placementEncounter);
                    continue;
                }
                var block = ((net.minecraft.world.item.BlockItem)placed).getBlock();
                helper.assertTrue(level.getBlockState(support.above()).is(block)
                    && (placed == Items.DIRT || level.getBlockState(support.above(2)).is(block)),
                    "prepared placement did not write expected cells: " + placed + " lower=" + level.getBlockState(support.above())
                        + " upper=" + level.getBlockState(support.above(2)) + " results=" + service.results(placementEncounter,0,128).results());
                var cells = new HashSet<GridCell>();
                cells.add(new GridCell(support.getX(), support.getY()+1, support.getZ()));
                if (placed != Items.DIRT) cells.add(new GridCell(support.getX(), support.getY()+2, support.getZ()));
                var evidence = service.abilityCheckpoint(placement.operation());
                helper.assertTrue(evidence.before().blocks().stream().map(AbilityCheckpoint.Block::cell)
                        .collect(java.util.stream.Collectors.toSet()).equals(cells)
                    && evidence.observed().blocks().stream().map(AbilityCheckpoint.Block::cell)
                        .collect(java.util.stream.Collectors.toSet()).equals(cells)
                    && player.getMainHandItem().getCount() == 1, "placement observations/consumption differ: " + placed);
                service.stop(placementEncounter);
                level.setBlockAndUpdate(support.above(2), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(support.above(), Blocks.AIR.defaultBlockState());
            }
            service.requestStart(player, UUID.randomUUID());
            id = service.encounterOf(player.getUUID());
            player.getInventory().setItem(player.getInventory().getSelectedSlot(), new ItemStack(Items.APPLE, 2));
            player.getFoodData().setFoodLevel(10);
            var self = new TacticalIntent.Target(TacticalIntent.TargetKind.SELF, level.dimension().identifier().toString(), null, null, -1, 0, 0, 0);
            var eat = new TacticalIntent("dndturn:consume", 1, TacticalIntent.Capability.USE_ITEM, self, cc.sighs.dndturn.combat.AbilitySource.equipment(TacticalIntent.Hand.MAIN_HAND, item(player)));
            service.tacticalActions().request(player, new TacticalNetwork.Request(service.generation(), id, UUID.randomUUID(), service.state(id).version(), eat, false));
            helper.assertTrue(player.isUsingItem() && !service.state(id).members().get(player.getUUID()).action(), "accepted food did not start and charge once");
            UUID eatingEncounter = id;
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(!player.isUsingItem(), "food still using"))
                .thenExecute(() -> {
                    helper.assertTrue(player.getMainHandItem().getCount() == 1 && player.getFoodData().getFoodLevel() > 10,
                        "food did not complete while body paused");
                    helper.assertTrue(!service.state(eatingEncounter).members().get(player.getUUID()).action(), "food refunded action");
                    service.stop(eatingEncounter);
                    service.requestStart(player, UUID.randomUUID());
                    UUID mining = service.encounterOf(player.getUUID());
                    player.getInventory().setItem(player.getInventory().getSelectedSlot(), new ItemStack(Items.IRON_PICKAXE));
                    BlockPos ore = base.offset(0, 0, 1);
                    level.setBlockAndUpdate(ore, Blocks.STONE.defaultBlockState());
                    service.tacticalActions().request(player, request(player, service, mining, TacticalIntent.Capability.BREAK, ore));
                    helper.assertTrue(!service.state(mining).members().get(player.getUUID()).action(), "mining did not charge on accepted start");
                }).thenWaitUntil(() -> helper.assertTrue(level.getBlockState(base.offset(0, 0, 1)).isAir(), "mining still pending"))
                .thenExecute(() -> {
                    UUID mining = service.encounterOf(player.getUUID());
                    helper.assertTrue(!service.state(mining).members().get(player.getUUID()).action(), "mining refunded action");
                    cleanup.run();
                }).thenSucceed();
        } catch (Throwable error) { cleanup.run(); throw error; }
    }
    private static boolean extensionRegistered;
    private static int extensionStarts, extensionCancels;
    private static boolean extensionReleaseFailure;
    public static void registerExtension() {
        if (extensionRegistered) return;
        AbilityPolicyChecks.register();
        TacticalCapabilities.register(new PlayerBehavior("test:registered_use", "Registered test use", TacticalIntent.Capability.USE_ITEM,
                Set.of(TacticalIntent.TargetKind.BLOCK)) {
            public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) { return null; }
            public boolean canExecute(ServerPlayer p, TacticalIntent i, net.minecraft.world.phys.Vec3 feet) {
                return feet.distanceToSqr(new net.minecraft.world.phys.Vec3(i.target().cell().x()+.5, i.target().cell().y(), i.target().cell().z()+.5)) < 2;
            }
            public void start(TacticalActions a, ServerPlayer p, TacticalActions.Execution e) { a.beginStep(p, e); extensionStarts++; a.accept(p, e, "registered extension started"); }
            public void tick(TacticalActions a, ServerPlayer p, TacticalActions.Execution e) { a.finishAction(p, e, OperationRecord.Outcome.COMPLETED, "registered extension completed"); }
            public void release(TacticalActions a, ServerPlayer p, TacticalActions.Execution e) {
                extensionCancels++;
                if (extensionReleaseFailure) throw new IllegalStateException("injected release failure");
            }
        });
        extensionRegistered = true;
    }
    public static void behaviors(GameTestHelper helper) {
        registerExtension(); extensionStarts = 0; extensionCancels = 0;
        helper.assertTrue(TacticalCapabilities.frozen(), "behavior registry was not frozen before world execution");
        boolean lateRegistrationRejected = false;
        try { TacticalCapabilities.register(TacticalCapabilities.all().getFirst()); }
        catch (IllegalStateException expected) { lateRegistrationRejected = true; }
        helper.assertTrue(lateRegistrationRejected, "runtime behavior replacement was accepted");
        var player = helper.makeMockServerPlayerInLevel(); var level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(61001, 151, 1));
        Set<Long> forced = new HashSet<>();
        for (int x=(base.getX()-24)>>4; x<=(base.getX()+24)>>4; x++) for (int z=(base.getZ()-24)>>4; z<=(base.getZ()+24)>>4; z++) {
            level.getChunk(x,z); if (level.setChunkForced(x,z,true)) forced.add(net.minecraft.world.level.ChunkPos.pack(x,z));
        }
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-6,-1,-6), base.offset(6,5,6)))
            level.setBlockAndUpdate(pos, pos.getY()<base.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5); player.setNoGravity(true);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var service = ServerCombatService.forServer(level.getServer());
        Runnable cleanup = () -> { service.leave(player.getUUID()); for (long packed : forced) { var c=net.minecraft.world.level.ChunkPos.unpack(packed); level.setChunkForced(c.x(),c.z(),false); } forced.clear(); };
        helper.runAtTickTime(199,cleanup);
        var named=net.minecraft.world.entity.EntityType.ZOMBIE.create(level,net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
        named.setPos(base.getX()+2.5,base.getY()+40,base.getZ()+.5);
        named.setPersistenceRequired(); named.setNoAi(true); named.setNoGravity(true);
        named.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
        helper.assertTrue(level.addFreshEntity(named),"entity fixture registration failed");
        var receiver = net.minecraft.world.entity.EntityType.COW.create(level, net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
        receiver.addTag("dndturn_test_receiver");
        receiver.setPos(base.getX()+3.5, base.getY()+40, base.getZ()+.5);
        receiver.setPersistenceRequired(); receiver.setNoAi(true); receiver.setNoGravity(true);
        helper.assertTrue(level.addFreshEntity(receiver), "registered receiver fixture failed");
        helper.runAtTickTime(199, receiver::discard);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(level.getEntity(named.getUUID()) == named,
            "waiting for fixture chunk entity registration")).thenExecute(() -> {
        try {
            var mobContext = new TacticalActor(named);
            helper.assertTrue(mobContext.id().equals(named.getUUID())
                && mobContext.instance().equals(((PresentationIdentity)named).dndturn$presentationInstance()),
                "runtime context did not capture the current Mob instance");
            boolean missingPlayerPort = false;
            try { mobContext.requirePlayer(); } catch (IllegalStateException expected) { missingPlayerPort = true; }
            helper.assertTrue(missingPlayerPort && new TacticalActor(player).requirePlayer() == player,
                "actor context emulated or lost a player inventory/input port");
            service.requestStart(player,UUID.randomUUID()); UUID id=service.encounterOf(player.getUUID());
            AbilityPolicyChecks.verify(helper, player, service.state(id));
            var selfSelection = new TacticalIntent.Target(TacticalIntent.TargetKind.SELF,level.dimension().identifier().toString(),null,null,-1,0,0,0);
            player.getInventory().setItem(1,new ItemStack(Items.APPLE,2));
            player.getInventory().setItem(2,new ItemStack(Items.DIAMOND_SWORD));
            // CAP-T05: discovery must not silently commit a different equipment selection.
            int originalSlot = player.getInventory().getSelectedSlot();
            var originalPosition = player.position();
            var originalState = service.state(id);
            var pureQuery = new TacticalNetwork.Query(service.generation(),id,UUID.randomUUID(),selfSelection,TacticalIntent.Hand.MAIN_HAND,2,3,null);
            var pure = service.tacticalActions().discover(player, pureQuery);
            helper.assertTrue(pure.item() == null && player.getInventory().getSelectedSlot() == originalSlot,
                "pure discovery changed equipment");
            helper.assertTrue(player.position().equals(originalPosition) && service.state(id).equals(originalState)
                && !player.isUsingItem() && !service.tacticalActions().running(player.getUUID()),
                "pure discovery changed world, rules, or control");
            var selection = service.tacticalActions().selectAndDiscover(player,new TacticalNetwork.Query(service.generation(),id,UUID.randomUUID(),selfSelection,TacticalIntent.Hand.MAIN_HAND,2,3,null));
            helper.assertTrue(player.getInventory().getSelectedSlot()==2 && selection.item().slot()==2 && selection.defaultBehavior().equals("dndturn:melee"),"selection confirmation bound wrong item");
            var confirmedQuery = new TacticalNetwork.Query(service.generation(),id,UUID.randomUUID(),selfSelection,TacticalIntent.Hand.MAIN_HAND,2,4,selection.item());
            var confirmedState = service.state(id);
            for (int repeat = 0; repeat < 3; repeat++) {
                var discovered = service.tacticalActions().discover(player, confirmedQuery);
                helper.assertTrue(selection.item().equals(discovered.item()) && service.state(id).equals(confirmedState)
                    && player.getInventory().getSelectedSlot() == 2, "repeated pure discovery mutated selection or rules");
            }
            var late = service.tacticalActions().selectAndDiscover(player,new TacticalNetwork.Query(service.generation(),id,UUID.randomUUID(),selfSelection,TacticalIntent.Hand.MAIN_HAND,1,2,null));
            helper.assertTrue(late.item()==null && player.getInventory().getSelectedSlot()==2,"late selection switched back to old slot");
            player.getMainHandItem().setDamageValue(1);
            var changed = service.tacticalActions().selectAndDiscover(player,new TacticalNetwork.Query(service.generation(),id,UUID.randomUUID(),selfSelection,TacticalIntent.Hand.MAIN_HAND,2,4,selection.item()));
            helper.assertTrue(changed.item()==null,"changed stack silently rebound selection");
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),new ItemStack(Items.IRON_AXE));
            BlockPos near=base.offset(1,0,0);
            var target=new TacticalIntent.Target(TacticalIntent.TargetKind.BLOCK,level.dimension().identifier().toString(),null,new GridCell(near.getX(),near.getY(),near.getZ()),4,0,.5,.5);
            var options=service.tacticalActions().selectAndDiscover(player,new TacticalNetwork.Query(service.generation(),id,UUID.randomUUID(),target,TacticalIntent.Hand.MAIN_HAND));
            helper.assertTrue(options.offers().stream().anyMatch(o -> o.intent().behaviorId().equals("dndturn:tool") && o.reason().isEmpty()),"axe tool capability missing");
            var offer=options.offers().stream().filter(o -> o.intent().behaviorId().equals("test:registered_use")).findFirst().orElseThrow();
            helper.assertTrue(offer.reason().isEmpty(),"registered behavior unavailable");
            var packet=new TacticalNetwork.Request(service.generation(),id,UUID.randomUUID(),options.version(),offer.intent(),false);
            service.tacticalActions().request(player,packet);
            helper.assertTrue(extensionStarts==1 && !service.state(id).members().get(player.getUUID()).action(),"extension did not execute and charge");
            service.tacticalActions().request(player,packet);
            service.tacticalActions().cancel(player.getUUID(),"test interruption");
            helper.assertTrue(extensionStarts==1 && extensionCancels==1 && !service.tacticalActions().running(player.getUUID()),"extension cancellation or dedup failed");
            service.tacticalActions().request(player,packet);
            helper.assertTrue(extensionStarts==1,"terminal extension repeated");
            service.stop(id); service.requestStart(player,UUID.randomUUID()); id=service.encounterOf(player.getUUID());
            BlockPos far=base.offset(4,0,0);
            var farTarget=new TacticalIntent.Target(TacticalIntent.TargetKind.BLOCK,level.dimension().identifier().toString(),null,new GridCell(far.getX(),far.getY(),far.getZ()),4,0,.5,.5);
            options=service.tacticalActions().selectAndDiscover(player,new TacticalNetwork.Query(service.generation(),id,UUID.randomUUID(),farTarget,TacticalIntent.Hand.MAIN_HAND));
            offer=options.offers().stream().filter(o -> o.intent().behaviorId().equals("test:registered_use")).findFirst().orElseThrow();
            service.tacticalActions().request(player,new TacticalNetwork.Request(service.generation(),id,UUID.randomUUID(),options.version(),offer.intent(),false));
            helper.assertTrue(service.tacticalActions().running(player.getUUID()) && extensionStarts==1 && service.state(id).members().get(player.getUUID()).action(),"extension did not reserve and approach before use");
            service.tacticalActions().cancel(player.getUUID(),"cancel approach");
            // Generic unhandled block must not fall back to dirt placement.
            level.setBlockAndUpdate(near,Blocks.STONE.defaultBlockState()); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT,8));
            service.tacticalActions().request(player,request(player,service,id,TacticalIntent.Capability.USE_BLOCK,near));
            helper.assertTrue(level.getBlockState(near.above()).isAir() && player.getMainHandItem().getCount()==8,"unhandled free block fell back to item");
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock> protect = event -> {
                if (event.getEntity() == player) event.setCanceled(true);
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(protect);
            try {
                service.tacticalActions().request(player,request(player,service,id,TacticalIntent.Capability.PLACE,near));
                helper.assertTrue(level.getBlockState(near.above()).isAir() && player.getMainHandItem().getCount()==8
                    && service.state(id).members().get(player.getUUID()).action(),"protection cancellation mutated world or charged");
            } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(protect); }
            // A clicked in-domain block cannot authorize the outside placement cell.
            var region=service.state(id).region();
            BlockPos edge=base;
            while (region.containsBlock(edge.getX()+1,edge.getY(),edge.getZ())) edge=edge.east();
            level.setBlockAndUpdate(edge,Blocks.STONE.defaultBlockState());
            var edgeHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(edge).add(.5,0,0),net.minecraft.core.Direction.EAST,edge,false);
            boolean refused=false;
            try { TacticalImpact.itemOnBlock(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,edgeHit)); }
            catch (IllegalStateException expected) { refused=true; }
            helper.assertTrue(refused && player.getMainHandItem().getCount()==8,"outside placement footprint authorized");
            // Door upper half and bed head must be authorized before their first write.
            BlockPos top = new BlockPos(base.getX()+2,(int)Math.floor(region.maxY()-.5),base.getZ());
            for (var blockItem : List.of(Items.OAK_DOOR,Items.RED_BED)) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(blockItem));
                BlockPos support = blockItem == Items.OAK_DOOR ? top.below() : edge.below();
                level.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(support.above(),Blocks.AIR.defaultBlockState());
                player.setYRot(-90);
                var multiHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(support).add(0,.5,0),net.minecraft.core.Direction.UP,support,false);
                refused=false;
                try { TacticalImpact.itemOnBlock(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,multiHit)); }
                catch (IllegalStateException expected) { refused=true; }
                helper.assertTrue(refused && player.getMainHandItem().getCount()==1,"multi-block boundary was authorized: "+blockItem);
            }
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.DIRT,8));

            // Stale stack rejection stays rejected even when the original item is restored.
            var stale=request(player,service,id,TacticalIntent.Capability.PLACE,near);
            player.getMainHandItem().shrink(1); service.tacticalActions().request(player,stale);
            player.getMainHandItem().grow(1); service.tacticalActions().request(player,stale);
            helper.assertTrue(player.getMainHandItem().getCount()==8 && level.getBlockState(near.above()).isAir(),"pre-start rejection was replayed");
            // Fluid bucket uses the requested face, never the old player orientation.
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET));
            level.setBlockAndUpdate(near,Blocks.WATER.defaultBlockState());
            var fluidTarget=new TacticalIntent.Target(TacticalIntent.TargetKind.BLOCK,level.dimension().identifier().toString(),null,new GridCell(near.getX(),near.getY(),near.getZ()),1,.5,1,.5);
            options=service.tacticalActions().selectAndDiscover(player,new TacticalNetwork.Query(service.generation(),id,UUID.randomUUID(),fluidTarget,TacticalIntent.Hand.MAIN_HAND));
            offer=options.offers().stream().filter(o -> o.intent().behaviorId().equals("dndturn:bucket")).findFirst().orElseThrow();
            player.setYRot(180); player.setXRot(60);
            service.tacticalActions().request(player,new TacticalNetwork.Request(service.generation(),id,UUID.randomUUID(),options.version(),offer.intent(),false));
            helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET),"fluid ray adapter failed: "+service.results(id,0,64).results());
            service.stop(id); service.requestStart(player,UUID.randomUUID()); id=service.encounterOf(player.getUUID());
            level.setBlockAndUpdate(near,Blocks.AIR.defaultBlockState());
            options=service.tacticalActions().selectAndDiscover(player,new TacticalNetwork.Query(service.generation(),id,UUID.randomUUID(),target,TacticalIntent.Hand.MAIN_HAND));
            offer=options.offers().stream().filter(o -> o.intent().behaviorId().equals("test:registered_use")).findFirst().orElseThrow();
            service.tacticalActions().request(player,new TacticalNetwork.Request(service.generation(),id,UUID.randomUUID(),options.version(),offer.intent(),false));
            UUID completedEncounter=id;
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(!service.tacticalActions().running(player.getUUID()),"extension awaiting normal tick"))
                .thenExecute(() -> {
                    helper.assertTrue(service.results(completedEncounter,0,64).results().stream().anyMatch(r -> r.reason().equals("registered extension completed")),"extension result missing");
                    helper.assertTrue(!service.state(completedEncounter).members().get(player.getUUID()).action(),"extension fee refunded");
                    service.stop(completedEncounter); service.requestStart(player,UUID.randomUUID());
                    UUID eating=service.encounterOf(player.getUUID());
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.APPLE,2)); player.getFoodData().setFoodLevel(10);
                    var self=new TacticalIntent.Target(TacticalIntent.TargetKind.SELF,level.dimension().identifier().toString(),null,null,-1,0,0,0);
                    var eat=new TacticalIntent("dndturn:consume", 1, TacticalIntent.Capability.USE_ITEM, self, cc.sighs.dndturn.combat.AbilitySource.equipment(TacticalIntent.Hand.MAIN_HAND, item(player)));
                    service.tacticalActions().request(player,new TacticalNetwork.Request(service.generation(),eating,UUID.randomUUID(),service.state(eating).version(),eat,false));
                    service.tacticalActions().cancel(player.getUUID(),"food interrupted");
                    helper.assertTrue(!player.isUsingItem() && player.getMainHandItem().getCount()==2 && !service.state(eating).members().get(player.getUUID()).action(),"food cancellation leaked control or refunded cost");
                    service.stop(eating);
                    named.setPos(base.getX()+2.5,base.getY(),base.getZ()+.5);
                    try {
                        service.requestStart(player,UUID.randomUUID()); UUID naming=service.encounterOf(player.getUUID());
                        if (!player.getUUID().equals(service.state(naming).current())) service.endCurrentTurn(naming,UUID.randomUUID(),service.state(naming).version());
                        var tag=new ItemStack(Items.NAME_TAG); tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Behavior target"));
                        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,tag);
                        var entityTarget=new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY,level.dimension().identifier().toString(),named.getUUID(),null,-1,0,0,0);
                        var entityOptions=service.tacticalActions().selectAndDiscover(player,new TacticalNetwork.Query(service.generation(),naming,UUID.randomUUID(),entityTarget,TacticalIntent.Hand.OFF_HAND));
                        var entityOffer=entityOptions.offers().stream().filter(o -> o.intent().behaviorId().equals("dndturn:entity_item")).findFirst().orElseThrow();
                        service.tacticalActions().request(player,new TacticalNetwork.Request(service.generation(),naming,UUID.randomUUID(),entityOptions.version(),entityOffer.intent(),false));
                        helper.assertTrue(named.hasCustomName() && named.getCustomName().getString().equals("Behavior target") && player.getOffhandItem().isEmpty(),"offhand entity use failed: "+service.results(naming,0,64).results());
                        service.stop(naming);
                        named.setPos(base.getX()+1.5, base.getY(), base.getZ()+.5);
                        for (boolean hit : new boolean[]{false, true}) {
                            named.setHealth(named.getMaxHealth());
                            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.WOODEN_SWORD));
                            service.requestStart(player,UUID.randomUUID());
                            UUID combat=service.encounterOf(player.getUUID());
                            if (!player.getUUID().equals(service.state(combat).current())) service.endCurrentTurn(combat,UUID.randomUUID(),service.state(combat).version());
                            service.useAttackRandomForGameTest(combat,new Random() { @Override public int nextInt(int bound) { return hit ? bound-1 : 0; } });
                            var intent=new TacticalIntent("dndturn:melee", 1, TacticalIntent.Capability.ATTACK, entityTarget, cc.sighs.dndturn.combat.AbilitySource.equipment(TacticalIntent.Hand.MAIN_HAND, item(player)));
                            var attack=new TacticalNetwork.Request(service.generation(),combat,UUID.randomUUID(),service.state(combat).version(),intent,false);
                            service.tacticalActions().request(player,attack);
                            var results=service.results(combat,0,128).results();
                            helper.assertTrue(results.stream().anyMatch(r -> r.snapshot().operationId().equals(attack.operation()) && r.outcome()==OperationRecord.Outcome.COMPLETED),"nonlethal/miss root not completed: "+results);
                            helper.assertTrue(named.isAlive() && (hit ? named.getHealth()<named.getMaxHealth() : named.getHealth()==named.getMaxHealth()),"deterministic nonlethal/miss outcome incorrect");
                            helper.assertTrue(!service.state(combat).members().get(player.getUUID()).action(),"legal attack/miss did not consume action");
                            float health=named.getHealth(); int size=results.size();
                            service.tacticalActions().request(player,attack);
                            helper.assertTrue(named.getHealth()==health && service.results(combat,0,128).results().size()==size,"attack retry repeated effect");
                            service.stop(combat);
                        }
                        named.setHealth(1);
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_SWORD));
                        service.requestStart(player,UUID.randomUUID());
                        UUID lethal=service.encounterOf(player.getUUID());
                        if (!player.getUUID().equals(service.state(lethal).current())) service.endCurrentTurn(lethal,UUID.randomUUID(),service.state(lethal).version());
                        service.useAttackRandomForGameTest(lethal,new Random() { @Override public int nextInt(int bound) { return bound-1; } });
                        var melee=new TacticalIntent("dndturn:melee", 1, TacticalIntent.Capability.ATTACK, entityTarget, cc.sighs.dndturn.combat.AbilitySource.equipment(TacticalIntent.Hand.MAIN_HAND, item(player)));
                        var lethalRequest=new TacticalNetwork.Request(service.generation(),lethal,UUID.randomUUID(),service.state(lethal).version(),melee,false);
                        service.tacticalActions().request(player,lethalRequest);
                        var lethalResults=service.results(lethal,0,128).results();
                        helper.assertTrue(lethalResults.stream().anyMatch(r -> r.snapshot().operationId().equals(lethalRequest.operation()) && r.outcome()==OperationRecord.Outcome.COMPLETED),
                            "confirmed lethal attack root must complete: "+lethalResults);
                        var terminal=lethalResults.stream().filter(r -> r.snapshot().operationId().equals(lethalRequest.operation())).findFirst().orElseThrow();
                        var projection=TacticalNetwork.Projection.terminal(service.generation(),1,terminal);
                        var buffer=io.netty.buffer.Unpooled.buffer();
                        try {
                            TacticalNetwork.Projection.CODEC.encode(buffer,projection);
                            var received=TacticalNetwork.Projection.CODEC.decode(buffer);
                            helper.assertTrue(received.outcome()==terminal.outcome() && received.reason().equals(terminal.reason()) && received.version()==terminal.publishedVersion(),"S2C terminal diverged from ledger");
                        } finally { buffer.release(); }
                        int count=lethalResults.size();
                        service.tacticalActions().request(player,lethalRequest);
                        helper.assertTrue(service.results(lethal,0,128).results().size()==count,"lethal retry published another result");
                        service.stop(lethal);
                        receiver.setPos(base.getX()+1.5, base.getY(), base.getZ()+.5);
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SWORD));
                        // Normal START calls beginEncounter, which enrolls living Mob instances in
                        // the discovery AABB. Assert that membership before testing capability support.
                        service.requestStart(player, UUID.randomUUID());
                        UUID adapted = service.encounterOf(player.getUUID());
                        helper.assertTrue(service.state(adapted).members().containsKey(receiver.getUUID())
                            && adapted.equals(service.encounterOf(receiver.getUUID())),
                            "normal START did not enroll the registered receiver");
                        if (!player.getUUID().equals(service.state(adapted).current()))
                            service.endCurrentTurn(adapted, UUID.randomUUID(), service.state(adapted).version());
                        service.useAttackRandomForGameTest(adapted, new Random() {
                            @Override public int nextInt(int bound) { return bound - 1; }
                        });
                        var adaptedTarget = new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY,
                            level.dimension().identifier().toString(), receiver.getUUID(), null, -1, 0, 0, 0);
                        var adaptedOptions = service.tacticalActions().selectAndDiscover(player,
                            new TacticalNetwork.Query(service.generation(), adapted, UUID.randomUUID(), adaptedTarget, TacticalIntent.Hand.MAIN_HAND));
                        var adaptedOffer = adaptedOptions.offers().stream()
                            .filter(o -> o.intent().behaviorId().equals("dndturn:melee") && o.reason().isEmpty()).findFirst().orElseThrow();
                        var adaptedRequest = new TacticalNetwork.Request(service.generation(), adapted, UUID.randomUUID(),
                            adaptedOptions.version(), adaptedOffer.intent(), false);
                        float receiverHealth = receiver.getHealth();
                        service.tacticalActions().request(player, adaptedRequest);
                        var adaptedResults = service.results(adapted, 0, 128).results();
                        helper.assertTrue(receiver.getHealth() < receiverHealth
                            && !service.state(adapted).members().get(player.getUUID()).action()
                            && adaptedResults.stream().anyMatch(r -> r.snapshot().operationId().equals(adaptedRequest.operation())
                                && r.terminal() && r.outcome() == OperationRecord.Outcome.COMPLETED),
                            "registered non-Zombie receiver failed shared melee plan: " + adaptedResults);
                        float afterAdapted = receiver.getHealth();
                        service.tacticalActions().request(player, adaptedRequest);
                        helper.assertTrue(receiver.getHealth() == afterAdapted
                            && service.results(adapted, 0, 128).results().size() == adaptedResults.size(),
                            "registered receiver retry replayed damage");
                        service.stop(adapted);
                        helper.assertTrue(service.encounterOf(receiver.getUUID()) == null,
                            "registered receiver retained membership after encounter stop");
                        var departedContext = new TacticalActor(receiver);
                        receiver.discard();
                        boolean staleContextRejected = false;
                        try { departedContext.verifyCurrent(); } catch (IllegalStateException expected) { staleContextRejected = true; }
                        helper.assertTrue(staleContextRejected, "removed instance retained a usable action context");
                        service.requestStart(player, UUID.randomUUID());
                        UUID faultEncounter = service.encounterOf(player.getUUID());
                        var faultOptions = service.tacticalActions().selectAndDiscover(player,
                            new TacticalNetwork.Query(service.generation(), faultEncounter, UUID.randomUUID(), target, TacticalIntent.Hand.MAIN_HAND));
                        var faultOffer = faultOptions.offers().stream().filter(o -> o.intent().behaviorId().equals("test:registered_use")).findFirst().orElseThrow();
                        var faultRequest = new TacticalNetwork.Request(service.generation(), faultEncounter, UUID.randomUUID(),
                            faultOptions.version(), faultOffer.intent(), false);
                        service.tacticalActions().request(player, faultRequest);
                        extensionReleaseFailure = true;
                        try { service.tacticalActions().tick(); }
                        finally { extensionReleaseFailure = false; }
                        var faultResult = service.results(faultEncounter, 0, 128).results().stream()
                            .filter(r -> r.snapshot().operationId().equals(faultRequest.operation()) && r.terminal()).findFirst().orElseThrow();
                        helper.assertTrue(faultResult.outcome() == OperationRecord.Outcome.COMPLETED
                            && faultResult.conclusion().release() == ExecutionConclusion.Release.FAILED
                            && !service.state(faultEncounter).members().get(player.getUUID()).action()
                            && !service.mayOrganizeInventory(player), "release failure erased effect/cost or allowed unsafe control");
                        int started = extensionStarts;
                        service.tacticalActions().request(player, faultRequest);
                        helper.assertTrue(extensionStarts == started, "release failure replayed the accepted world action");
                        var faultProjection = TacticalNetwork.Projection.terminal(service.generation(), 2, faultResult);
                        var faultBuffer = io.netty.buffer.Unpooled.buffer();
                        try {
                            TacticalNetwork.Projection.CODEC.encode(faultBuffer, faultProjection);
                            helper.assertTrue(faultProjection.equals(TacticalNetwork.Projection.CODEC.decode(faultBuffer))
                                && !faultBuffer.isReadable(), "release evidence did not survive projection codec");
                            } finally { faultBuffer.release(); }
                            service.tacticalActions().tick();
                            helper.assertTrue(!service.tacticalActions().controlFault(player.getUUID())
                                && service.tacticalActions().releaseReconciliation(faultRequest.operation()) != null
                                && !service.tacticalActions().releaseReconciliation(faultRequest.operation()).retired(),
                                "same-instance release retry did not reconcile");
                            var immutableFault = service.results(faultEncounter,0,128).results().stream()
                                .filter(r -> r.snapshot().operationId().equals(faultRequest.operation()) && r.terminal()).findFirst().orElseThrow();
                            helper.assertTrue(immutableFault.equals(faultResult) && extensionStarts == started,
                                "release reconciliation rewrote historical evidence or replayed effect");
                    } finally { named.discard(); receiver.discard(); cleanup.run(); }
                }).thenSucceed();
        } catch (Throwable failure) { cleanup.run(); throw failure; }
        });
    }
    public static void ranged(GameTestHelper helper) {
        helper.runAtTickTime(50, () -> rangedReady(helper, Items.BOW, 49001));
    }
    public static void crossbow(GameTestHelper helper) {
        helper.runAtTickTime(50, () -> rangedReady(helper, Items.CROSSBOW, 53001));
    }
    public static void snowball(GameTestHelper helper) {
        helper.runAtTickTime(50, () -> rangedReady(helper, Items.SNOWBALL, 57001));
    }
    private static void rangedReady(GameTestHelper helper, net.minecraft.world.item.Item weapon, int arena) {
        var player = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        // Keep distant ranged fixtures in dedicated stable chunks, independent of the randomized test structure origin.
        BlockPos base = new BlockPos(arena, 151, 1);
        Set<Long> forced = new HashSet<>();
        for (int x = (base.getX() - 24) >> 4; x <= (base.getX() + 24) >> 4; x++)
            for (int z = (base.getZ() - 24) >> 4; z <= (base.getZ() + 24) >> 4; z++) {
                level.getChunk(x, z);
                if (level.setChunkForced(x, z, true)) forced.add(net.minecraft.world.level.ChunkPos.pack(x, z));
            }
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-5, -1, -5), base.offset(5, 5, 5)))
            level.setBlockAndUpdate(pos, pos.getY() < base.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        player.setPos(base.getX() + .5, base.getY(), base.getZ() + .5);
        player.setNoGravity(true);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        for (var leftover : level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
                new net.minecraft.world.phys.AABB(base).inflate(20))) leftover.discard();
        helper.runAfterDelay(20, () -> {
        var priorDifficulty = level.getDifficulty();
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        helper.runAtTickTime(199, () -> level.getServer().setDifficulty(priorDifficulty, true));
        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level,net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
        zombie.setPos(base.getX()+3.5,base.getY(),base.getZ()+.5);
        helper.assertTrue(level.addFreshEntity(zombie),"ranged fixture registration failed");
        zombie.setPersistenceRequired();
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zombie.setNoAi(true); zombie.setNoGravity(true);
        zombie.setPos(base.getX()+3.5, base.getY(), base.getZ()+.5);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(level.getEntity(zombie.getUUID()) == zombie,
            "ranged fixture not visible after registration: " + zombie.getRemovalReason() + " difficulty=" + level.getDifficulty())).thenExecute(() -> {
        var service = ServerCombatService.forServer(level.getServer());
        service.requestStart(player, UUID.randomUUID());
        UUID encounter = service.encounterOf(player.getUUID());
        Runnable cleanup = () -> {
            service.stop(encounter); zombie.discard();
            level.getServer().setDifficulty(priorDifficulty, true);
            for (long packed : forced) { var c = net.minecraft.world.level.ChunkPos.unpack(packed); level.setChunkForced(c.x(), c.z(), false); }
            forced.clear();
        };
        helper.runAtTickTime(199, cleanup);
        try {
            if (!player.getUUID().equals(service.state(encounter).current()))
                service.endCurrentTurn(encounter, UUID.randomUUID(), service.state(encounter).version());
            player.getInventory().setItem(player.getInventory().getSelectedSlot(), new ItemStack(weapon, weapon == Items.SNOWBALL ? 4 : 1));
            player.getInventory().setItem(9, new ItemStack(Items.ARROW, 4));
            var target = new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY, level.dimension().identifier().toString(), zombie.getUUID(), null, -1, 0, 0, 0);
            var intent = new TacticalIntent("dndturn:" + (weapon == Items.BOW ? "bow" : weapon == Items.CROSSBOW ? "crossbow" : "snowball"), 1, TacticalIntent.Capability.ATTACK, target, cc.sighs.dndturn.combat.AbilitySource.equipment(TacticalIntent.Hand.MAIN_HAND, item(player)));
            var request = new TacticalNetwork.Request(service.generation(), encounter, UUID.randomUUID(), service.state(encounter).version(), intent, false);
            service.tacticalActions().request(player, request);
            helper.assertTrue((weapon == Items.SNOWBALL || player.isUsingItem()) && !service.state(encounter).members().get(player.getUUID()).action(), "ranged request failed to start: " + service.results(encounter, 0, 64).results());
            service.tacticalActions().request(player, request);
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(!service.tacticalActions().running(player.getUUID()), "ranged plan still executing"))
                .thenExecute(() -> {
                    helper.assertTrue((weapon == Items.SNOWBALL ? player.getMainHandItem() : player.getInventory().getItem(9)).getCount() == 3,
                        "bow ammo mismatch: " + service.results(encounter, 0, 64).results());
                    var arrows = level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class, player.getBoundingBox().inflate(8));
                    helper.assertTrue(arrows.size() == 1 && service.isEntitySimulationPaused(arrows.getFirst()), "projectile launch or scheduling failed");
                    service.tacticalActions().request(player, request);
                    helper.assertTrue((weapon == Items.SNOWBALL ? player.getMainHandItem() : player.getInventory().getItem(9)).getCount() == 3, "completed ranged retry consumed ammo");
                    cleanup.run();
                }).thenSucceed();
        } catch (Throwable error) { cleanup.run(); throw error; }
            });
            });
    }
    private static TacticalIntent.ItemReference item(ServerPlayer player) {
        return new TacticalIntent.ItemReference(player.getInventory().getSelectedSlot(), TacticalItems.revision(player, player.getMainHandItem()));
    }
    private static TacticalNetwork.Request request(ServerPlayer player, ServerCombatService service, UUID encounter,
                                                   TacticalIntent.Capability capability, BlockPos pos) {
        var target = new TacticalIntent.Target(TacticalIntent.TargetKind.BLOCK, player.level().dimension().identifier().toString(), null,
            new GridCell(pos.getX(), pos.getY(), pos.getZ()), 1, .5, 1, .5);
        var intent = new TacticalIntent("dndturn:" + switch (capability) { case MOVE -> "move"; case ATTACK -> "melee"; case PLACE -> "place"; case BREAK -> "break"; case USE_BLOCK -> "block"; case USE_ITEM -> "consume"; case EQUIP -> "equip"; }, 1, capability, target, capability == TacticalIntent.Capability.USE_BLOCK ? cc.sighs.dndturn.combat.AbilitySource.basic(TacticalIntent.Hand.MAIN_HAND) : cc.sighs.dndturn.combat.AbilitySource.equipment(TacticalIntent.Hand.MAIN_HAND, item(player)));
        return new TacticalNetwork.Request(service.generation(), encounter, UUID.randomUUID(), service.state(encounter).version(), intent, false);
    }
}
