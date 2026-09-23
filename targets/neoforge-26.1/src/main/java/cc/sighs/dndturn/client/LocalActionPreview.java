package cc.sighs.dndturn.client;

import cc.sighs.dndturn.combat.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Client-thread only. No network, server registry, item execution, or resource mutation. */
final class LocalActionPreview {
    record Snapshot(UUID query, TacticalIntent intent, List<TacticalNetwork.Candidate> candidates,
                    List<TacticalNetwork.PreviewStep> route, List<TacticalIntent.Point> trajectory,
                    TacticalIntent.Point contact, String status, String reason, double reachableDistance) {}
    final UUID id=UUID.randomUUID();
    final TacticalIntent.Target target;
    final Vec3 requested;
    final Entity entity;
    final Vec3 entityPosition;
    final String behavior;
    final TacticalIntent baseIntent;
    final PreviewPathfinder finder;
    private final String posture;
    private final int movementBudget;
    private Vec3 feet;
    private boolean done;
    private Snapshot snapshot;
    private int work;
    private int age;
    private int validationCursor;
    private static final class Budget extends RuntimeException {
        Budget() { super(null,null,false,false); }
    }
    LocalActionPreview(String behavior, TacticalIntent.Target target, Vec3 desired, Entity entity, TacticalNetwork.Ability ability, LocalActionPreview previous) {
        var p=Minecraft.getInstance().player;
        posture=PreviewPathfinder.posture(p);
        movementBudget=ClientCombatState.encounter().movementTicks();
        this.behavior=behavior; this.target=target; this.requested=desired; this.entity=entity;
        entityPosition=entity==null?null:entity.position();
        baseIntent=behavior.equals("dndturn:move") ? new TacticalIntent(behavior,1,TacticalIntent.Capability.MOVE,target,AbilitySource.basic())
            : ability==null || ability.source()==null ? null : new TacticalIntent(behavior,ability.version(),ability.cost(),target,ability.source());
        finder=new PreviewPathfinder(p,null,() -> { if (++work>8000) throw new Budget(); });
        try {
            if (desired!=null) {
                feet=finder.surfaceAt(desired);
                if (feet==null || !canExecute(feet)) { fail("鼠标位置无法站立或执行此行动"); return; }
                var direct=finder.directTo(feet);
                if (direct!=null) { complete(direct); return; }
                if (previous!=null && previous.done && previous.snapshot!=null && previous.requested!=null
                    && previous.behavior.equals(behavior) && previous.target.equals(target)
                    && PreviewPathfinder.key(previous.requested).equals(PreviewPathfinder.key(feet))
                    && !previous.snapshot.route().isEmpty()) {
                    var reused=finder.retarget(previous.snapshot.route(),feet);
                    if (reused!=null) { complete(reused); return; }
                }
                finder.destination(feet);
            } else {
                // Hovering the entity itself asks for an approach. Clicking ground never substitutes a sample.
                var goals=new LinkedHashSet<GridCell>();
                if (canExecute(p.position())) { feet=p.position(); complete(List.of()); return; }
                Vec3 center=entity!=null?entity.position():new Vec3(target.cell().x()+.5,target.cell().y(),target.cell().z()+.5);
                for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) for (int y=-1;y<=1;y++) {
                    var cell=new GridCell((int)Math.floor(center.x)+x,(int)Math.ceil(center.y)+y,(int)Math.floor(center.z)+z);
                    Vec3 f=finder.surface(cell);
                    if (f!=null && canExecute(f)) goals.add(cell);
                }
                finder.begin(goals);
            }
        } catch (Budget exhausted) { fail("本地预览工作预算已用尽"); }
    }
    boolean canExecute(Vec3 f) {
        var p=Minecraft.getInstance().player;
        if (behavior.equals("dndturn:move")) return true;
        Vec3 eye=f.add(0,p.getEyeHeight(),0), end;
        if (entity!=null) {
            if (p.getDimensions(p.getPose()).makeBoundingBox(f.x,f.y,f.z).intersects(entity.getBoundingBox())) return false;
            if (behavior.equals("dndturn:intrinsic_melee") || behavior.equals("dndturn:melee")) {
                if (new GridCell((int)Math.floor(f.x),(int)Math.floor(f.y),(int)Math.floor(f.z))
                    .chebyshev(new GridCell(entity.blockPosition().getX(),entity.blockPosition().getY(),entity.blockPosition().getZ()))>1) return false;
            } else if (!ProjectileProfiles.contains(behavior) && !"dndturn:entity_item".equals(behavior)) return false;
            double reach=ProjectileProfiles.contains(behavior)?6:3;
            if (f.distanceToSqr(entity.position())>reach*reach) return false;
            end=entity.getEyePosition();
        } else {
            end=new Vec3(target.cell().x()+target.x(),target.cell().y()+target.y(),target.cell().z()+target.z());
            double range=ProjectileProfiles.contains(behavior)?6:p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
            if (eye.distanceToSqr(end)>range*range) return false;
        }
        var hit=p.level().clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        return hit.getType()==HitResult.Type.MISS || target.kind()==TacticalIntent.TargetKind.BLOCK
            && hit.getBlockPos().getX()==target.cell().x() && hit.getBlockPos().getY()==target.cell().y() && hit.getBlockPos().getZ()==target.cell().z();
    }
    Snapshot advance() {
        if (done) return snapshot;
        work=0;
        try {
            if (++age>80) { fail("本地路径搜索达到预算上限"); return snapshot; }
            if (finder.advance(16)) {
                var route=feet==null?finder.result():finder.finishAt(feet);
                if (route==null) fail("鼠标位置没有可用路径");
                else { if (feet==null) feet=route.isEmpty()?Minecraft.getInstance().player.position():PreviewPathfinder.vector(route.getLast().feet()); complete(route); }
            }
        } catch (Budget exhausted) { fail("本地路径搜索达到工作预算上限"); }
        return snapshot;
    }
    private void complete(List<TacticalNetwork.PreviewStep> route) {
        var player=Minecraft.getInstance().player;
        var limit=MovementPreviewBudget.limit(player.position(),route,movementBudget,
            player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
        Vec3 endpoint=feet;
        var proposal=baseIntent;
        boolean clippedMove=behavior.equals("dndturn:move") && limit.exceeded();
        if (clippedMove) {
            endpoint=limit.stop();
            // Submit the visible affordable endpoint, not the red destination. The server
            // independently checks support, collision, membership and the live budget.
            proposal=new TacticalIntent(behavior,1,TacticalIntent.Capability.MOVE,
                new TacticalIntent.Target(TacticalIntent.TargetKind.GROUND,target.dimension(),null,
                    PreviewPathfinder.key(endpoint),-1,0,0,0),AbilitySource.basic());
        }
        var intent=proposal==null?null:proposal.withApproach(new TacticalIntent.Approach(id,0,PreviewPathfinder.value(endpoint)));
        if (clippedMove && limit.route().isEmpty()) intent=null;
        String reason=clippedMove ? intent==null?"剩余移动预算不足以到达下一个可站立位置":"红色部分超出预计移动预算；请求移动至灰色终点"
            : limit.exceeded()?"接近路径超出预计移动预算":"本地预览";
        snapshot=new Snapshot(id,intent,List.of(new TacticalNetwork.Candidate(0,PreviewPathfinder.value(endpoint))),route,List.of(),null,"PATH",
            reason+"；提交后由服务器复验，费用按实际移动结算",limit.distance());
        done=true;
        refreshFlight();
    }
    void refreshFlight() {
        if (!done || snapshot==null || feet==null || !ProjectileProfiles.contains(behavior)) return;
        work=0;
        try {
            var player=Minecraft.getInstance().player;
            Vec3 destination=entity==null ? new Vec3(target.cell().x()+target.x(),target.cell().y()+target.y(),target.cell().z()+target.z()) : entity.getBoundingBox().getCenter();
            var flight=BowPreview.predict(player,entity,destination,player.isUsingItem()?player.position():feet,behavior,() -> { if (++work>8000) throw new Budget(); });
            snapshot=new Snapshot(id,snapshot.intent(),snapshot.candidates(),snapshot.route(),flight.points(),flight.contact(),flight.status(),"本地发射中心轨迹（实际保留散布）；"+flight.description(),snapshot.reachableDistance());
        } catch (Budget exhausted) { snapshot=new Snapshot(id,snapshot.intent(),snapshot.candidates(),snapshot.route(),List.of(),null,"UNKNOWN","弹道预测达到预算上限",snapshot.reachableDistance()); }
    }
    Snapshot snapshot() { return snapshot; }
    boolean done() { return done; }
    /** Refresh a bounded slice of world dependencies; never rerun the whole search on a timer. */
    boolean stillValid() {
        if (ClientCombatState.encounter()==null || movementBudget!=ClientCombatState.encounter().movementTicks()) return false;
        if (!posture.equals(PreviewPathfinder.posture(Minecraft.getInstance().player))) return false;
        if (finder.dependenciesChanged(64)) return false;
        if (!done || snapshot==null || snapshot.route().isEmpty()) return true;
        var player=Minecraft.getInstance().player;
        work=0;
        var fresh=new PreviewPathfinder(player,null,() -> { if (++work>8000) throw new Budget(); });
        try {
            for (int n=0;n<2;n++) {
                int i=validationCursor++ % snapshot.route().size();
                Vec3 from=i==0?player.position():PreviewPathfinder.vector(snapshot.route().get(i-1).feet());
                if (!fresh.checkSegment(from,PreviewPathfinder.vector(snapshot.route().get(i).feet()))) return false;
            }
            return canExecute(feet);
        } catch (Budget exhausted) { return false; }
    }
    private void fail(String reason) { done=true; snapshot=new Snapshot(id,null,List.of(),List.of(),List.of(),null,"UNAVAILABLE",reason,0); }
}
