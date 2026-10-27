package cc.sighs.dndturn.platform.server.projectile;
import cc.sighs.dndturn.domain.encounter.StateView;
import cc.sighs.dndturn.platform.server.action.AbilityExecutors;
import cc.sighs.dndturn.platform.server.damage.DamageResolution;
import static cc.sighs.dndturn.platform.server.damage.DamageResolution.*;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.EncounterRegion;
import cc.sighs.dndturn.domain.encounter.operation.DamageTrace;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.encounter.operation.ProjectileOrigin;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import cc.sighs.dndturn.domain.resolution.CombatRules;
import cc.sighs.dndturn.domain.resolution.RuleResolver;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.diagnostics.DebugDiagnostics;
import cc.sighs.dndturn.platform.spatial.MinecraftCoordinates;
import cc.sighs.dndturn.platform.server.entity.MinecraftProjectiles;
import cc.sighs.dndturn.platform.server.action.TacticalLaunchContext;
import cc.sighs.dndturn.platform.server.actor.ActorStateAuthority;
import cc.sighs.dndturn.platform.server.control.SimulationPolicy;
import cc.sighs.dndturn.platform.server.damage.TacticalDamageContext;
import cc.sighs.dndturn.platform.server.persistence.CombatPersistenceEnvelope;
import cc.sighs.dndturn.platform.server.persistence.EncounterPersistence;
import cc.sighs.dndturn.platform.server.world.EnvironmentScheduler;
import cc.sighs.dndturn.platform.server.control.SimulationPreparation;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Owns launch evidence, simulation leases, pending contacts, quarantine and their recovery. */
public final class ProjectileExecutionCoordinator {
    public interface Host {
        cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectRoundController.ApplicationScope applications(java.util.function.Supplier<cc.sighs.dndturn.domain.encounter.operation.OperationRecord.Snapshot> root, java.util.List<LivingEntity> targets);
        cc.sighs.dndturn.platform.server.damage.DamageAttempt damageAttempt();
        long clock();
        Map<UUID, EncounterRegion> regions();
        boolean knockback(UUID encounter);
        void enterOutcome();
        void exitOutcome();
        void flushOutcome();
        OperationRecord.Snapshot operationSnapshot(UUID operationId, UUID parentId, UUID encounterId,
            UUID owner, UUID source, UUID target, long tick, long version, GridCell sourceCell,
            GridCell targetCell, OperationRecord.Kind kind);
        void sync(StateView state);
        UUID encounterAtBlock(ServerLevel level, BlockPos pos);
        boolean isEntityInsidePausedRegion(Entity entity);
        void queueMerge(EncounterAuthority.MergePlan plan);
        Entity resolve(UUID id);
    }
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftServer server;
    private final EncounterAuthority engine;
    private final UUID generation;
    private final ActorStateAuthority actorStates;
    private final EnvironmentScheduler environment;
    private final EncounterPersistence persistence;
    private final DamageResolution damageResolution;
    private final Host host;
    public ProjectileExecutionCoordinator(MinecraftServer server, EncounterAuthority engine, UUID generation,
            ActorStateAuthority actors, EnvironmentScheduler environment, EncounterPersistence persistence,
            DamageResolution damageResolution, Host host) {
        this.server = server; this.engine = engine; this.generation = generation; this.actorStates = actors;
        this.environment = environment; this.persistence = persistence; this.damageResolution = damageResolution; this.host = host;
    }

    private final Map<UUID, CombatPersistenceEnvelope.ProjectileAbility> projectileAbilities = new HashMap<>();
    /** Value evidence survives departure of the owner; simulation domain is a scheduler projection. */
    private final Map<UUID, ProjectileOrigin> projectileOrigins = new HashMap<>();
    private final Map<UUID, UUID> projectileSimulationDomains = new HashMap<>();
    private final Set<UUID> retainedProjectileHistory = new HashSet<>();
    /** A verified collision waits here until its source and target domains merge. */
    private record PendingProjectileAttack(UUID targetId, UUID sourceEncounterId) {}
    private final Map<UUID, PendingProjectileAttack> pendingProjectileAttacks = new HashMap<>();
    private final Map<UUID, CombatPersistenceEnvelope.QuarantinedProjectile> quarantinedProjectiles = new HashMap<>();

    /** Capture the shooter while it is still resolvable; reloading never overwrites old evidence. */
    public void captureArrowOrigin(AbstractArrow arrow, boolean loadedFromDisk) {
        requireThread();
        if (loadedFromDisk || !(arrow.level() instanceof ServerLevel level)) return;
        Entity owner = arrow.getOwner();
        ItemStack weapon = arrow.getWeaponItem();
        String weaponId = weapon == null || weapon.isEmpty() ? ""
            : BuiltInRegistries.ITEM.getKey(weapon.getItem()).toString();
        ItemStack ammunition = arrow.getPickupItemStackOrigin();
        String ammoId = ammunition.isEmpty() ? ""
            : BuiltInRegistries.ITEM.getKey(ammunition.getItem()).toString();
        projectileOrigins.putIfAbsent(arrow.getUUID(), new ProjectileOrigin(arrow.getUUID(),
            owner == null ? null : owner.getUUID(), TacticalLaunchContext.current() == null ? null : TacticalLaunchContext.current().operation(),
            owner == null ? null : engine.encounterOf(owner.getUUID()),
            level.dimension().identifier().toString(), host.clock(),
            MinecraftProjectiles.arrowBaseDamage(arrow), weaponId, ammoId,
            owner instanceof ServerPlayer, true,
            weapon != null && weapon.getOrDefault(DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).size() > 0,
            TacticalLaunchContext.current() == null ? null : TacticalLaunchContext.current().trace()));
        if (TacticalLaunchContext.current() != null) {
            var launch = TacticalLaunchContext.current();
            if (owner == null || !launch.owner().equals(owner.getUUID())) throw new IllegalStateException("launch owner mismatch");
            TacticalLaunchContext.observe(arrow);
            if (launch.invocation() != null) projectileAbilities.put(arrow.getUUID(),
                new CombatPersistenceEnvelope.ProjectileAbility(launch.root(), launch.invocation()));
            projectileSimulationDomains.put(arrow.getUUID(), TacticalLaunchContext.current().encounter());
            retainedProjectileHistory.add(arrow.getUUID());
        }
        persistence.changed();
    }

