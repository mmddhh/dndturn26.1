package cc.sighs.dndturn.platform.server.effect.vanilla;

import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.encounter.time.RoundDuration;
import cc.sighs.dndturn.domain.encounter.time.RoundTime;
import cc.sighs.dndturn.platform.mixin.server.effect.LivingEffectRoundAccess;
import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import cc.sighs.dndturn.platform.server.actor.ActorLifecycleAdapters;
import cc.sighs.dndturn.platform.server.effect.GroupEffects;
import cc.sighs.dndturn.platform.server.checkpoint.VanillaEffectSettlementState.Timer;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import static cc.sighs.dndturn.platform.server.checkpoint.VanillaEffectSettlementState.*;

/** One service owns duration values and evidence. Native objects exist only within a callback. */
public final class VanillaEffectRoundController {
    private final VanillaEffectHost service;
    private final EncounterAuthority engine;
    private final GroupEffects groupEffects = new GroupEffects();
    private final Map<UUID, Owner> owners = new HashMap<>();
    private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();
    // Display receipts are not active effects and never replay native callbacks on restore.
    private final Map<UUID, Map<String, cc.sighs.dndturn.platform.network.EncounterProtocol.EffectNotice>> instantDisplays = new HashMap<>();
    private final Set<UUID> restored = new HashSet<>();
    private static final ThreadLocal<ApplicationScope> APPLICATION = new ThreadLocal<>();
    public final class ApplicationScope implements AutoCloseable {
        private final java.util.function.Supplier<OperationRecord.Snapshot> cause;
        private final Map<UUID, LivingEntity> targets = new HashMap<>();
        private int sequence;
        private ApplicationScope(java.util.function.Supplier<OperationRecord.Snapshot> cause, List<LivingEntity> targets) {
            if (APPLICATION.get()!=null || targets.size()>32) throw new IllegalStateException("effect application scope");
            this.cause=cause;
            for (var target:targets) this.targets.put(target.getUUID(),target);
            APPLICATION.set(this);
        }
        public void close() { targets.clear(); APPLICATION.remove(); }
        private void invoke(MobEffect effect, ServerLevel level, LivingEntity target, int amplifier, double scale, Runnable nativeCall) {
            var root=Objects.requireNonNull(cause.get(), "instant effect requires executing item operation");
            if (targets.get(target.getUUID())!=target || level.getEntity(target.getUUID())!=target || target.level()!=level
                || !level.getServer().isSameThread() || !target.isAlive()
                || !root.equals(engine.pendingOperation(root.encounterId(),root.operationId()))
                || !root.encounterId().equals(engine.encounterOf(target.getUUID()))
                || !Double.isFinite(scale) || scale<0 || scale>1 || amplifier<0 || amplifier>255
                || ++sequence>128) throw new IllegalStateException("instant effect scope invalidated");
            if (root.kind()==OperationRecord.Kind.ENVIRONMENT) {
                var player=level.getServer().getPlayerList().getPlayer(root.owner());
                var pos=target.blockPosition();
                boolean environmental=engine.stateView(root.encounterId()).phase()==EncounterPhase.ENVIRONMENT
                    && root.owner().equals(engine.stateView(root.encounterId()).current())
                    && !engine.stateView(root.encounterId()).members().containsKey(root.owner());
                if (!positionAllowed(root.encounterId(),level,pos)
                    || !environmental && (player==null || player.level()!=level || !level.mayInteract(player,pos)
                        || level.getServer().isUnderSpawnProtection(level,pos,player)))
                    throw new IllegalStateException("instant effect target left permitted footprint");
            }
            String id=BuiltInRegistries.MOB_EFFECT.getKey(effect).toString();
            if (effect!=MobEffects.INSTANT_HEALTH.value() && effect!=MobEffects.INSTANT_DAMAGE.value()
                && effect!=MobEffects.SATURATION.value()) throw new IllegalStateException("instant effect needs adapter");
            UUID permit=UUID.randomUUID();
            UUID operation=UUID.nameUUIDFromBytes((root.operationId()+"/instant/"+sequence+"/"+target.getUUID()+"/"+id+"/"+amplifier+"/"+scale)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (engine.resultFor(root.encounterId(),operation)!=null) return;
            engine.issueOutcomePermit(new EncounterAuthority.OutcomePermit(permit,root.encounterId(),root.operationId(),root.owner(),
                Set.of(target.getUUID()),Set.of(EncounterPhase.CANDIDATE,EncounterPhase.ACTIVE,EncounterPhase.ENVIRONMENT),1,engine.stateView(root.encounterId()).round()));
            var child=new OperationRecord.Snapshot(operation,root.operationId(),root.encounterId(),root.owner(),root.owner(),target.getUUID(),
                service.clock(),engine.stateView(root.encounterId()).version(),root.sourceCell(),
                cc.sighs.dndturn.platform.spatial.MinecraftCoordinates.cell(target.blockPosition()),OperationRecord.Kind.NATIVE_EFFECT,service.generation());
            float before=target.getHealth(), absorption=target.getAbsorptionAmount();
            String foodBefore=food(target);
            boolean admitted=false;
            var previous=CURRENT.get();
            try {
                if (!engine.beginOperation(child,permit)) throw new IllegalStateException("instant effect admission failed");
                admitted=true;
                CURRENT.set(new Frame(VanillaEffectRoundController.this,child,target,id,amplifier,new RoundTime(1),1));
                nativeCall.run();
                if (engine.encounterOf(target.getUUID()) != null) {
                    var displays = instantDisplays.computeIfAbsent(target.getUUID(), ignored -> new LinkedHashMap<>());
                    if (displays.size() < 128 || displays.containsKey(id)) displays.put(id,
                        new cc.sighs.dndturn.platform.network.EncounterProtocol.EffectNotice(id, amplifier + 1, 0, 1, true, "MANAGED"));
                }
                engine.publish(root.encounterId(),operation,0,OperationRecord.Outcome.COMPLETED,
                    id+" amplifier="+amplifier+" scale="+scale+" health="+before+"->"+target.getHealth()
                        +" absorption="+absorption+"->"+target.getAbsorptionAmount()+" food="+foodBefore+"->"+food(target),0,
                    Math.max(0,before-target.getHealth()),true);
            } catch (RuntimeException failure) {
                if (admitted && engine.pendingOperation(root.encounterId(),operation)!=null)
                    engine.publish(root.encounterId(),operation,0,OperationRecord.Outcome.UNKNOWN,id+" native outcome uncertain health="+before+"->"+target.getHealth()+" absorption="+absorption+"->"+target.getAbsorptionAmount()+" food="+foodBefore+"->"+food(target),0,
                        Math.max(0,before-target.getHealth()),true);
                throw failure;
            } finally {
                if (previous==null) CURRENT.remove(); else CURRENT.set(previous);
                engine.revokeEffectPermit(root.encounterId(),permit);
            }
        }
    }
    private boolean positionAllowed(UUID domain, ServerLevel level, net.minecraft.core.BlockPos pos) {
        var state=engine.stateView(domain);
        if (!state.region().dimension().equals(level.dimension().identifier().toString()) || !level.hasChunkAt(pos)
            || !level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)
            || !state.region().containsBlock(pos.getX(),pos.getY(),pos.getZ())) return false;
        var spawn=level.getRespawnData();
        int radius=level.getServer() instanceof net.minecraft.server.dedicated.DedicatedServer dedicated ? dedicated.spawnProtectionRadius() : 0;
        return level.dimension()!=spawn.dimension() || level.getServer().getPlayerList().getOps().isEmpty() || radius<=0
            || Math.max(Math.abs((long)pos.getX()-spawn.pos().getX()),Math.abs((long)pos.getZ()-spawn.pos().getZ()))>radius;
    }
    public void cloud(AreaEffectCloud cloud, LivingEntity target, MobEffect effect, int amplifier, double scale,
                      UUID domain, UUID step, Runnable nativeCall) {
        if (step==null || cloud.level()!=target.level() || cloud.level().getEntity(cloud.getUUID())!=cloud
            || !cloud.level().tickRateManager().runsNormally()) throw new IllegalStateException("cloud step unavailable");
        var level=(ServerLevel)cloud.level();
        var state=engine.stateView(domain);
        var root=new OperationRecord.Snapshot(UUID.randomUUID(),null,domain,state.current(),cloud.getUUID(),target.getUUID(),
            service.clock(),state.version(),cc.sighs.dndturn.platform.spatial.MinecraftCoordinates.cell(cloud.blockPosition()),
            cc.sighs.dndturn.platform.spatial.MinecraftCoordinates.cell(target.blockPosition()),OperationRecord.Kind.ENVIRONMENT,service.generation());
        if (!engine.beginEnvironmentEffect(root,step)) throw new IllegalStateException("cloud effect admission denied");
        service.completeWorldOutcome(() -> {
        try (var scope=applications(() -> root,List.of(target))) {
            if (!positionAllowed(domain,level,cloud.blockPosition()) || !positionAllowed(domain,level,target.blockPosition())) {
                engine.publish(domain,root.operationId(),0,OperationRecord.Outcome.REJECTED,"cloud position unavailable or protected",0,0,true);
                return;
            }
            scope.invoke(effect,level,target,amplifier,scale,nativeCall);
            engine.publish(domain,root.operationId(),0,OperationRecord.Outcome.COMPLETED,"native cloud application",0,0,true);
        } catch (RuntimeException failure) {
            cloud.getPersistentData().putBoolean("dndturn:native_effect_uncertain",true);
            if (engine.pendingOperation(domain,root.operationId())!=null)
                engine.publish(domain,root.operationId(),0,OperationRecord.Outcome.UNKNOWN,"cloud application uncertain; held without replay",0,0,true);
            throw failure;
        } finally { service.changed(); }
        });
    }
    static boolean omen(ServerLevel level, LivingEntity actor, MobEffectInstance effect) {
        var frame=CURRENT.get();
        if (frame==null || frame.actor!=actor) throw new IllegalStateException("omen without turn boundary");
        boolean raid=effect.is(MobEffects.RAID_OMEN);
        if (raid && !frame.lastRound) return true;
        var runtime=frame.runtime;
        var root=frame.root;
        UUID permit=UUID.randomUUID();
        UUID operation=UUID.nameUUIDFromBytes((root.operationId()+"/omen/"+frame.effect)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (runtime.engine.resultFor(root.encounterId(),operation)!=null)
            throw new IllegalStateException("omen callback already observed");
        runtime.engine.issueOutcomePermit(new EncounterAuthority.OutcomePermit(permit,root.encounterId(),root.operationId(),root.owner(),
            Set.of(actor.getUUID()),Set.of(EncounterPhase.CANDIDATE,EncounterPhase.ACTIVE),1,runtime.engine.stateView(root.encounterId()).round()));
        var child=new OperationRecord.Snapshot(operation,root.operationId(),root.encounterId(),root.owner(),root.owner(),actor.getUUID(),
            runtime.service.clock(),runtime.engine.stateView(root.encounterId()).version(),root.sourceCell(),
            cc.sighs.dndturn.platform.spatial.MinecraftCoordinates.cell(actor.blockPosition()),OperationRecord.Kind.NATIVE_EFFECT,runtime.service.generation());
        boolean admitted=false;
        try {
            if (!runtime.engine.beginOperation(child,permit)) throw new IllegalStateException("omen callback admission denied");
            admitted=true;
            boolean remains=omenNative(level,actor,effect,frame,raid);
            boolean rejected=frame.details.values().stream().anyMatch(v -> v.startsWith("REJECTED_"));
            runtime.engine.publish(root.encounterId(),operation,0,rejected ? OperationRecord.Outcome.REJECTED : OperationRecord.Outcome.COMPLETED,
                frame.effect+" "+frame.details,0,0,true);
            return remains;
        } catch (RuntimeException failure) {
            if (admitted && runtime.engine.pendingOperation(root.encounterId(),operation)!=null)
                runtime.engine.publish(root.encounterId(),operation,0,OperationRecord.Outcome.UNKNOWN,
                    frame.effect+" native outcome uncertain "+frame.details,0,0,true);
            throw failure;
        } finally { runtime.engine.revokeEffectPermit(root.encounterId(),permit); }
    }
    private static boolean omenNative(ServerLevel level,LivingEntity actor,MobEffectInstance effect,Frame frame,boolean raid) {
        if (!(actor instanceof net.minecraft.server.level.ServerPlayer player) || player.isSpectator()) return true;
        var pos=raid ? player.getRaidOmenPosition() : player.blockPosition();
        if (pos==null) return true;
        if (!frame.runtime.positionAllowed(frame.root.encounterId(),level,pos)) {
            frame.details.put("omen","REJECTED_POSITION"); return !raid;
        }
        if (!raid) {
            boolean remains=effect.getEffect().value().applyEffectTick(level,actor,effect.getAmplifier());
            frame.accepted=!remains && actor.hasEffect(MobEffects.RAID_OMEN);
            frame.details.put("omen",remains ? "NO_CONVERSION" : frame.accepted ? "CONVERTED_TO_RAID_OMEN" : "CONVERSION_NOT_APPLIED");
            return remains;
        }
        // Vanilla POI search uses radius 64; all dependencies must already be loaded, but only its actual result is an effect destination.
        int chunkRadius=Math.floorDiv(64,16)+1; // PoiManager.getInSquare scans this padded chunk square.
        for(int x=(pos.getX()>>4)-chunkRadius;x<=(pos.getX()>>4)+chunkRadius;x++)
            for(int z=(pos.getZ()>>4)-chunkRadius;z<=(pos.getZ()>>4)+chunkRadius;z++)
            if (!level.hasChunk(x,z)) { frame.details.put("raid","REJECTED_UNLOADED_DEPENDENCY"); return false; }
        var pois=level.getPoiManager().getInRange(t -> t.is(net.minecraft.tags.PoiTypeTags.VILLAGE),pos,64,
            net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.IS_OCCUPIED).limit(4097).toList();
        if (pois.size()>4096) { frame.details.put("raid","REJECTED_POI_BUDGET"); return false; }
        var total=net.minecraft.world.phys.Vec3.ZERO;
        for(var poi:pois) total=total.add(poi.getPos().getX(),poi.getPos().getY(),poi.getPos().getZ());
        var center=pois.isEmpty()?pos:net.minecraft.core.BlockPos.containing(total.scale(1.0/pois.size()));
        var previous=level.getRaidAt(center);
        if (!frame.runtime.positionAllowed(frame.root.encounterId(),level,center)
            || previous!=null && !frame.runtime.positionAllowed(frame.root.encounterId(),level,previous.getCenter())) {
            frame.details.put("raid","REJECTED_CENTER"); return false;
        }
        var actual=level.getRaids().createOrExtendRaid(player,pos);
        frame.accepted=actual!=null;
        frame.details.put("raid",actual==null ? "NATIVE_REFUSED" : previous==actual ? "EXTENDED" : "CREATED");
        if (actual!=null) frame.details.put("raid_center",actual.getCenter().toShortString());
        player.clearRaidOmenPosition();
        return false;
    }
    private static String food(LivingEntity target) {
        return target instanceof net.minecraft.world.entity.player.Player player
            ? player.getFoodData().getFoodLevel()+"/"+player.getFoodData().getSaturationLevel() : "not_applicable";
    }
    public ApplicationScope applications(java.util.function.Supplier<OperationRecord.Snapshot> cause, List<LivingEntity> targets) {
        return new ApplicationScope(cause,targets);
    }
    public static boolean instant(MobEffect effect, ServerLevel level, LivingEntity target, int amplifier, double scale, Runnable nativeCall) {
        var scope=APPLICATION.get();
        if (scope==null) return false;
        scope.invoke(effect,level,target,amplifier,scale,nativeCall); return true;
    }
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private static final class Frame {
        final VanillaEffectRoundController runtime;
        final OperationRecord.Snapshot root;
        final LivingEntity actor;
        final String effect;
        final int amplifier;
        final RoundTime time;
        final int period;
        DamageSource damageSource;
        boolean invoked, accepted, incomingObserved, lastRound;
        final Map<String,String> details=new LinkedHashMap<>();
        double amount;
        int priorInvulnerability;
        Frame(VanillaEffectRoundController runtime, OperationRecord.Snapshot root, LivingEntity actor,
              String effect, int amplifier, RoundTime time, int period) {
            this.runtime=runtime; this.root=root; this.actor=actor; this.effect=effect;
            this.amplifier=amplifier; this.time=time; this.period=period;
        }
    }
    public VanillaEffectRoundController(VanillaEffectHost service, EncounterAuthority engine) { this.service=service; this.engine=engine; }
    public cc.sighs.dndturn.domain.effect.EffectSnapshot.Vanilla describe(LivingEntity actor, MobEffectInstance effect) {
        boolean managed = engine.encounterOf(actor.getUUID()) != null;
        var adapter = cc.sighs.dndturn.platform.server.effect.vanilla.EffectAdapters.resolve(effect.getEffect().value());
        String id = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString();
        var timers = managed ? owners.get(actor.getUUID()) : null;
        var chain = timers == null ? null : timers.effects().get(id);
        long rounds = !managed ? -2 : chain != null && chain.getFirst().expectedNativeTicks() == effect.getDuration()
            ? chain.getFirst().duration().rounds()
            : effect.isInfiniteDuration() ? -1 : time(actor).durationRounds(effect.getDuration());
        return new cc.sighs.dndturn.domain.effect.EffectSnapshot.Vanilla(id, effect.getAmplifier()+1,
            effect.getDuration(), effect.isAmbient(), effect.isVisible(), rounds,
            !managed ? cc.sighs.dndturn.domain.effect.EffectSnapshot.NativeSupport.NATIVE
                : !EffectAdapters.supported(effect)
                    ? cc.sighs.dndturn.domain.effect.EffectSnapshot.NativeSupport.NEEDS_ADAPTER
                    : cc.sighs.dndturn.domain.effect.EffectSnapshot.NativeSupport.MANAGED);
    }
    public void environmentTurnEnded(UUID encounter) {
        for (UUID member : engine.stateView(encounter).members().keySet()) instantDisplays.remove(member);
    }
    public List<cc.sighs.dndturn.platform.network.EncounterProtocol.EffectNotice> display(LivingEntity actor) {
        var result = new ArrayList<cc.sighs.dndturn.platform.network.EncounterProtocol.EffectNotice>();
        actor.getActiveEffects().stream().sorted(Comparator.comparing(VanillaEffectRoundController::key)).limit(128).forEach(effect -> {
            var value = describe(actor, effect);
            result.add(new cc.sighs.dndturn.platform.network.EncounterProtocol.EffectNotice(key(effect),
                effect.getAmplifier() + 1, effect.getDuration(), value.remainingRounds(), effect.getEffect().value().isInstantenous(), value.support().name()));
        });
        result.addAll(instantDisplays.getOrDefault(actor.getUUID(), Map.of()).values());
        return List.copyOf(result);
    }
    public Map<UUID, Owner> owners() { return Map.copyOf(owners); }
    public Map<UUID, Settlement> settlements() { return Map.copyOf(settlements); }
    public void restore(Map<UUID, Owner> saved, Map<UUID, Settlement> evidence) {
        owners.putAll(saved); restored.addAll(saved.keySet()); settlements.putAll(evidence);
    }

    /** All participant durations are owned here; unsupported callbacks are never ticked. */
    
    public boolean holdEffect(LivingEntity actor, MobEffectInstance effect) {
        var runtime=this;
        if(runtime.restored.contains(actor.getUUID())) return true;
        runtime.captureEffect(actor,effect);
        return true;
    }
    public boolean holdFire(Entity entity) {
        if (!(entity instanceof Mob actor) || actor.getRemainingFireTicks()<=0) return false;
        var runtime=this;
        if(runtime.restored.contains(actor.getUUID())) return true;
        runtime.captureFire(actor);
        return true;
    }
    public RoundTime time(LivingEntity actor) { return engine.roundTime(engine.encounterOf(actor.getUUID())); }
    private static String key(MobEffectInstance effect) { return BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString(); }
    private static List<MobEffectInstance> chain(MobEffectInstance top) {
        var list=new ArrayList<MobEffectInstance>();
        var seen=Collections.newSetFromMap(new IdentityHashMap<MobEffectInstance,Boolean>());
        for(var current=top;current!=null;current=((EffectTimerAccess)current).dndturn$hiddenEffect()) {
            if(list.size()==16 || !seen.add(current)) throw new IllegalStateException("unsupported effect chain depth");
            list.add(current);
        }
        return list;
    }
    private Owner owner(UUID id) { return owners.getOrDefault(id,new Owner(Map.of(),null)); }
    private void save(UUID id, Map<String,List<Timer>> effects, Timer fire) {
        var value=new Owner(effects,fire);
        if(!value.equals(owners.put(id,value))) service.changed();
    }
    private List<Timer> captureEffect(LivingEntity actor,MobEffectInstance effect) {
        var previous=owner(actor.getUUID()); var old=previous.effects().getOrDefault(key(effect),List.of());
        var nativeChain=chain(effect); var captured=new ArrayList<Timer>();
        for(int i=0;i<nativeChain.size();i++) {
            var current=nativeChain.get(i); var access=(EffectTimerAccess)current;
            access.dndturn$suppressLifecycle(!EffectAdapters.supported(current));
            Timer existing=i<old.size()?old.get(i):null;
            if(existing!=null && existing.instance().equals(access.dndturn$timerInstance())
                && existing.amplifier()==current.getAmplifier() && existing.expectedNativeTicks()==current.getDuration()) {
                captured.add(existing); continue;
            }
            var duration=RoundDuration.capture(time(actor),current.getDuration());
            access.dndturn$duration(duration.nativeTicks());
            captured.add(new Timer(access.dndturn$timerInstance(),current.getAmplifier(),current.getDuration(),duration,EffectAdapters.resolve(current.getEffect().value()).id(),EffectAdapters.resolve(current.getEffect().value()).version()));
        }
        if(!captured.equals(old)) {
            var effects=new HashMap<>(previous.effects()); effects.put(key(effect),List.copyOf(captured));
            save(actor.getUUID(),effects,previous.fire());
            ((LivingEffectRoundAccess)actor).dndturn$effectUpdated(effect,false,null);
        }
        return List.copyOf(captured);
    }
    private Timer captureFire(Mob actor) {
        var previous=owner(actor.getUUID()); var old=previous.fire();
        UUID instance=((PresentationIdentity)actor).dndturn$presentationInstance();
        if(old!=null && old.instance().equals(instance) && old.expectedNativeTicks()==actor.getRemainingFireTicks()) return old;
        if(actor.getRemainingFireTicks()<=0) { save(actor.getUUID(),previous.effects(),null); return null; }
        var duration=RoundDuration.capture(time(actor),actor.getRemainingFireTicks());
        actor.setRemainingFireTicks(duration.nativeTicks());
        var captured=new Timer(instance,0,actor.getRemainingFireTicks(),duration,"dndturn:fire",1);
        save(actor.getUUID(),previous.effects(),captured); return captured;
    }
    /** Validate persisted expectations before adopting new in-memory identities. Never replay effects. */
    public boolean reconcile(LivingEntity actor) {
        if(!restored.contains(actor.getUUID())) return true;
        var saved=owners.get(actor.getUUID()); var rebound=new HashMap<String,List<Timer>>();
        for(var entry:saved.effects().entrySet()) {
            var effect=actor.getActiveEffects().stream().filter(e->key(e).equals(entry.getKey())).findFirst().orElse(null);
            if(effect==null) return false;
            var nativeChain=chain(effect); if(nativeChain.size()!=entry.getValue().size()) return false;
            var timers=new ArrayList<Timer>();
            for(int i=0;i<nativeChain.size();i++) {
                var current=nativeChain.get(i); var old=entry.getValue().get(i);
                if(!old.adapter().equals(EffectAdapters.resolve(current.getEffect().value()).id())
                    || old.semanticVersion()!=EffectAdapters.resolve(current.getEffect().value()).version()
                    || current.getDuration()!=old.expectedNativeTicks() || current.getAmplifier()!=old.amplifier()) return false;
                ((EffectTimerAccess)current).dndturn$suppressLifecycle(!EffectAdapters.supported(current));
                timers.add(new Timer(((EffectTimerAccess)current).dndturn$timerInstance(),old.amplifier(),old.expectedNativeTicks(),old.duration(),old.adapter(),old.semanticVersion()));
            }
            rebound.put(entry.getKey(),List.copyOf(timers));
        }
        Timer fire=saved.fire();
        if(fire!=null) {
            if(!(actor instanceof Mob) || actor.getRemainingFireTicks()!=fire.expectedNativeTicks()) return false;
            fire=new Timer(((PresentationIdentity)actor).dndturn$presentationInstance(),0,fire.expectedNativeTicks(),fire.duration(),fire.adapter(),fire.semanticVersion());
        }
        save(actor.getUUID(),rebound,fire); restored.remove(actor.getUUID()); return true;
    }
    /** Also samples newly applied effects when END_TURN arrives before another body tick. */
    public void capture(LivingEntity actor) {
        groupEffects.capture(service, actor);
        ActorLifecycleAdapters.capture(service, actor);
        if(restored.contains(actor.getUUID()) && !reconcile(actor)) throw new IllegalStateException("effect restore evidence mismatch");
        var present=new HashSet<String>();
        for(var effect:List.copyOf(actor.getActiveEffects())) {
            present.add(key(effect)); captureEffect(actor,effect);
        }
        var previous=owner(actor.getUUID()); var effects=new HashMap<>(previous.effects());
        if(effects.keySet().retainAll(present)) save(actor.getUUID(),effects,previous.fire());
        if(actor instanceof Mob mob) captureFire(mob);
    }
    public void release(LivingEntity actor) {
        if(engine.encounterOf(actor.getUUID())!=null) {
            // A failed restart reconciliation must not overwrite newer native state with old timers.
            if(!restored.contains(actor.getUUID()) || reconcile(actor)) capture(actor);
        }
        ActorLifecycleAdapters.release(service, actor);
        groupEffects.release(service, actor);
        if (actor.isAlive()) for (var effect:actor.getActiveEffects())
            for (var current:chain(effect)) ((EffectTimerAccess)current).dndturn$suppressLifecycle(false);
        forget(actor.getUUID()); // native timers already represent remaining rounds * captured base
    }
    public void forget(UUID owner) { instantDisplays.remove(owner); groupEffects.forget(owner); if(owners.remove(owner)!=null) service.changed(); restored.remove(owner); }
    private void evidence(OperationRecord.Snapshot root,Phase phase,List<Observation> observations) {
        settlements.put(root.operationId(),new Settlement(root.operationId(),root.encounterId(),root.owner(),POLICY,VERSION,phase,observations));
        service.changed();
    }
    public void settle(LivingEntity actor,OperationRecord.Snapshot root) {
        if(CURRENT.get()!=null || settlements.containsKey(root.operationId())
            || !root.equals(engine.pendingOperation(root.encounterId(),root.operationId()))
            || root.kind()!=OperationRecord.Kind.END_TURN || !root.owner().equals(actor.getUUID()))
            throw new IllegalStateException("turn settlement identity unavailable");
        capture(actor);
        var observations=new ArrayList<Observation>(); evidence(root,Phase.PREPARED,observations);
        try {
            var boundary=List.copyOf(actor.getActiveEffects());
            var captured=new IdentityHashMap<MobEffectInstance,List<Timer>>();
            for(var effect:boundary) captured.put(effect,captureEffect(actor,effect));
            for(var effect:boundary) {
                if(!actor.isAlive()) break;
                if(actor.getEffect(effect.getEffect())!=effect) continue;
                if (!unchanged(effect,captured.get(effect))) { captureEffect(actor,effect); continue; }
                settleEffect(actor,effect,root,observations);
            }
            if(actor.isAlive() && actor instanceof Mob mob) settleFire(mob,root,observations);
            evidence(root,Phase.OBSERVED,observations);
        } catch(RuntimeException failure) {
            evidence(root,Phase.UNKNOWN,observations); throw failure;
        } finally { CURRENT.remove(); }
    }
    private static boolean unchanged(MobEffectInstance effect,List<Timer> timers) {
        var values=chain(effect);
        if (values.size()!=timers.size()) return false;
        for(int i=0;i<values.size();i++) {
            var current=values.get(i); var timer=timers.get(i);
            if (!timer.instance().equals(((EffectTimerAccess)current).dndturn$timerInstance())
                || timer.expectedNativeTicks()!=current.getDuration() || timer.amplifier()!=current.getAmplifier()) return false;
        }
        return true;
    }
    private void settleEffect(LivingEntity actor,MobEffectInstance effect,OperationRecord.Snapshot root,List<Observation> observations) {
        var timers=captureEffect(actor,effect); var initial=timers.getFirst();
        boolean remains=initial.duration().active();
        if(remains) {
            int basePeriod=effect.getEffect().equals(MobEffects.POISON)?25:effect.getEffect().equals(MobEffects.WITHER)?40
                :effect.getEffect().equals(MobEffects.REGENERATION)?50:0;
            int period=basePeriod==0?1:Math.max(1,basePeriod>>effect.getAmplifier());
            var frame=new Frame(this,root,actor,key(effect),effect.getAmplifier(),initial.duration().time(),period);
            frame.lastRound=initial.duration().rounds()==1;
            float health=actor.getHealth(),absorption=actor.getAbsorptionAmount();
            CURRENT.set(frame);
            try {
                // Base MobEffect.applyEffectTick has no effect. Absorption keeps its native early expiration check.
                remains=EffectAdapters.apply(EffectAdapters.resolve(effect.getEffect().value()),
                    (ServerLevel)actor.level(),actor,effect,initial.duration().time().ticks());
                observations.add(new Observation(frame.effect,frame.amplifier,frame.time,frame.amount,health,actor.getHealth(),
                    absorption,actor.getAbsorptionAmount(),frame.accepted,Phase.OBSERVED,frame.details));
                evidence(root,Phase.PREPARED,observations);
            } catch(RuntimeException failure) {
                observations.add(new Observation(frame.effect,frame.amplifier,frame.time,frame.amount,health,actor.getHealth(),
                    absorption,actor.getAbsorptionAmount(),frame.accepted,Phase.UNKNOWN,frame.details)); throw failure;
            } finally { CURRENT.remove(); }
            if(actor.getEffect(effect.getEffect())==effect) {
                // Native callbacks can replace/refresh a timer. Never overwrite a changed instance or chain.
                var after=chain(effect);
                if(after.size()!=timers.size()) { captureEffect(actor,effect); return; }
                for(int i=0;i<after.size();i++) if(!timers.get(i).instance().equals(((EffectTimerAccess)after.get(i)).dndturn$timerInstance())
                    || timers.get(i).expectedNativeTicks()!=after.get(i).getDuration() || timers.get(i).amplifier()!=after.get(i).getAmplifier()) {
                    captureEffect(actor,effect); return;
                }
                if (!remains) {
                    ((EffectTimerAccess)effect).dndturn$duration(0);
                    captureEffect(actor,effect);
                } else {
                var advanced=new ArrayList<Timer>();
                for(int i=0;i<after.size();i++) {
                    var duration=timers.get(i).duration().endTurn(); var current=after.get(i);
                    ((EffectTimerAccess)current).dndturn$duration(duration.nativeTicks());
                    advanced.add(new Timer(timers.get(i).instance(),current.getAmplifier(),current.getDuration(),duration,EffectAdapters.resolve(current.getEffect().value()).id(),EffectAdapters.resolve(current.getEffect().value()).version()));
                }
                boolean promoted=((EffectTimerAccess)effect).dndturn$promoteHidden();
                if(promoted) {
                    advanced.removeFirst(); var top=advanced.getFirst();
                    advanced.set(0,new Timer(((EffectTimerAccess)effect).dndturn$timerInstance(),top.amplifier(),top.expectedNativeTicks(),top.duration(),top.adapter(),top.semanticVersion()));
                }
                var owner=owner(actor.getUUID());var effects=new HashMap<>(owner.effects());effects.put(key(effect),List.copyOf(advanced));
                save(actor.getUUID(),effects,owner.fire());
                ((LivingEffectRoundAccess)actor).dndturn$effectUpdated(effect,promoted,null);
                remains=effect.isInfiniteDuration() || effect.getDuration()>0;
                }
            }
        }
        int expiringDuration=effect.getDuration(), expiringAmplifier=effect.getAmplifier();
        if(!remains && actor.getEffect(effect.getEffect())==effect
            && !NeoForge.EVENT_BUS.post(new MobEffectEvent.Expired(actor,effect)).isCanceled()) {
            if(actor.getEffect(effect.getEffect())==effect && effect.getDuration()==expiringDuration && effect.getAmplifier()==expiringAmplifier) {
                actor.removeEffectNoUpdate(effect.getEffect());
                ((LivingEffectRoundAccess)actor).dndturn$effectsRemoved(List.of(effect));
                var owner=owner(actor.getUUID());var effects=new HashMap<>(owner.effects());effects.remove(key(effect));
                save(actor.getUUID(),effects,owner.fire());
            }
        }
    }
    private void settleFire(Mob actor,OperationRecord.Snapshot root,List<Observation> observations) {
        var timer=captureFire(actor); if(timer==null) return;
        var frame=new Frame(this,root,actor,"minecraft:on_fire",0,timer.duration().time(),20);
        float health=actor.getHealth(),absorption=actor.getAbsorptionAmount(); CURRENT.set(frame);
        try {
            if(actor.fireImmune()) actor.clearFire();
            else {
                if(!actor.isInLava()) hurt((ServerLevel)actor.level(),actor,actor.damageSources().onFire(),1);
                if(actor.getRemainingFireTicks()==timer.expectedNativeTicks()) {
                    var duration=timer.duration().endTurn();actor.setRemainingFireTicks(duration.nativeTicks());
                    var previous=owner(actor.getUUID());
                    save(actor.getUUID(),previous.effects(),duration.active()?new Timer(timer.instance(),0,duration.nativeTicks(),duration,"dndturn:fire",1):null);
                }
            }
            observations.add(new Observation(frame.effect,0,frame.time,frame.amount,health,actor.getHealth(),absorption,
                actor.getAbsorptionAmount(),frame.accepted,Phase.OBSERVED,frame.details));
        } catch(RuntimeException failure) {
            observations.add(new Observation(frame.effect,0,frame.time,frame.amount,health,actor.getHealth(),absorption,
                actor.getAbsorptionAmount(),frame.accepted,Phase.UNKNOWN,frame.details));throw failure;
        } finally { CURRENT.remove(); }
    }
    public static void heal(LivingEntity target,float nativeAmount) {
        var frame=CURRENT.get();
        if(frame==null || frame.actor!=target) { target.heal(nativeAmount);return; }
        if(!Set.of("minecraft:regeneration", "minecraft:instant_health", "minecraft:instant_damage").contains(frame.effect) || frame.invoked) throw new IllegalStateException("unbound periodic healing");
        frame.invoked=true; frame.amount=frame.time.periodicAmount(nativeAmount,frame.period);
        target.heal((float)frame.amount); frame.accepted=true;
    }
    public static boolean hurt(ServerLevel level,LivingEntity target,DamageSource source,float nativeAmount) {
        var frame=CURRENT.get();
        if(frame==null || frame.actor!=target) return target.hurtServer(level,source,nativeAmount);
        boolean sourceMatches=switch(frame.effect) {
            case "minecraft:poison" -> source.is(net.neoforged.neoforge.common.NeoForgeMod.POISON_DAMAGE) || source.is(DamageTypes.MAGIC);
            case "minecraft:instant_health", "minecraft:instant_damage" -> source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
            case "minecraft:wither" -> source.is(DamageTypes.WITHER);
            case "minecraft:on_fire" -> source.is(DamageTypes.ON_FIRE);
            default -> false;
        };
        if(!sourceMatches || frame.invoked || target.level()!=level || !level.getServer().isSameThread())
            throw new IllegalStateException("unbound periodic damage");
        frame.invoked=true;frame.amount=frame.time.periodicAmount(nativeAmount,frame.period);
        if(frame.effect.equals("minecraft:poison")) frame.amount=Math.min(frame.amount,Math.max(0,target.getHealth()-1));
        var engine=frame.runtime.engine; var root=frame.root; UUID permit=UUID.randomUUID(), operation=UUID.randomUUID();
        engine.issueOutcomePermit(new EncounterAuthority.OutcomePermit(permit,root.encounterId(),root.operationId(),root.owner(),
            Set.of(target.getUUID()),Set.of(EncounterPhase.CANDIDATE,EncounterPhase.ACTIVE,EncounterPhase.ENVIRONMENT),1,engine.stateView(root.encounterId()).round()));
        var child=new OperationRecord.Snapshot(operation,root.operationId(),root.encounterId(),root.owner(),root.owner(),target.getUUID(),
            frame.runtime.service.clock(),engine.stateView(root.encounterId()).version(),root.sourceCell(),root.sourceCell(),
            OperationRecord.Kind.DAMAGE,frame.runtime.service.generation());
        float health=target.getHealth(); boolean admitted=false;
        try {
            if(!engine.beginOperation(child,permit)) throw new IllegalStateException("periodic damage admission failed");
            admitted=true;frame.damageSource=source;frame.priorInvulnerability=target.invulnerableTime;
            frame.accepted=target.hurtServer(level,source,(float)frame.amount);
            if(frame.accepted) target.invulnerableTime=0;
            else if(target.invulnerableTime==0) target.invulnerableTime=frame.priorInvulnerability;
            engine.publish(root.encounterId(),operation,0,OperationRecord.Outcome.COMPLETED,
                frame.effect+" nativeAccepted="+frame.accepted,0,Math.max(0,health-target.getHealth()),true);
            return frame.accepted;
        } catch(RuntimeException failure) {
            if(admitted && engine.pendingOperation(root.encounterId(),operation)!=null)
                engine.publish(root.encounterId(),operation,0,OperationRecord.Outcome.UNKNOWN,
                    frame.effect+" native outcome uncertain",0,Math.max(0,health-target.getHealth()),true);
            throw failure;
        } finally { frame.damageSource=null;engine.revokeEffectPermit(root.encounterId(),permit); }
    }
    /** Called at Incoming, after native immunity/override early returns and finite permit admission. */
    public static boolean authorizeIncoming(LivingIncomingDamageEvent event) {
        var frame=CURRENT.get();
        if(frame==null || frame.actor!=event.getEntity() || frame.damageSource!=event.getSource() || frame.incomingObserved) return false;
        frame.incomingObserved=true;
        frame.actor.invulnerableTime=0; event.setInvulnerabilityTicks(1); return true;
    }
}
