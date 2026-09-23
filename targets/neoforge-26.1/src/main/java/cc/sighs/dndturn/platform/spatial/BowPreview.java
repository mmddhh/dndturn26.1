package cc.sighs.dndturn.platform.spatial;

import cc.sighs.dndturn.domain.action.ActionIntent;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Nominal audited projectile flight. Never creates/ticks an entity or consumes a random source. */
public final class BowPreview {
    public record Flight(List<ActionIntent.Point> points, ActionIntent.Point contact, String status) {
        public String description() {
            return switch (status) {
                case "BLOCK" -> "方块阻挡";
                case "ENTITY" -> "其他实体阻挡";
                case "TARGET_CONTACT" -> "预计接触目标，不保证命中";
                case "UNLOADED" -> "前方未加载，后续轨迹未知";
                case "WATER" -> "预计入水；后续浮漂与鱼获由环境推进";
                case "UNKNOWN_EFFECT" -> "遇到未支持效果，后续轨迹未知";
                default -> "已达预测步数上限，后续轨迹未知";
            };
        }
    }
    public static Flight predict(ServerPlayer player, ActionIntent intent, Vec3 feet, Runnable budget) {
        return predict(player, player.level().getEntity(intent.target().entity()), feet, budget);
    }
    public static Flight predict(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity target, Vec3 feet, Runnable budget) {
        if (target == null) throw new IllegalStateException("target unavailable");
        return predict(player,target,target.getBoundingBox().getCenter(),feet,"dndturn:bow",budget);
    }
    public static Flight predict(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity target,
                                 Vec3 destination, Vec3 feet, String behavior, Runnable budget) {
        var profile=ProjectileProfiles.get(behavior);
        if (profile==null) return new Flight(List.of(),null,"UNKNOWN_EFFECT");
        boolean fishing=behavior.equals("dndturn:fishing_rod");
        var aim = AimGeometry.plannedAim(feet.add(0, player.getEyeHeight(), 0), destination);
        float yaw = aim.yaw() * ((float)Math.PI / 180), pitch = aim.pitch() * ((float)Math.PI / 180);
        // shootFromRotation applies the pitch offset to Y only, then normalizes all three axes.
        Vec3 velocity = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch),
            -Mth.sin(pitch+profile.pitchOffset()*(float)Math.PI/180), Mth.cos(yaw) * Mth.cos(pitch)).normalize().scale(profile.speed());
        // At a future approach point the launch speed is not yet observed; nominally stationary.
        if (!fishing && !behavior.equals("dndturn:crossbow") && feet.distanceToSqr(player.position()) < .0001) {
            var known = player.getKnownMovement(); velocity = velocity.add(known.x, player.onGround() ? 0 : known.y, known.z);
        }
        Vec3 p = feet.add(0, player.getEyeHeight() - .1F, 0);
        if (fishing) {
            Vec3 direction=new Vec3(-Mth.sin(yaw),Mth.clamp(-(Mth.sin(pitch)/Mth.cos(pitch)),-5,5),Mth.cos(yaw));
            velocity=direction.scale(.6/direction.length()+.5);
            p=feet.add(-Mth.sin(yaw)*.3,player.getEyeHeight(),Mth.cos(yaw)*.3);
        }
        var points = new ArrayList<ActionIntent.Point>(); points.add(PreviewPathfinder.value(p));
        boolean leftOwner = false;
        boolean inWater = false; // A new arrow has not run Entity.baseTick/fluid interaction yet.
        for (int age = 0; age < 160; age++) {
            budget.run();
            // ThrowableProjectile applies gravity and drag BEFORE its movement/impact sweep.
            if (profile.throwable()) {
                if (inWater) return new Flight(List.copyOf(points),null,"UNKNOWN_EFFECT");
                velocity=velocity.add(0,-profile.gravity(),0).scale(.99F);
            }
            Vec3 end = p.add(velocity);
            AABB search = new AABB(p.x - .25, p.y, p.z - .25, p.x + .25, p.y + .5, p.z + .25).expandTowards(velocity).inflate(1);
            if (!loaded(player, search)) return new Flight(List.copyOf(points), null, "UNLOADED");
            var state = player.level().getBlockState(BlockPos.containing(p));
            if (fishing && state.getFluidState().is(FluidTags.WATER))
                return new Flight(List.copyOf(points),PreviewPathfinder.value(p),"WATER");
            if (state.is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN)
                || state.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                || state.is(net.minecraft.world.level.block.Blocks.END_PORTAL))
                return new Flight(List.copyOf(points), null, "UNKNOWN_EFFECT");
            for (var box : state.getCollisionShape(player.level(), BlockPos.containing(p)).toAabbs())
                if (box.move(BlockPos.containing(p)).contains(p)) return new Flight(List.copyOf(points), PreviewPathfinder.value(p), "BLOCK");
            boolean waterBefore = inWater;
            Vec3 nextVelocity = waterBefore ? velocity.scale(.6F) : velocity;
            var block = player.level().clipIncludingBorder(new ClipContext(p, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (unknownEffects(player, p, block.getLocation(), budget)) return new Flight(List.copyOf(points), null, "UNKNOWN_EFFECT");
            leftOwner |= player.getRootVehicle().getSelfAndPassengers().noneMatch(e -> search.intersects(e.getBoundingBox()));
            final boolean outside = leftOwner;
            var hits = new ArrayList<>(ProjectileUtil.getManyEntityHitResult(player.level(), player, p, block.getLocation(), search,
                e -> e.canBeHitByProjectile() && (outside || !player.isPassengerOfSameVehicle(e))
                    && (!(e instanceof net.minecraft.world.entity.player.Player other) || player.canHarmPlayer(other)),
                Math.max(0, Math.min(.3F, (age - 2) / 20F)), ClipContext.Block.COLLIDER, false));
            Vec3 origin = p;
            hits.sort(Comparator.comparingDouble(h -> origin.distanceToSqr(h.getEntity().position())));
            if (!hits.isEmpty()) {
                var hit = hits.getFirst(); points.add(PreviewPathfinder.value(hit.getLocation()));
                return new Flight(List.copyOf(points), PreviewPathfinder.value(hit.getLocation()), hit.getEntity() == target ? "TARGET_CONTACT" : "ENTITY");
            }
            if (block.getType() != HitResult.Type.MISS) {
                points.add(PreviewPathfinder.value(block.getLocation()));
                return new Flight(List.copyOf(points), PreviewPathfinder.value(block.getLocation()), "BLOCK");
            }
            if (fishing) {
                // FLYING checks its pre-gravity ray, then moves with gravity and damps by .92.
                velocity=velocity.add(0,-.03,0);
                p=p.add(velocity); velocity=velocity.scale(.92);
                points.add(PreviewPathfinder.value(p));
                continue;
            }
            p = end; points.add(PreviewPathfinder.value(p));
            // isInWater is still the previous baseTick's observation at this point in AbstractArrow.tick.
            if (!profile.throwable()) {
                if (!waterBefore) nextVelocity = nextVelocity.scale(.99F);
                velocity = nextVelocity.add(0, -profile.gravity(), 0);
            }
            var fluid = fluid(player, p, budget); inWater = fluid.height > 0;
            if (fluid.flow.lengthSqr() >= 1.0E-5F) velocity = velocity.add(fluid.flow.normalize().scale(.014));
        }
        return new Flight(List.copyOf(points), null, "HORIZON");
    }
    private record Water(double height, Vec3 flow) {}
    /** Fixed .84 EntityFluidInteraction's non-player water tracker, without entity callbacks. */
    private static Water fluid(net.minecraft.world.entity.player.Player player, Vec3 p, Runnable budget) {
        AABB box = new AABB(p.x-.25,p.y,p.z-.25,p.x+.25,p.y+.5,p.z+.25).deflate(.001);
        double height = 0; Vec3 flow = Vec3.ZERO;
        for (int x=(int)Math.floor(box.minX); x<Math.ceil(box.maxX); x++)
            for (int y=(int)Math.floor(box.minY); y<Math.ceil(box.maxY); y++)
                for (int z=(int)Math.floor(box.minZ); z<Math.ceil(box.maxZ); z++) {
                    budget.run(); var pos = new BlockPos(x,y,z); var f = player.level().getFluidState(pos);
                    if (!f.is(FluidTags.WATER)) continue;
                    double top = y + f.getHeight(player.level(),pos);
                    if (top < box.minY) continue;
                    height = Math.max(height,top-p.y); Vec3 v=f.getFlow(player.level(),pos);
                    flow=flow.add(height<.4 ? v.scale(height) : v);
                }
        return new Water(height,flow);
    }
    private static boolean unknownEffects(net.minecraft.world.entity.player.Player player, Vec3 from, Vec3 to, Runnable budget) {
        var box = new AABB(from,to).inflate(.25);
        for (var p : BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))) {
            budget.run(); var state=player.level().getBlockState(p);
            var blocks=net.minecraft.core.registries.BuiltInRegistries.BLOCK;
            if (!blocks.getKey(state.getBlock()).getNamespace().equals("minecraft")
                || state.is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN) || state.is(net.minecraft.world.level.block.Blocks.COBWEB)
                || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW) || state.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                || state.is(net.minecraft.world.level.block.Blocks.END_PORTAL) || state.is(net.minecraft.world.level.block.Blocks.END_GATEWAY)
                || state.getFluidState().is(FluidTags.LAVA)) return true;
        }
        return false;
    }
    private static boolean loaded(net.minecraft.world.entity.player.Player p, AABB box) {
        for (int x = (int)Math.floor(box.minX / 16); x <= (int)Math.floor(box.maxX / 16); x++)
            for (int z = (int)Math.floor(box.minZ / 16); z <= (int)Math.floor(box.maxZ / 16); z++)
                if (!p.level().hasChunkAt(new BlockPos(x * 16, 0, z * 16))) return false;
        return true;
    }
}