    public void captureSnowballOrigin(net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball ball, boolean loaded) {
        var launch = TacticalLaunchContext.current();
        if (loaded || launch == null || ball.getOwner() == null || !launch.owner().equals(ball.getOwner().getUUID())) return;
        projectileOrigins.put(ball.getUUID(), new ProjectileOrigin(ball.getUUID(), launch.owner(), launch.operation(), launch.encounter(),
            ball.level().dimension().identifier().toString(), host.clock(), 0, "minecraft:snowball", "minecraft:snowball", true, true, false, launch.trace()));
        if (launch.invocation() != null) projectileAbilities.put(ball.getUUID(),
            new CombatPersistenceEnvelope.ProjectileAbility(launch.root(), launch.invocation()));
        projectileSimulationDomains.put(ball.getUUID(), launch.encounter());
        retainedProjectileHistory.add(ball.getUUID());
        persistence.changed();
    }
    public void captureItemProjectile(net.minecraft.world.entity.projectile.Projectile projectile, boolean loaded) {
        var launch=TacticalLaunchContext.current();
        if (loaded || launch==null || !MinecraftProjectiles.matches(projectile,launch.invocation())
            || projectile.getOwner()==null || !launch.owner().equals(projectile.getOwner().getUUID())) return;
        String item=projectile instanceof net.minecraft.world.entity.projectile.FishingHook ? "minecraft:fishing_rod"
            : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                ((net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile)projectile).getItem().getItem()).toString();
        projectileOrigins.put(projectile.getUUID(),new ProjectileOrigin(projectile.getUUID(),launch.owner(),launch.operation(),launch.encounter(),
            projectile.level().dimension().identifier().toString(),host.clock(),0,item,item,true,true,false,null));
        projectileAbilities.put(projectile.getUUID(),new CombatPersistenceEnvelope.ProjectileAbility(launch.root(),launch.invocation()));
        projectileSimulationDomains.put(projectile.getUUID(),launch.encounter()); retainedProjectileHistory.add(projectile.getUUID());
        persistence.changed();
    }
    public boolean ownsItemProjectile(ServerPlayer player,net.minecraft.world.entity.projectile.Projectile projectile) {
        var origin=projectileOrigins.get(projectile.getUUID());
        return origin!=null && origin.launchVerified() && player.getUUID().equals(origin.ownerId())
            && projectileAbilities.containsKey(projectile.getUUID()) && !quarantinedProjectiles.containsKey(projectile.getUUID());
    }
    public net.minecraft.world.entity.projectile.ProjectileDeflection itemProjectileImpact(
        net.minecraft.world.entity.projectile.Projectile projectile,HitResult hit,
        java.util.function.Supplier<net.minecraft.world.entity.projectile.ProjectileDeflection> original) {
        if (!MinecraftProjectiles.supported(projectile) || !projectileOrigins.containsKey(projectile.getUUID())) return original.get();
        var none=net.minecraft.world.entity.projectile.ProjectileDeflection.NONE;
        if (quarantinedProjectiles.containsKey(projectile.getUUID())) return none;
        UUID domain=liveProjectileDomain(projectile.getUUID());
        if (domain==null) return original.get();
        if (!domain.equals(environment.domain(projectile.level()))) return none;
        var origin=projectileOrigins.get(projectile.getUUID());
        UUID target=hit instanceof EntityHitResult eh?eh.getEntity().getUUID():null;
        UUID operation=arrowOperationId(projectile.getUUID(),target);
        if (engine.resultFor(domain,operation)!=null) return none;
        var owner=server.getPlayerList().getPlayer(origin.ownerId());
        boolean admitted=false;
        host.enterOutcome();
        try {
            if (owner==null || owner.level()!=projectile.level()) throw new IllegalStateException("item effect owner unavailable");
            double radius=projectile instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion?4.5:2;
            var contact=hit.getLocation();
            for (var pos:BlockPos.betweenClosed(BlockPos.containing(contact.x-radius,contact.y-2,contact.z-radius),
                BlockPos.containing(contact.x+radius,contact.y+3,contact.z+radius))) {
                if (!projectile.level().hasChunkAt(pos) || !domain.equals(host.encounterAtBlock((ServerLevel)projectile.level(),pos))
                    || !projectile.level().mayInteract(owner,pos) || server.isUnderSpawnProtection((ServerLevel)projectile.level(),pos,owner))
                    throw new IllegalStateException("item impact footprint outside permitted loaded domain");
            }
            if (hit instanceof net.minecraft.world.phys.BlockHitResult bh
                && !MinecraftProjectiles.inert(projectile.level().getBlockState(bh.getBlockPos())))
                throw new IllegalStateException("projectile block callback requires an adapter");
            if (target!=null && !(projectile.level().getEntity(target) instanceof LivingEntity)
                && !(projectile instanceof net.minecraft.world.entity.projectile.FishingHook
                    && projectile.level().getEntity(target) instanceof net.minecraft.world.entity.item.ItemEntity))
                throw new IllegalStateException("projectile deflection target unsupported");
            var affected=projectile.level().getEntitiesOfClass(LivingEntity.class,
                projectile.getBoundingBox().move(contact.subtract(projectile.position())).inflate(radius,2,radius));
            if (affected.size()>32) throw new IllegalStateException("potion target budget exceeded");
            for (var body:affected) if (projectile instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion
                && (!domain.equals(engine.encounterOf(body.getUUID())) || body instanceof ServerPlayer && body!=owner))
                throw new IllegalStateException("potion area includes an unauthorized target");
            var snapshot=new OperationRecord.Snapshot(operation,origin.rootOperationId(),domain,origin.ownerId(),projectile.getUUID(),target,
                host.clock(),engine.stateView(domain).version(),MinecraftCoordinates.cell(projectile.blockPosition()),MinecraftCoordinates.cell(BlockPos.containing(contact)),
                OperationRecord.Kind.ENVIRONMENT,generation);
            if (!engine.beginCausalItemImpact(origin,snapshot)) throw new IllegalStateException("causal item effect admission denied");
            admitted=true;
            net.minecraft.world.entity.projectile.ProjectileDeflection result;
            try (var applications=host.applications(() -> snapshot,affected)) { result=original.get(); }
            engine.publish(domain,operation,0,OperationRecord.Outcome.COMPLETED,"native item impact completed",0,0,true);
            return result;
        } catch(RuntimeException failure) {
            if (admitted && engine.pendingOperation(domain,operation)!=null)
                engine.publish(domain,operation,0,OperationRecord.Outcome.UNKNOWN,"item impact outcome uncertain",0,0,true);
            else if (!admitted) engine.rejectCausalProjectileEffect(domain,operation,origin.ownerId(),projectile.getUUID(),target,host.clock(),failure.getMessage());
            quarantineProjectile(projectile.getUUID(),domain,target,"item impact requires reconciliation: "+failure.getMessage());
            return none;
        } finally {
            host.exitOutcome();
            host.flushOutcome();
        }
    }
    public boolean handleSnowballImpact(net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball ball, HitResult hit) {
        var origin = projectileOrigins.get(ball.getUUID());
        UUID domain = liveProjectileDomain(ball.getUUID());
        if (origin == null || domain == null) return host.isEntityInsidePausedRegion(ball);
        if (!domain.equals(environment.domain(ball.level()))) return true;
        UUID target = hit instanceof EntityHitResult entityHit ? entityHit.getEntity().getUUID() : null;
        UUID operation = arrowOperationId(ball.getUUID(), target);
        if (engine.resultFor(domain, operation) != null) { ball.discard(); return true; }
        var state = engine.stateView(domain);
        var selected = origin.launchTrace();
        Entity contacted = target == null ? null : ball.level().getEntity(target);
        if (target == null) {
            engine.recordCausalProjectileContact(domain, operation, origin.ownerId(), ball.getUUID(), null,
                host.clock(), OperationRecord.Outcome.COMPLETED, "snowball hit block");
        } else if (selected == null || !selected.targetId().equals(target)
            || !(contacted instanceof LivingEntity)
            || !domain.equals(engine.encounterOf(target))) {
            engine.rejectCausalProjectileEffect(domain, operation, origin.ownerId(), ball.getUUID(), target,
                host.clock(), "snowball contact outside selected supported target");
        } else {
            var snapshot = host.operationSnapshot(operation, null, domain, origin.ownerId(), ball.getUUID(), target,
                host.clock(), state.version(), null, null, OperationRecord.Kind.ATTACK);
            if (!engine.beginCausalProjectileAttack(origin, snapshot)) return true;
            var trace = new DamageTrace(operation, target, selected.rollMode(), selected.firstDie(), selected.secondDie(),
                selected.selectedDie(), selected.rollTotal(), selected.targetArmorClass(), selected.hit(), selected.critical(),
                0, selected.toughnessReduction(), 0, false, 0, 0,
                damageEvidence(ball, state, selected.hit() ? DamageTrace.Stage.ZERO_DAMAGE : DamageTrace.Stage.MISS, null, List.of()));
            engine.publish(domain, operation, 0, OperationRecord.Outcome.COMPLETED,
                selected.hit() ? "snowball hit: vanilla base damage is zero for supported target" : "snowball missed",
                0, 0, true, trace);
        }
        ball.discard();
        host.sync(engine.stateView(domain));
        return true;
    }

    public void noteArrowLeave(Entity arrow) {
        requireThread();
        UUID projectileId = arrow.getUUID();
        Entity.RemovalReason reason = arrow.getRemovalReason();
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
            || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) return;
        UUID recorded = projectileSimulationDomains.remove(projectileId);
        PendingProjectileAttack waiting = pendingProjectileAttacks.remove(projectileId);
        if (recorded != null || waiting != null) persistence.changed();
        if (waiting != null) {
            // The collision target can still belong to B while the arrow is simulated by A.
            // Removing the arrow before the merge must settle that pending collision in B.
            UUID encounterId = engine.encounterOf(waiting.targetId());
            if (encounterId != null && engine.encounterIds().contains(encounterId)) {
                ProjectileOrigin origin = projectileOrigins.get(projectileId);
                UUID owner = origin == null || origin.ownerId() == null
                    ? projectileId : origin.ownerId();
                engine.recordRestoredUnknown(encounterId,
                    arrowOperationId(projectileId, waiting.targetId()), owner,
                    projectileId, waiting.targetId(), host.clock(),
                    "projectile removed before pending cross-domain collision settled");
                host.sync(engine.stateView(encounterId));
            }
        }
        // A revoked scheduler lease is not evidence that the arrow never participated.
        // Launch history is retained for causal retries and reference-aware archival.
        ProjectileOrigin origin = projectileOrigins.get(projectileId);
        if (recorded == null && !retainedProjectileHistory.contains(projectileId)
            && (origin == null || origin.sourceEncounterId() == null)) {
            if (projectileOrigins.remove(projectileId) != null) persistence.changed();
        }
    }

    /** Scheduling is revocable; immutable launch evidence is retained independently. */
    public UUID liveProjectileDomain(UUID projectileId) {
        UUID recorded = projectileSimulationDomains.get(projectileId);
        if (recorded == null) return null;
        UUID canonical = engine.canonicalEncounterId(recorded);
        if (!engine.encounterIds().contains(canonical)) {
            projectileSimulationDomains.remove(projectileId);
            persistence.changed();
            return null;
        }
        if (!canonical.equals(recorded)) {
            projectileSimulationDomains.put(projectileId, canonical);
            persistence.changed();
        }
        return canonical;
    }

    public void revokeEndedProjectileDomains() {
        for (UUID projectileId : Set.copyOf(projectileSimulationDomains.keySet()))
            liveProjectileDomain(projectileId);
    }

    /** Called by the fixed-version entity gate before an arrow's collision or despawn logic. */
    public SimulationPreparation prepareArrowSimulation(AbstractArrow arrow) {
        if (quarantinedProjectiles.containsKey(arrow.getUUID())) return SimulationPreparation.quarantined();
        if (!(arrow.level() instanceof ServerLevel level)) return SimulationPreparation.advance();
        UUID projectileId = arrow.getUUID();
        UUID domain = liveProjectileDomain(projectileId);
        if (domain == null && !host.regions().isEmpty()) {
            Vec3 start = arrow.position();
            Vec3 end = start.add(arrow.getDeltaMovement());
            EncounterRegion.Point from = new EncounterRegion.Point(start.x, start.y, start.z);
            EncounterRegion.Point to = new EncounterRegion.Point(end.x, end.y, end.z);
            double first = Double.POSITIVE_INFINITY;
            String dimension = level.dimension().identifier().toString();
            for (var entry : host.regions().entrySet()) {
                EncounterRegion region = entry.getValue();
                if (!region.dimension().equals(dimension)) continue;
                double hit = region.firstIntersectionFraction(from, to);
                if (hit < first || hit == first && domain != null
                    && entry.getKey().compareTo(domain) < 0) {
                    first = hit;
                    domain = entry.getKey();
                }
            }
            if (domain != null) {
                projectileSimulationDomains.put(projectileId, domain);
                retainedProjectileHistory.add(projectileId);
                persistence.changed();
            }
        }
        if (domain == null) return SimulationPreparation.advance();
        UUID canonical = engine.canonicalEncounterId(domain);
        if (!engine.encounterIds().contains(canonical)) return SimulationPreparation.advance();
        if (!domain.equals(canonical)) projectileSimulationDomains.put(projectileId, canonical);
        PendingProjectileAttack waiting = pendingProjectileAttacks.get(projectileId);
        if (waiting != null) {
            UUID sourceCanonical = engine.canonicalEncounterId(waiting.sourceEncounterId());
            UUID targetDomain = engine.encounterOf(waiting.targetId());
            Entity pendingTarget = level.getEntity(waiting.targetId());
            if (targetDomain == null || !(pendingTarget instanceof LivingEntity living)
                || living.isDeadOrDying()
                || !living.getBoundingBox().inflate(0.3).contains(arrow.position())) {
                pendingProjectileAttacks.remove(projectileId);
                persistence.changed();
                rejectArrowTransit(arrow, canonical, "pending-target-unavailable",
                    "pending arrow collision no longer has a live member at the observed impact");
                return SimulationPreparation.ended();
            }
            if (targetDomain != null
                && (targetDomain.equals(sourceCanonical) || !engine.encounterIds().contains(sourceCanonical))
                && targetDomain.equals(environment.domain(level))) {
                Entity target = pendingTarget;
                if (target != null) {
                    projectileSimulationDomains.put(projectileId, targetDomain);
                    pendingProjectileAttacks.remove(projectileId);
                    persistence.changed();
                    handleArrowImpact(arrow, new EntityHitResult(target));
                    if (arrow.isRemoved()) return SimulationPreparation.ended();
                    if (quarantinedProjectiles.containsKey(projectileId)) return SimulationPreparation.quarantined();
                    return new SimulationPreparation(SimulationPreparation.Status.CONTACT_HANDLED, "PROJECTILE_CONTACT_HANDLED");
                }
            }
            return new SimulationPreparation(SimulationPreparation.Status.HELD, "PROJECTILE_CONTACT_PENDING");
        }
        // Pure scheduling decision after the owner has completed domain/contact reconciliation.
        // The old return was exactly !canonical.equals(environment.domain(level)).
        return SimulationPreparation.scheduling(SimulationPolicy.entity(new SimulationPolicy.EntityFacts(false, true,
            !canonical.equals(environment.domain(level)), false)));
    }

    /** Called before the fixed-version arrow path applies block/fluid effects. */
    public boolean allowArrowBlockEffects(AbstractArrow arrow, Vec3 from, Vec3 to) {
        requireThread();
        if (quarantinedProjectiles.containsKey(arrow.getUUID())) return false;
        if (!(arrow.level() instanceof ServerLevel level)) return true;
        UUID recorded = liveProjectileDomain(arrow.getUUID());
        if (recorded == null) return true;
        UUID encounterId = engine.canonicalEncounterId(recorded);
        EncounterRegion region = host.regions().get(encounterId);
        if (region == null) return true;
        if (!encounterId.equals(environment.domain(level))) return false;
        if (!region.intersectsSegment(new EncounterRegion.Point(from.x, from.y, from.z),
            new EncounterRegion.Point(to.x, to.y, to.z))) return true;
        if (arrow.isOnFire()) {
            rejectArrowTransit(arrow, encounterId, "fire", "arrow fire traversal unsupported");
            return false;
        }
        // Block effects run before ProjectileImpactEvent. A closed whitelist avoids invoking
        // a stateful block callback before the rule owner can classify that callback.
        int minX = (int) Math.floor(Math.min(from.x, to.x) - 0.3);
        int maxX = (int) Math.floor(Math.max(from.x, to.x) + 0.3);
        int minY = (int) Math.floor(Math.min(from.y, to.y) - 0.3);
        int maxY = (int) Math.floor(Math.max(from.y, to.y) + 0.3);
        int minZ = (int) Math.floor(Math.min(from.z, to.z) - 0.3);
        int maxZ = (int) Math.floor(Math.max(from.z, to.z) + 0.3);
        double cells = (double) ((long) maxX - minX + 1) * ((long) maxY - minY + 1)
            * ((long) maxZ - minZ + 1);
        if (cells > 512) {
            rejectArrowTransit(arrow, encounterId, "oversized-path",
                "arrow block-effect path exceeds supported sweep");
            return false;
        }
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++)
            for (int z = minZ; z <= maxZ; z++) {
                if (!region.containsBlock(x, y, z)) continue;
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()
                    || !level.getFluidState(pos).isEmpty()) {
                    rejectArrowTransit(arrow, encounterId, pos.toShortString(),
                        "arrow block/fluid traversal unsupported at " + pos.toShortString());
                    return false;
                }
            }
        return true;
    }

    private void rejectArrowTransit(AbstractArrow arrow, UUID encounterId, String key, String reason) {
        ProjectileOrigin origin = projectileOrigins.get(arrow.getUUID());
        UUID operationId = UUID.nameUUIDFromBytes(("dndturn:arrow-transit:" + arrow.getUUID()
            + ':' + key).getBytes(StandardCharsets.UTF_8));
        engine.rejectCausalProjectileEffect(encounterId, operationId,
            origin == null || origin.ownerId() == null ? arrow.getUUID() : origin.ownerId(),
            arrow.getUUID(), null, host.clock(), reason);
        arrow.discard();
        host.sync(engine.stateView(encounterId));
    }

    /** Intercept before vanilla deflection, ignition, hurt, and arrow destruction. */
    public boolean handleArrowImpact(AbstractArrow arrow, HitResult hit) {
        requireThread();
        if (quarantinedProjectiles.containsKey(arrow.getUUID())) return true;
        if (!(arrow.level() instanceof ServerLevel level)) return false;
        UUID recorded = liveProjectileDomain(arrow.getUUID());
        if (recorded == null) return false;
        UUID encounterId = engine.canonicalEncounterId(recorded);
        if (!engine.encounterIds().contains(encounterId)) return false;
        if (!encounterId.equals(environment.domain(level))) return true;
        ProjectileOrigin origin = projectileOrigins.get(arrow.getUUID());
        if (origin == null) origin = new ProjectileOrigin(arrow.getUUID(), null, null, null,
            level.dimension().identifier().toString(), host.clock(), 0, "", "", false, false, false);
        if (!(hit instanceof EntityHitResult entityHit)
            || !(entityHit.getEntity() instanceof LivingEntity target)
            || engine.encounterOf(target.getUUID()) == null) {
            rejectArrowImpact(arrow, hit, encounterId, origin,
                "arrow impact target is outside the authorized living member set");
            return true;
        }
        UUID ownerId = origin.ownerId();
        if (origin.ownerWasPlayer() && target instanceof ServerPlayer) {
            rejectArrowImpact(arrow, hit, encounterId, origin, "PvP arrow forbidden");
            return true;
        }
        if (origin.launchTrace() != null && !origin.launchTrace().targetId().equals(target.getUUID())) {
            rejectArrowImpact(arrow, hit, encounterId, origin, "projectile collided with unselected target"); return true;
        }
        String unsupported = !origin.launchVerified() ? "launch evidence unavailable"
            : !"minecraft:arrow".equals(origin.ammoId()) ? "arrow ammunition effect unsupported"
            : origin.weaponEnchanted() ? "bow enchantment effect unsupported"
            : arrow.isOnFire() ? "arrow fire effect unsupported"
            : arrow.getPierceLevel() > 0 ? "piercing arrow effect unsupported"
            : !"".equals(origin.weaponId()) && !"minecraft:bow".equals(origin.weaponId())
                && !"minecraft:crossbow".equals(origin.weaponId()) ? "weapon effect unsupported" : null;
        if (unsupported != null) {
            rejectArrowImpact(arrow, hit, encounterId, origin, unsupported);
            return true;
        }
        Entity owner = ownerId == null ? null : host.resolve(ownerId);
        UUID shooterDomain = ownerId == null ? null : engine.encounterOf(ownerId);
        UUID targetDomain = engine.encounterOf(target.getUUID());
        if (!encounterId.equals(targetDomain)) {
            UUID sourceDomain = encounterId;
            host.queueMerge(engine.requestCausalMerge(sourceDomain, targetDomain));
            pendingProjectileAttacks.put(arrow.getUUID(),
                new PendingProjectileAttack(target.getUUID(), sourceDomain));
            persistence.changed();
            return true;
        }
        if (shooterDomain != null && !engine.canonicalEncounterId(shooterDomain).equals(encounterId)) {
            host.queueMerge(engine.requestCausalMerge(shooterDomain, encounterId));
            pendingProjectileAttacks.put(arrow.getUUID(),
                new PendingProjectileAttack(target.getUUID(), shooterDomain));
            persistence.changed();
            return true;
        }
        if (owner instanceof LivingEntity living && !living.isDeadOrDying()
            && shooterDomain == null && owner.level() == level) engine.join(encounterId, ownerId);
        UUID operationId = arrowOperationId(arrow.getUUID(), target.getUUID());
        OperationRecord.Result previous = engine.resultFor(encounterId, operationId);
        if (previous != null) {
            arrow.discard();
            return true;
        }
        StateView state = engine.stateView(encounterId);
        boolean openingAdvantage = owner instanceof LivingEntity livingOwner
            && target instanceof Mob mob && mob.getTarget() != livingOwner
            && state.members().containsKey(ownerId)
            && !engine.hasAttemptedAttack(encounterId, ownerId);
        OperationRecord.Snapshot root = host.operationSnapshot(operationId, null,
            encounterId, ownerId == null ? arrow.getUUID() : ownerId, arrow.getUUID(),
            target.getUUID(), host.clock(), state.version(), null, null,
            OperationRecord.Kind.ATTACK);
        UUID permitId = null;
        UUID damageId = null;
        DamageTrace attempted = null;
        float healthBefore = target.getHealth();
        float absorptionBefore = target.getAbsorptionAmount();
        LivingEntity equipmentOwner = owner instanceof LivingEntity living ? living : null;
        Map<EquipmentKey, EquipmentValue> equipmentBefore = equipmentSnapshot(equipmentOwner, target);
        if (!engine.beginCausalProjectileAttack(origin, root)) return true;
        if (shooterDomain == null && ownerId != null) engine.preserveActionOnNextTurn(encounterId, ownerId);
        var diagnosticOrigin = origin;
        DebugDiagnostics.log("PROJECTILE_ATTACK_ACCEPTED", () -> "op=" + root.operationId()
            + " root=" + diagnosticOrigin.rootOperationId() + " actor=" + root.owner() + " projectile=" + root.source()
            + " encounter=" + root.encounterId() + " target=" + root.target());
        try {
            if (ownerId != null && state.members().containsKey(ownerId))
                engine.setHostile(encounterId, ownerId, target.getUUID(), true);
            var defenseFacts = actorStates.snapshots().defense(target);
            int armorClass = RuleResolver.armorClass(defenseFacts);
            double toughness = defenseFacts.read(RuleFacts.DEFENSE, RuleFacts.TOUGHNESS);
            if (!Double.isFinite(toughness) || toughness > Integer.MAX_VALUE)
                throw new IllegalStateException("unsupported target toughness");
            int reduction = RuleResolver.reduction(defenseFacts);
            CombatRules.RollMode mode = CombatRules.mode(openingAdvantage,
                state.members().get(target.getUUID()).dodging());
            DamageTrace launchTrace = origin.launchTrace();
            if (launchTrace != null) { mode = launchTrace.rollMode(); armorClass = launchTrace.targetArmorClass(); reduction = launchTrace.toughnessReduction(); }
            var damagePlan = damageResolution.resolveAttack(encounterId, armorClass, reduction, origin.arrowBaseDamage(), mode, launchTrace);
            CombatRules.AttackRoll roll = damagePlan.roll();
            if (!roll.hit()) {
                DamageTrace trace = attackTrace(operationId, target.getUUID(), mode, roll, armorClass,
                    origin.arrowBaseDamage(), reduction, 0, false, 0, 0,
                    damageEvidence(arrow, state, DamageTrace.Stage.MISS, null, List.of()));
                engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.COMPLETED,
                    "arrow missed: die=" + roll.die() + " AC=" + armorClass, 0, 0, true, trace);
                arrow.discard();
                host.sync(engine.stateView(encounterId));
                return true;
            }
            int tacticalDamage = damagePlan.damage();
            attempted = attackTrace(operationId, target.getUUID(), mode, roll, armorClass,
                origin.arrowBaseDamage(), reduction, tacticalDamage, false, 0, 0,
                damageEvidence(arrow, state, DamageTrace.Stage.UNKNOWN, null, List.of()));
            if (tacticalDamage == 0) {
                DamageTrace trace = attackTrace(operationId, target.getUUID(), mode, roll, armorClass,
                    origin.arrowBaseDamage(), reduction, 0, false, 0, 0,
                    damageEvidence(arrow, state, DamageTrace.Stage.ZERO_DAMAGE, null, List.of()));
                engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.COMPLETED,
                    "arrow hit with zero tactical damage", 0, 0, true, trace);
                arrow.discard();
                host.sync(engine.stateView(encounterId));
                return true;
            }
            permitId = UUID.randomUUID();
            engine.issueOutcomePermit(new EncounterAuthority.OutcomePermit(permitId, encounterId,
                operationId, arrow.getUUID(), Set.of(target.getUUID()),
                Set.of(EncounterPhase.ENVIRONMENT), 1, engine.stateView(encounterId).round()));
            damageId = UUID.randomUUID();
            OperationRecord.Snapshot child = host.operationSnapshot(damageId, operationId,
                encounterId, root.owner(), arrow.getUUID(), target.getUUID(), host.clock(),
                engine.stateView(encounterId).version(), null, null, OperationRecord.Kind.DAMAGE);
            if (!engine.beginOperation(child, permitId))
                throw new IllegalStateException("arrow damage permit rejected");
            host.enterOutcome();
            try {
                DamageSource source = level.damageSources().source(TacticalDamageContext.DAMAGE_TYPE,
                    arrow, owner);
                var observation = TacticalDamageContext.hurtObserved(level, target, source,
                    tacticalDamage, host.knockback(encounterId), damageId, host.damageAttempt());
                float healthLoss = Math.max(0, healthBefore - target.getHealth());
                float absorptionLoss = Math.max(0, absorptionBefore - target.getAbsorptionAmount());
                DamageTrace trace = attackTrace(operationId, target.getUUID(), mode, roll, armorClass,
                    origin.arrowBaseDamage(), reduction, tacticalDamage, observation.accepted(),
                    absorptionLoss, healthLoss,
                    damageEvidence(arrow, state, observation.accepted() ? DamageTrace.Stage.VANILLA_ACCEPTED
                        : DamageTrace.Stage.VANILLA_REJECTED, observation,
                        equipmentChanges(equipmentBefore, equipmentSnapshot(equipmentOwner, target))));
                engine.publish(encounterId, damageId, 0,
                    observation.accepted() ? OperationRecord.Outcome.COMPLETED
                        : healthLoss > 0 || absorptionLoss > 0 ? OperationRecord.Outcome.PARTIAL
                        : OperationRecord.Outcome.REJECTED,
                    "arrow hurtServer=" + observation.accepted() + " absorptionLoss=" + absorptionLoss,
                    0, healthLoss, true);
                actorStates.observedHit(damageId, equipmentOwner, target, observation.accepted(), healthLoss + absorptionLoss);
                engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.COMPLETED,
                    "arrow hit: die=" + roll.die(), 0, healthLoss, true, trace);
                arrow.discard();
                host.sync(engine.stateView(encounterId));
                return true;
            } finally {
                host.exitOutcome();
            }
        } catch (RuntimeException failure) {
            quarantineProjectile(arrow.getUUID(), encounterId, target.getUUID(),
                "arrow world effect uncertain: " + failure.getClass().getSimpleName());
            float healthLoss = Math.max(0, healthBefore - target.getHealth());
            float absorptionLoss = Math.max(0, absorptionBefore - target.getAbsorptionAmount());
            DamageTrace unknown = attempted == null ? null : new DamageTrace(operationId,
                target.getUUID(), attempted.rollMode(), attempted.firstDie(), attempted.secondDie(),
                attempted.selectedDie(), attempted.rollTotal(), attempted.targetArmorClass(),
                attempted.hit(), attempted.critical(), attempted.weaponDamage(), attempted.toughnessReduction(),
                attempted.tacticalDamage(), false, absorptionLoss, healthLoss,
                damageEvidence(arrow, state, DamageTrace.Stage.UNKNOWN, null,
                    equipmentChanges(equipmentBefore, equipmentSnapshot(equipmentOwner, target))));
            if (damageId != null && engine.pendingOperation(encounterId, damageId) != null)
                engine.publish(encounterId, damageId, 0, OperationRecord.Outcome.UNKNOWN,
                    "arrow world effect uncertain: " + failure.getClass().getSimpleName(), 0, healthLoss, true);
            if (engine.pendingOperation(encounterId, operationId) != null)
                engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.UNKNOWN,
                    "arrow world effect uncertain: " + failure.getClass().getSimpleName(), 0, healthLoss, true, unknown);
            host.sync(engine.stateView(encounterId));
            LOGGER.error("Arrow {} tactical settlement failed", arrow.getUUID(), failure);
            return true;
        } finally {
            if (permitId != null && engine.encounterIds().contains(encounterId))
                engine.revokeEffectPermit(encounterId, permitId);
            host.flushOutcome();
        }
    }

    private static UUID arrowOperationId(UUID projectileId, UUID targetId) {
        return UUID.nameUUIDFromBytes(("dndturn:arrow:" + projectileId + ':' + targetId)
            .getBytes(StandardCharsets.UTF_8));
    }

    private void rejectArrowImpact(AbstractArrow arrow, HitResult hit, UUID encounterId,
                                   ProjectileOrigin origin, String reason) {
        String collision = hit instanceof EntityHitResult entityHit
            ? "entity:" + entityHit.getEntity().getUUID()
            : hit.getType() + ":" + BlockPos.containing(hit.getLocation());
        UUID operationId = UUID.nameUUIDFromBytes(("dndturn:arrow-effect:" + arrow.getUUID()
            + ':' + collision).getBytes(StandardCharsets.UTF_8));
        engine.rejectCausalProjectileEffect(encounterId, operationId,
            origin.ownerId() == null ? arrow.getUUID() : origin.ownerId(), arrow.getUUID(),
            hit instanceof EntityHitResult entityHit ? entityHit.getEntity().getUUID() : null,
            host.clock(), reason);
        arrow.discard();
        host.sync(engine.stateView(encounterId));
    }

    public void quarantineProjectile(UUID projectile, UUID encounter, UUID target, String reason) {
        quarantinedProjectiles.putIfAbsent(projectile, new CombatPersistenceEnvelope.QuarantinedProjectile(
            arrowOperationId(projectile, target), encounter, target, reason));
        persistence.changed();
    }

    public boolean isProjectileQuarantined(UUID projectile) {
        requireThread();
        return quarantinedProjectiles.containsKey(projectile);
    }
    public void restore(CombatPersistenceEnvelope saved) {
            projectileOrigins.putAll(saved.projectileOrigins());
            projectileSimulationDomains.putAll(saved.projectileDomains());
            quarantinedProjectiles.putAll(saved.quarantinedProjectiles());
            for (var encounter : saved.rules().encounters()) {
                for (var operation : encounter.pending().values()) {
                    if (operation.source() != null && saved.projectileOrigins().containsKey(operation.source()))
                        quarantinedProjectiles.putIfAbsent(operation.source(),
                            new CombatPersistenceEnvelope.QuarantinedProjectile(operation.operationId(),
                                encounter.id(), operation.target(), "restored executing projectile effect cannot be replayed"));
                }
            }
            // Existing saved launch records may already be referenced by an ended domain.
            // Retention never grants scheduling or effect authorization.
            retainedProjectileHistory.addAll(saved.projectileOrigins().keySet());
            for (var entry : saved.pendingProjectileAttacks().entrySet()) {
                pendingProjectileAttacks.put(entry.getKey(), new PendingProjectileAttack(
                    entry.getValue().targetId(), entry.getValue().sourceEncounterId()));
                quarantineProjectile(entry.getKey(), entry.getValue().sourceEncounterId(),
                    entry.getValue().targetId(), "restored pending collision requires evidence; replay forbidden");
            }
            projectileAbilities.putAll(saved.projectileAbilities());
    }

    public void auditAbilityRecovery(UUID encounter) {
        for (var entry : projectileAbilities.entrySet()) {
            var origin = projectileOrigins.get(entry.getKey());
            if (origin == null || origin.sourceEncounterId() == null
                || !engine.canonicalEncounterId(origin.sourceEncounterId()).equals(encounter)) continue;
            try { AbilityExecutors.resolve(entry.getValue().invocation()); }
            catch (RuntimeException unavailable) {
                quarantineProjectile(entry.getKey(), encounter, entry.getValue().invocation().target().entity(),
                    "launch ability version unavailable; delayed effect cannot be replayed");
            }
        }
    }

    public int reconcilePending(UUID encounterId) {
        int unknown = 0;
            for (var entry : Map.copyOf(pendingProjectileAttacks).entrySet()) {
                UUID projectileId = entry.getKey();
                UUID domain = projectileSimulationDomains.get(projectileId);
                if (domain == null || !engine.canonicalEncounterId(domain).equals(encounterId)) continue;
                ProjectileOrigin origin = projectileOrigins.get(projectileId);
                {
                    UUID owner = origin == null || origin.ownerId() == null
                        ? projectileId : origin.ownerId();
                    engine.recordRestoredUnknown(encounterId,
                        arrowOperationId(projectileId, entry.getValue().targetId()), owner,
                        projectileId, entry.getValue().targetId(), host.clock(),
                        "pending collision effects cannot be reconciled after restart; not replayed");
                    pendingProjectileAttacks.remove(projectileId);
                    unknown++;
                }
            }
        return unknown;
    }

    public Map<UUID, CombatPersistenceEnvelope.PendingProjectileAttack> pendingSnapshot() {
        Map<UUID, CombatPersistenceEnvelope.PendingProjectileAttack> pendingArrows = new HashMap<>();
        for (var entry : pendingProjectileAttacks.entrySet())
            pendingArrows.put(entry.getKey(), new CombatPersistenceEnvelope.PendingProjectileAttack(
                entry.getValue().targetId(), entry.getValue().sourceEncounterId()));
        return Map.copyOf(pendingArrows);
    }

    private DamageTrace.DamageEvidence damageEvidence(Entity actor, StateView state,
            DamageTrace.Stage stage, TacticalDamageContext.HurtObservation observed,
            List<DamageTrace.EquipmentChange> changes) {
        return DamageResolution.damageEvidence(actor, state, stage, observed, changes,
            projectileOrigins.get(actor.getUUID()), host.knockback(state.id()));
    }
    public ProjectileOrigin origin(UUID id) { return projectileOrigins.get(id); }
    public UUID recordedDomain(UUID id) { return projectileSimulationDomains.get(id); }
    public boolean hasPendingContact(UUID id) { return pendingProjectileAttacks.containsKey(id); }
    public Map<UUID, ProjectileOrigin> origins() { return Map.copyOf(projectileOrigins); }
    public Map<UUID, UUID> domains() { return Map.copyOf(projectileSimulationDomains); }
    public Map<UUID, CombatPersistenceEnvelope.QuarantinedProjectile> quarantines() { return Map.copyOf(quarantinedProjectiles); }
    public Map<UUID, CombatPersistenceEnvelope.ProjectileAbility> abilities() { return Map.copyOf(projectileAbilities); }
    private void requireThread() { if (!server.isSameThread()) throw new IllegalStateException("server thread required"); }

    public void expireOrigins(long retention) {
        int before = projectileOrigins.size();
        for (UUID projectileId : List.copyOf(projectileOrigins.keySet())) {
            ProjectileOrigin origin = projectileOrigins.get(projectileId);
            if (origin == null || host.clock() - origin.launchedServerTick() < retention) continue;
            // A live or still-relevant projectile keeps its launch evidence.
            if (host.resolve(projectileId) != null
                || pendingProjectileAttacks.containsKey(projectileId)
                || projectileAbilities.containsKey(projectileId)
                || quarantinedProjectiles.containsKey(projectileId)) continue;
            projectileOrigins.remove(projectileId);
            projectileSimulationDomains.remove(projectileId);
            retainedProjectileHistory.remove(projectileId);
        }
        if (projectileOrigins.size() != before) {
            DebugDiagnostics.log("PROJECTILE_HISTORY_EXPIRED", () -> "removed=" + (before - projectileOrigins.size()));
            persistence.changed();
        }
    }
}
