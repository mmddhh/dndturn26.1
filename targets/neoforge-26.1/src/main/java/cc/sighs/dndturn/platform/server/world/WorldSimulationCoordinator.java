package cc.sighs.dndturn.platform.server.world;
import cc.sighs.dndturn.domain.encounter.StateView;
import cc.sighs.dndturn.platform.diagnostics.DebugDiagnostics;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.EncounterRegion;
import cc.sighs.dndturn.domain.encounter.operation.ProjectileOrigin;
import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import cc.sighs.dndturn.platform.server.entity.MinecraftProjectiles;
import cc.sighs.dndturn.platform.server.control.SimulationPolicy;
import cc.sighs.dndturn.platform.server.control.WorldOutcomePolicy;
import cc.sighs.dndturn.platform.server.world.CloudOrigins;
import cc.sighs.dndturn.platform.server.world.EnvironmentScheduler;
import cc.sighs.dndturn.platform.server.world.RegionalScheduledTicks;
import cc.sighs.dndturn.platform.server.control.SimulationPreparation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/** Owns spatial scheduling projections, environment boundaries and per-step simulation decisions. */
public final class WorldSimulationCoordinator implements RegionalScheduledTicks.RegionAccess {
    public interface Projectiles {
        boolean isProjectileQuarantined(UUID id);
        cc.sighs.dndturn.domain.encounter.operation.ProjectileOrigin origin(UUID id);
        UUID recordedDomain(UUID id);
        boolean hasPendingContact(UUID id);
        SimulationPreparation prepareArrowSimulation(AbstractArrow arrow);
        UUID liveProjectileDomain(UUID id);
        void quarantineProjectile(UUID projectile, UUID encounter, UUID target, String reason);
    }
    public interface Host {
        long clock();
        boolean prepareBoundary(ServerLevel level);
        boolean blocked(UUID encounter);
        boolean exitedRegion(Entity entity, UUID encounter);
        void reconcileMovement(Entity entity);
        void sync(StateView state);
        void syncBodyStateTransitions();
        void stop(UUID encounter);
    }
    private final MinecraftServer server;
    private final EncounterAuthority engine;
    private final Host host;
    private final Projectiles projectiles;
    private final CloudOrigins.Evidence clouds;
    public WorldSimulationCoordinator(MinecraftServer server, EncounterAuthority engine, Host host, Projectiles projectiles, CloudOrigins.Evidence clouds) {
        this.server = server; this.engine = engine; this.host = host; this.projectiles = projectiles; this.clouds = clouds;
        this.environment = new EnvironmentScheduler(engine);
    }
    public EnvironmentScheduler scheduler() { return environment; }
    public RegionalScheduledTicks scheduledTicks() { return scheduledTicks; }
    public Map<UUID, EncounterRegion> regions() { return Map.copyOf(regions); }
    public Set<Long> chunks(UUID encounter) { return regionChunks.getOrDefault(encounter, Set.of()); }
    public void bind(UUID encounter, EncounterRegion region) { var chunks = exactRegionChunks(region); regions.put(encounter, region); regionChunks.put(encounter, chunks); }
    public void unbind(UUID encounter) { regions.remove(encounter); regionChunks.remove(encounter); environmentStepStartedAt.remove(encounter); }
    public void advanceClock() { simulationDecisions.clear(); }
    public void close() {
        try { scheduledTicks.releaseAll(); }
        finally { regions.clear(); regionChunks.clear(); simulationDecisions.clear(); environmentStepStartedAt.clear(); environment.close(); }
    }
    private void requireThread() { if (!server.isSameThread()) throw new IllegalStateException("world simulation requires server thread"); }

    /** Exact region snapshots are a query projection; EncounterAuthority owns membership and phase. */
    private final Map<UUID, EncounterRegion> regions = new HashMap<>();
    private final Map<UUID, Set<Long>> regionChunks = new HashMap<>();
    private final EnvironmentScheduler environment;
    private final RegionalScheduledTicks scheduledTicks = new RegionalScheduledTicks(this);

    public UUID activeEnvironmentStep(ServerLevel level) { return environment.step(level); }
    /** The primary retains its periodic clock on merge; clocks are never summed. */
    public long environmentTimeAt(ServerLevel level, BlockPos pos) {
        UUID domain = encounterAtBlock(level, pos);
        if (domain == null) return level.getGameTime();
        long time = engine.environmentTime(domain);
        return domain.equals(environment.domain(level)) && environment.step(level) != null
            ? Math.addExact(time, 1) : time;
    }

