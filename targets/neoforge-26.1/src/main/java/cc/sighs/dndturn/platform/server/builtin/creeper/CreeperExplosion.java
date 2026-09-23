package cc.sighs.dndturn.platform.server.builtin.creeper;

import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.encounter.operation.WorldOutcomeObservation;
import cc.sighs.dndturn.platform.mixin.server.effect.CreeperEffectAccess;
import cc.sighs.dndturn.platform.observation.VanillaEffectTypes;
import cc.sighs.dndturn.platform.server.action.MinecraftCoordinates;
import cc.sighs.dndturn.platform.server.effect.TriggeredAbilities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** One synchronous, operation-bound native explosion. No tactical damage conversion. */
public final class CreeperExplosion {
    private static final class Frame {
        final TriggeredAbilities.Context context;
        final Creeper source;
        final Set<UUID> targets = new HashSet<>();
        final Map<BlockPos, String> blocks = new LinkedHashMap<>();
        final Map<Entity, net.minecraft.world.phys.Vec3> motions = new IdentityHashMap<>();
        final List<WorldOutcomeObservation.Spawn> spawns = new ArrayList<>();
        final List<WorldOutcomeObservation.Damage> damageObservations = new ArrayList<>();
        DamageSource damage;
        LivingEntity victim;
        int rejectedEntities, rejectedBlocks;
        Frame(TriggeredAbilities.Context context) { this.context = context; source = (Creeper)context.actor().body(); }
    }
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private CreeperExplosion() {}
    static void validate(Creeper source) {
        if (!(source instanceof Creeper) || !(source.level() instanceof ServerLevel level) || !level.getServer().isSameThread())
            throw new IllegalArgumentException("Creeper executor identity");
        int radius = ((CreeperEffectAccess)source).dndturn$explosionRadius() * (source.isPowered() ? 2 : 1);
        if (radius < 1 || radius > 6) throw new IllegalArgumentException("Creeper explosion radius unsupported");
        if (source.getActiveEffects().size() > 32 || source.getActiveEffects().stream().anyMatch(effect -> !VanillaEffectTypes.supported(effect)))
            throw new IllegalStateException("CREEPER_EFFECT_ADAPTER_UNAVAILABLE");
        int extent = radius * 2 + 2;
        BlockPos center = source.blockPosition();
        for (int x = -extent; x <= extent; x += 1) for (int z = -extent; z <= extent; z += 1)
            if (!level.hasChunkAt(center.offset(x, 0, z))) throw new IllegalStateException("explosion dependencies not loaded");
    }
    static OperationRecord.Outcome execute(TriggeredAbilities.Context context) {
        if (CURRENT.get() != null) throw new IllegalStateException("nested explosion needs a separate invocation");
        var frame = new Frame(context); validate(frame.source);
        CURRENT.set(frame);
        try {
            ((CreeperEffectAccess)frame.source).dndturn$explode();
            if (!frame.source.isRemoved()) throw new IllegalStateException("Creeper removal not observed");
            if (frame.rejectedEntities != 0 || frame.rejectedBlocks != 0)
                com.mojang.logging.LogUtils.getLogger().info("CREEPER_EFFECT_SCOPE operation={} rejectedEntities={} rejectedBlocks={}",
                        context.operation().operationId(), frame.rejectedEntities, frame.rejectedBlocks);
            return frame.rejectedEntities != 0 || frame.rejectedBlocks != 0 ? OperationRecord.Outcome.PARTIAL : OperationRecord.Outcome.COMPLETED;
        } finally {
            try {
                var blocks = frame.blocks.entrySet().stream().map(e -> new WorldOutcomeObservation.BlockChange(
                        e.getKey().getX(), e.getKey().getY(), e.getKey().getZ(), e.getValue(), frame.source.level().getBlockState(e.getKey()).toString())).toList();
                var motions = frame.motions.entrySet().stream().map(e -> {
                    var after = e.getKey().getDeltaMovement(); var before = e.getValue();
                    return new WorldOutcomeObservation.Motion(e.getKey().getUUID(), before.x, before.y, before.z, after.x, after.y, after.z);
                }).toList();
                context.service().actorStates().observeInvocation(context.emission().operation(), new WorldOutcomeObservation(
                        blocks, motions, frame.spawns, frame.damageObservations, frame.source.isRemoved(), frame.rejectedEntities, frame.rejectedBlocks));
            } finally { CURRENT.remove(); }
        }
    }
    public static void onDetonate(ExplosionEvent.Detonate event) {
        var frame = CURRENT.get();
        if (frame == null || event.getExplosion().getDirectSourceEntity() != frame.source) return;
        var state = frame.context.engine().stateView(frame.context.operation().encounterId());
        int entities = event.getAffectedEntities().size(), blocks = event.getAffectedBlocks().size();
        if (entities > 256 || blocks > 8192) throw new IllegalStateException("explosion observation budget");
        event.getAffectedEntities().removeIf(e -> !(e instanceof LivingEntity) || !state.members().containsKey(e.getUUID())
                || e.level() != frame.source.level() || !state.region().containsPoint(e.getX(), e.getY(), e.getZ()));
        event.getAffectedEntities().forEach(e -> frame.targets.add(e.getUUID()));
        event.getAffectedEntities().forEach(e -> frame.motions.put(e, e.getDeltaMovement()));
        event.getAffectedBlocks().removeIf(pos -> !state.region().containsPoint(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5)
                || !frame.source.level().hasChunkAt(pos));
        frame.rejectedEntities = entities - event.getAffectedEntities().size();
        frame.rejectedBlocks = blocks - event.getAffectedBlocks().size();
        event.getAffectedBlocks().forEach(pos -> frame.blocks.put(pos.immutable(), frame.source.level().getBlockState(pos).toString()));
    }
    public static void inserted(Entity entity) {
        var frame = CURRENT.get();
        if (frame == null || entity.level() != frame.source.level()) return;
        if (frame.spawns.size() >= 256) throw new IllegalStateException("explosion spawn observation budget");
        if (entity instanceof net.minecraft.world.entity.AreaEffectCloud)
            CreeperClouds.bind((net.minecraft.world.entity.AreaEffectCloud)entity, frame.context);
        frame.spawns.add(new WorldOutcomeObservation.Spawn(entity.getUUID(), net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()));
    }
    static boolean admitSpawn(Entity entity) {
        var frame = CURRENT.get(); if (frame == null) return true;
        boolean allowed = entity.level() == frame.source.level() && frame.spawns.size() < 256
                && (entity instanceof net.minecraft.world.entity.item.ItemEntity
                    || entity instanceof net.minecraft.world.entity.AreaEffectCloud);
        var region = frame.context.engine().stateView(frame.context.operation().encounterId()).region();
        var box = entity.getBoundingBox();
        for (double x : new double[] {box.minX,box.maxX}) for (double y : new double[] {box.minY,box.maxY})
            for (double z : new double[] {box.minZ,box.maxZ})
                allowed &= region.containsPoint(x,y,z) && frame.source.level().hasChunkAt(BlockPos.containing(x,y,z));
        if (!allowed) frame.rejectedEntities++;
        return allowed;
    }
    public static boolean allows(LivingEntity target, DamageSource damage) {
        var frame = CURRENT.get();
        return frame != null && frame.victim == target && frame.damage == damage;
    }
    /** Wrap the virtual call itself, so immunity and specialized early returns remain observable. */
    public static boolean hurt(Entity entity, ServerLevel level, DamageSource source, float amount) {
        var frame = CURRENT.get();
        if (frame == null) return entity.hurtServer(level, source, amount);
        if (!(entity instanceof LivingEntity target) || !frame.targets.contains(entity.getUUID())
                || source.getDirectEntity() != frame.source || entity.level() != level || frame.victim != null)
            throw new IllegalStateException("unbound Creeper damage");
        var engine = frame.context.engine(); var root = frame.context.operation();
        UUID permit = UUID.randomUUID(), operation = UUID.randomUUID();
        engine.issueOutcomePermit(new EncounterAuthority.OutcomePermit(permit, root.encounterId(), root.operationId(), root.owner(),
                Set.of(target.getUUID()), Set.of(EncounterPhase.CANDIDATE, EncounterPhase.ACTIVE), 1, engine.stateView(root.encounterId()).round()));
        var child = new OperationRecord.Snapshot(operation, root.operationId(), root.encounterId(), root.owner(), root.owner(), target.getUUID(),
                frame.context.service().actionHost().planClock(), engine.stateView(root.encounterId()).version(), root.sourceCell(),
                MinecraftCoordinates.cell(target.blockPosition()), OperationRecord.Kind.DAMAGE, frame.context.service().generation());
        float health = target.getHealth(), absorption = target.getAbsorptionAmount();
        boolean admitted = false;
        try {
            if (!engine.beginOperation(child, permit)) throw new IllegalStateException("Creeper damage admission");
            admitted = true; frame.victim = target; frame.damage = source;
            boolean accepted = target.hurtServer(level, source, amount);
            frame.damageObservations.add(new WorldOutcomeObservation.Damage(operation, target.getUUID(), amount, health, target.getHealth(),
                    absorption, target.getAbsorptionAmount(), accepted, true, !target.isAlive()));
            engine.publish(root.encounterId(), operation, 0, OperationRecord.Outcome.COMPLETED,
                    "native explosion accepted=" + accepted + " absorption=" + Math.max(0, absorption - target.getAbsorptionAmount()),
                    0, Math.max(0, health - target.getHealth()), true);
            return accepted;
        } catch (RuntimeException failure) {
            if (admitted && frame.damageObservations.stream().noneMatch(d -> d.operation().equals(operation)))
                frame.damageObservations.add(new WorldOutcomeObservation.Damage(operation, target.getUUID(), amount, health, target.getHealth(),
                        absorption, target.getAbsorptionAmount(), false, false, !target.isAlive()));
            if (admitted && engine.pendingOperation(root.encounterId(), operation) != null)
                engine.publish(root.encounterId(), operation, 0, OperationRecord.Outcome.UNKNOWN, "native explosion damage unknown",
                        0, Math.max(0, health - target.getHealth()), true);
            throw failure;
        } finally { frame.victim = null; frame.damage = null; engine.revokeEffectPermit(root.encounterId(), permit); }
    }
}