    public UUID encounterAtBlock(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().identifier().toString();
        UUID selected = null;
        for (var entry : regions.entrySet()) {
            EncounterRegion region = entry.getValue();
            if (region.dimension().equals(dimension)
                && region.containsBlock(pos.getX(), pos.getY(), pos.getZ())
                && (selected == null || entry.getKey().compareTo(selected) < 0)) selected = entry.getKey();
        }
        return selected;
    }

    public Set<Long> candidateRegionChunks(ServerLevel level) {
        Set<Long> chunks = new HashSet<>();
        String dimension = level.dimension().identifier().toString();
        for (var entry : regions.entrySet()) {
            if (entry.getValue().dimension().equals(dimension))
                chunks.addAll(regionChunks.getOrDefault(entry.getKey(), Set.of()));
        }
        return chunks;
    }

    public static Set<Long> exactRegionChunks(EncounterRegion region) {
        Set<Long> chunks = new HashSet<>();
        int y = (int) Math.ceil(region.minY() - 0.5);
        if (y + 0.5 > region.maxY()) return chunks;
        int minX = Math.floorDiv((int) Math.floor(region.discovery().minX() - region.radius()), 16);
        int maxX = Math.floorDiv((int) Math.floor(region.discovery().maxX() + region.radius()), 16);
        int minZ = Math.floorDiv((int) Math.floor(region.discovery().minZ() - region.radius()), 16);
        int maxZ = Math.floorDiv((int) Math.floor(region.discovery().maxZ() + region.radius()), 16);
        for (int chunkX = minX; chunkX <= maxX; chunkX++) for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
            boolean relevant = false;
            for (int x = chunkX * 16; x < chunkX * 16 + 16 && !relevant; x++)
                for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++)
                    if (region.containsBlock(x, y, z)) { relevant = true; break; }
            if (relevant) chunks.add(net.minecraft.world.level.ChunkPos.pack(chunkX, chunkZ));
        }
        return Set.copyOf(chunks);
    }

    /** Called before ServerLevel advances game time or drains either scheduled queue. */
    public void beforeLevelTick(ServerLevel level) {
        requireThread();
        if (regions.isEmpty() || candidateRegionChunks(level).isEmpty()) return;
        if (!host.prepareBoundary(level)) return;
        scheduledTicks.captureAll(level);
        if (!level.tickRateManager().runsNormally() || level.isDebug()) return;
        List<UUID> candidates = new ArrayList<>();
        for (UUID id : engine.encounterIds()) {
            if (host.blocked(id)) continue;
            EncounterRegion region = regions.get(id);
            if (region != null && region.dimension().equals(level.dimension().identifier().toString())
                && engine.stateView(id).phase() == EncounterPhase.ENVIRONMENT) candidates.add(id);
        }
        environment.begin(level, candidates, encounterId -> {
            var state = engine.stateView(encounterId);
            boolean loadedMember = state.members().keySet().stream().anyMatch(id -> level.getEntity(id) != null);
            boolean allowed = loadedMember && regionChunksLoaded(level, regionChunks.getOrDefault(encounterId, Set.of()));
            if (allowed) environmentStepStartedAt.put(encounterId, host.clock());
            return allowed;
        });
    }

    public void beforeBlockQueue(ServerLevel level) {
        requireThread();
        if (!candidateRegionChunks(level).isEmpty())
            scheduledTicks.beforeBlockQueue(level, environment.domain(level));
    }

    public void beforeFluidQueue(ServerLevel level) {
        requireThread();
        if (!candidateRegionChunks(level).isEmpty())
            scheduledTicks.beforeFluidQueue(level, environment.domain(level));
    }

    /** Reached only after the normal ServerLevel tick returned through all world stages. */
    public void afterLevelTick(ServerLevel level) {
        requireThread();
        UUID encounterId = environment.complete(level);
        if (encounterId != null) {
            environmentStepStartedAt.remove(encounterId);
            host.sync(engine.stateView(encounterId));
            host.syncBodyStateTransitions();
        }
    }

    /** Called by the outer vanilla world-tick exception boundary, before it reports the crash. */
    public void abortLevelTick(ServerLevel level, Throwable failure) {
        requireThread();
        UUID encounterId = environment.abort(level, host.clock(), failure);
        if (encounterId != null) { environmentStepStartedAt.remove(encounterId); host.stop(encounterId); }
    }

    public void beforeChunkUnload(ServerLevel level, net.minecraft.world.level.ChunkPos chunk) {
        requireThread();
        scheduledTicks.releaseChunk(level, chunk);
    }

    public net.minecraft.world.level.chunk.ChunkAccess.PackedTicks savedTicksForChunk(
        ServerLevel level, net.minecraft.world.level.ChunkPos chunk,
        net.minecraft.world.level.chunk.ChunkAccess.PackedTicks vanilla) {
        requireThread();
        List<net.minecraft.world.ticks.SavedTick<net.minecraft.world.level.block.Block>> blocks =
            new ArrayList<>(vanilla.blocks());
        blocks.addAll(scheduledTicks.savedInChunk(level.getBlockTicks(), chunk));
        List<net.minecraft.world.ticks.SavedTick<net.minecraft.world.level.material.Fluid>> fluids =
            new ArrayList<>(vanilla.fluids());
        fluids.addAll(scheduledTicks.savedInChunk(level.getFluidTicks(), chunk));
        return new net.minecraft.world.level.chunk.ChunkAccess.PackedTicks(blocks, fluids);
    }

    public static boolean regionChunksLoaded(ServerLevel level, Set<Long> chunks) {
        for (long chunk : chunks) {
            net.minecraft.world.level.ChunkPos pos = net.minecraft.world.level.ChunkPos.unpack(chunk);
            // Level.shouldTickBlocksAt only checks the distance-manager range. The native
            // BoundTickingBlockEntity also requires BLOCK_TICKING and entity storage loaded.
            // Do not spend an environment step while that inner vanilla gate will skip it.
            var loaded = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (loaded == null || !level.shouldTickBlocksAt(chunk) || !level.areEntitiesLoaded(chunk)
                    || !loaded.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.BLOCK_TICKING)) return false;
        }
        return true;
    }

    /** Pure query. Preparing a vanilla simulation step is a separate execution boundary. */
    public boolean isEntitySimulationPaused(Entity entity) {
        return !SimulationPolicy.entity(captureEntitySimulation(entity)).allowed();
    }

    /** Read-only native classification and current owner evidence. */
    public SimulationPolicy.EntityFacts captureEntitySimulation(Entity entity) {
        boolean quarantine = projectiles.isProjectileQuarantined(entity.getUUID());
        if (CloudOrigins.tagged(entity))
            return new SimulationPolicy.EntityFacts(!CloudOrigins.confirmed(entity, clouds), true, cloudSimulationPaused(entity), false);
        if (entity instanceof net.minecraft.world.entity.AreaEffectCloud)
            return new SimulationPolicy.EntityFacts(false, true, isEnvironmentEntityPaused(entity), false);
        if (entity instanceof net.minecraft.world.entity.item.PrimedTnt)
            return new SimulationPolicy.EntityFacts(quarantine, true, isEnvironmentEntityPaused(entity), false);
        if (entity instanceof AbstractArrow || (entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball || MinecraftProjectiles.supported(entity)) && (projectiles.origin(entity.getUUID()) != null)) {
            UUID recorded = projectiles.recordedDomain(entity.getUUID());
            UUID domain = recorded == null ? null : engine.canonicalEncounterId(recorded);
            if (domain == null || !engine.encounterIds().contains(domain))
                return new SimulationPolicy.EntityFacts(quarantine, true, isEntityInsidePausedRegion(entity), false);
            return new SimulationPolicy.EntityFacts(quarantine, true,
                !domain.equals(environment.domain(entity.level())), projectiles.hasPendingContact(entity.getUUID()));
        }
        return new SimulationPolicy.EntityFacts(quarantine, false, false, false);
    }

    private record SimulationDecision(long tick, EncounterAuthority.Revision revision, UUID environment,
                                      UUID environmentStep, UUID instance, SimulationPreparation result) {}
    public boolean cloudSimulationPaused(Entity cloud) {
        return !WorldOutcomePolicy.cloudSimulation(captureCloud(cloud)).allowed();
    }
    private WorldOutcomePolicy.CloudFacts captureCloud(Entity cloud) {
        boolean confirmed = CloudOrigins.confirmed(cloud, clouds);
        UUID domain = confirmed ? engine.canonicalEncounterId(CloudOrigins.domain(cloud)) : null;
        return new WorldOutcomePolicy.CloudFacts(confirmed, domain != null && engine.encounterIds().contains(domain),
            domain != null && domain.equals(environment.domain(cloud.level())), isEntityInsidePausedRegion(cloud),
            cloud.level().tickRateManager().runsNormally(), host.blocked(domain));
    }
    public boolean cloudTargetAllowed(Entity cloud, LivingEntity target) {
        var source = captureCloud(cloud);
        UUID domain = source.confirmed() ? engine.canonicalEncounterId(CloudOrigins.domain(cloud)) : null;
        UUID step = environmentStepFor(cloud);
        return WorldOutcomePolicy.cloudTarget(new WorldOutcomePolicy.CloudTargetFacts(
            WorldOutcomePolicy.cloudSimulation(source).allowed(), source.liveDomain(),
            domain != null && domain.equals(engine.encounterOf(target.getUUID())),
            step != null && step.equals(environmentStepFor(target)), isEntityInsidePausedRegion(target))).allowed();
    }
    private final Map<UUID, SimulationDecision> simulationDecisions = new HashMap<>();

    /** Called only from the vanilla entity/ride execution gate; repeated visits cannot replay effects. */
    public SimulationPreparation prepareEntitySimulation(Entity entity) {
        requireThread();
        if (entity.isRemoved()) return SimulationPreparation.ended();
        if (projectiles.isProjectileQuarantined(entity.getUUID())) return SimulationPreparation.quarantined();
        SimulationDecision prior = simulationDecisions.get(entity.getUUID());
        UUID environment = this.environment.domain(entity.level());
        UUID step = this.environment.step(entity.level());
        UUID instance = entity instanceof PresentationIdentity identity ? identity.dndturn$presentationInstance() : null;
        // A contact consumed this entity's step. A different operation's rule revision cannot replay its body tick.
        if (prior != null && prior.tick() == host.clock() && Objects.equals(prior.instance(), instance)
            && prior.result().status() == SimulationPreparation.Status.CONTACT_HANDLED) return prior.result();
        if (prior != null && prior.tick() == host.clock()
            && prior.revision().equals(engine.revision()) && Objects.equals(prior.environment(), environment)
            && Objects.equals(prior.environmentStep(), step) && Objects.equals(prior.instance(), instance))
            return prior.result();
        SimulationPreparation result = prepareEntitySimulationStep(entity);
        simulationDecisions.put(entity.getUUID(), new SimulationDecision(host.clock(),
            engine.revision(), environment, step, instance, result));
        return result;
    }

    private SimulationPreparation prepareEntitySimulationStep(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.AreaEffectCloud)
            return SimulationPreparation.scheduling(SimulationPolicy.entity(captureEntitySimulation(entity)));
        if (projectiles.isProjectileQuarantined(entity.getUUID()))
            return SimulationPreparation.quarantined();
        if (entity instanceof net.minecraft.world.entity.item.PrimedTnt)
            return SimulationPreparation.scheduling(SimulationPolicy.entity(captureEntitySimulation(entity)));
        if (entity instanceof AbstractArrow arrow) return projectiles.prepareArrowSimulation(arrow);
        if (MinecraftProjectiles.supported(entity) && (projectiles.origin(entity.getUUID()) != null)) {
            UUID domain=projectiles.liveProjectileDomain(entity.getUUID());
            if (domain==null) return SimulationPreparation.advance();
            // No mutation in policy; domain reconciliation above remains owned here.
            if (!SimulationPolicy.entity(new SimulationPolicy.EntityFacts(false, true,
                !domain.equals(environment.domain(entity.level())), false)).allowed())
                return new SimulationPreparation(SimulationPreparation.Status.HELD, "ENVIRONMENT_STEP_REQUIRED");
            var box=entity.getBoundingBox().expandTowards(entity.getDeltaMovement()).inflate(.1);
            if (box.getSize()>8) { projectiles.quarantineProjectile(entity.getUUID(),domain,null,"item projectile sweep exceeds budget"); return SimulationPreparation.quarantined(); }
            for (var pos:BlockPos.betweenClosed(box)) {
                if (!entity.level().hasChunkAt(pos) || !domain.equals(encounterAtBlock((ServerLevel)entity.level(),pos))
                    || !MinecraftProjectiles.inert(entity.level().getBlockState(pos))
                        && !(entity instanceof net.minecraft.world.entity.projectile.FishingHook
                            && entity.level().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.LiquidBlock
                            && entity.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER))) {
                    projectiles.quarantineProjectile(entity.getUUID(),domain,null,"item projectile encountered unsupported traversal"); return SimulationPreparation.quarantined();
                }
            }
            if (entity instanceof net.minecraft.world.entity.projectile.FishingHook) {
                for (var pos:BlockPos.betweenClosed(entity.blockPosition().offset(-3,-1,-3),entity.blockPosition().offset(3,3,3)))
                    if (!entity.level().hasChunkAt(pos) || !domain.equals(encounterAtBlock((ServerLevel)entity.level(),pos))) {
                        projectiles.quarantineProjectile(entity.getUUID(),domain,null,"fishing observation outside loaded domain"); return SimulationPreparation.quarantined();
                    }
            }
            return SimulationPreparation.advance();
        }
        if (entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball && (projectiles.origin(entity.getUUID()) != null)) {
            UUID domain = projectiles.liveProjectileDomain(entity.getUUID());
            if (domain == null) return SimulationPreparation.advance();
            return SimulationPreparation.scheduling(SimulationPolicy.entity(new SimulationPolicy.EntityFacts(false, true,
                !domain.equals(environment.domain(entity.level())), false)));
        }
        host.reconcileMovement(entity);
        return SimulationPreparation.scheduling(SimulationPolicy.entity(captureEntitySimulation(entity)));
    }

    /** Lease-owner mutation, called only from prepareEntitySimulationStep, never from a query. */
    public boolean isEntityInsidePausedRegion(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        Vec3 center = entity.getBoundingBox().getCenter();
        String dimension = level.dimension().identifier().toString();
        for (var entry : regions.entrySet()) {
            EncounterRegion region = entry.getValue();
            if (region.dimension().equals(dimension)
                && region.containsPoint(center.x, center.y, center.z)
                && !host.exitedRegion(entity, entry.getKey())) return true;
        }
        return false;
    }

    /** Current spatial ownership, not membership or the original igniter's turn. */
    public UUID environmentStepFor(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !level.tickRateManager().runsNormally()) return null;
        UUID active = environment.domain(level);
        if (active == null || !engine.encounterIds().contains(active)) return null;
        Vec3 center = entity.getBoundingBox().getCenter();
        EncounterRegion region = regions.get(active);
        return region != null && region.dimension().equals(level.dimension().identifier().toString())
            && region.containsPoint(center.x, center.y, center.z) && !isEnvironmentEntityPaused(entity)
            ? environment.step(level) : null;
    }

    private boolean isEnvironmentEntityPaused(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        Vec3 center = entity.getBoundingBox().getCenter();
        String dimension = level.dimension().identifier().toString();
        for (var entry : regions.entrySet()) {
            if (entry.getValue().dimension().equals(dimension)
                && entry.getValue().containsPoint(center.x, center.y, center.z)
                && !entry.getKey().equals(environment.domain(level))) return true;
        }
        return false;
    }

    /** Use the same continuous region with a block's center, not its whole chunk. */
    public boolean isBlockSimulationPaused(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().identifier().toString();
        for (var entry : regions.entrySet()) {
            EncounterRegion region = entry.getValue();
            if (region.dimension().equals(dimension)
                && region.containsBlock(pos.getX(), pos.getY(), pos.getZ()))
                if (!entry.getKey().equals(environment.domain(level))) return true;
        }
        return false;
    }

    public boolean clientEntityPaused(Entity entity) {
        if (projectiles.isProjectileQuarantined(entity.getUUID())) return true;
        if (entity instanceof AbstractArrow || (entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball || MinecraftProjectiles.supported(entity)) && (projectiles.origin(entity.getUUID()) != null)) {
            UUID recorded = projectiles.recordedDomain(entity.getUUID());
            UUID domain = recorded == null ? null : engine.canonicalEncounterId(recorded);
            if (domain != null && engine.encounterIds().contains(domain)) {
                var state = engine.stateView(domain);
                return !SimulationPolicy.presentationDuringEnvironment(false, projectiles.hasPendingContact(entity.getUUID()),
                    host.blocked(domain), state.phase() == EncounterPhase.ENVIRONMENT, server.tickRateManager().isFrozen()).allowed();
            }
        }
        return isEntitySimulationPaused(entity);
    }
    private final Map<UUID, Long> environmentStepStartedAt = new HashMap<>();
    public void expireStepDeadlines(long limit) {
        long now = host.clock();
        for (UUID encounterId : List.copyOf(engine.encounterIds())) {
            StateView state = engine.stateView(encounterId);
            UUID step = engine.authorizedEnvironmentStep(encounterId);
            if (step == null) { environmentStepStartedAt.remove(encounterId); continue; }
            long started = environmentStepStartedAt.getOrDefault(encounterId, now);
            if (now - started < limit) continue;
            ServerLevel level = levelFor(state.region().dimension());
            if (level == null) continue;
            DebugDiagnostics.log("EXECUTION_DEADLINE_ENVIRONMENT", () -> "encounter=" + encounterId + " step=" + step);
            environmentStepStartedAt.remove(encounterId);
            if (environment.abort(level, now, new IllegalStateException("EXECUTION_DEADLINE")) != null)
                host.stop(encounterId);
        }
    }
    private ServerLevel levelFor(String dimension) {
        for (ServerLevel level : server.getAllLevels())
            if (level.dimension().identifier().toString().equals(dimension)) return level;
        return null;
    }
}
