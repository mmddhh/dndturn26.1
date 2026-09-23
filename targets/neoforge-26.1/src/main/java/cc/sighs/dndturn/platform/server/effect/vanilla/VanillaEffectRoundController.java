package cc.sighs.dndturn.platform.server.effect.vanilla;

import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.encounter.time.RoundDuration;
import cc.sighs.dndturn.domain.encounter.time.RoundTime;
import cc.sighs.dndturn.platform.mixin.server.effect.LivingEffectRoundAccess;
import cc.sighs.dndturn.platform.observation.VanillaEffectTypes;
import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import cc.sighs.dndturn.platform.server.actor.ActorLifecycleAdapters;
import cc.sighs.dndturn.platform.server.effect.GroupEffects;
import cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectSettlementState.Timer;
import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
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
import static cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectSettlementState.*;

/** One service owns duration values and evidence. Native objects exist only within a callback. */
public final class VanillaEffectRoundController {
    private final EncounterRuntime service;
    private final EncounterAuthority engine;
    private final GroupEffects groupEffects = new GroupEffects();
    private final Map<UUID, Owner> owners = new HashMap<>();
    private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();
    private final Set<UUID> restored = new HashSet<>();
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
        boolean invoked, accepted, incomingObserved;
        double amount;
        int priorInvulnerability;
        Frame(VanillaEffectRoundController runtime, OperationRecord.Snapshot root, LivingEntity actor,
              String effect, int amplifier, RoundTime time, int period) {
            this.runtime=runtime; this.root=root; this.actor=actor; this.effect=effect;
            this.amplifier=amplifier; this.time=time; this.period=period;
        }
    }
    public VanillaEffectRoundController(EncounterRuntime service, EncounterAuthority engine) { this.service=service; this.engine=engine; }
    public Map<UUID, Owner> owners() { return Map.copyOf(owners); }
    public Map<UUID, Settlement> settlements() { return Map.copyOf(settlements); }
    public void restore(Map<UUID, Owner> saved, Map<UUID, Settlement> evidence) {
        owners.putAll(saved); restored.addAll(saved.keySet()); settlements.putAll(evidence);
    }
    private static VanillaEffectRoundController runtime(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return null;
        var service=ServerRuntime.existingEncounter(level.getServer());
        return service!=null && service.isMember(entity.getUUID()) ? service.vanillaEffects().participantEffects() : null;
    }
    /** Exact .84 implementations only; instant effects, raids and unknown overrides are not translated. */
    
    public static boolean holdEffect(LivingEntity actor, MobEffectInstance effect) {
        var runtime=runtime(actor);
        if(runtime==null || !VanillaEffectTypes.supported(effect)) return false;
        if(runtime.restored.contains(actor.getUUID())) return true;
        runtime.captureEffect(actor,effect);
        return true;
    }
    public static boolean holdFire(Entity entity) {
        if (!(entity instanceof Mob actor) || actor.getRemainingFireTicks()<=0) return false;
        var runtime=runtime(actor);
        if(runtime==null) return false;
        if(runtime.restored.contains(actor.getUUID())) return true;
        runtime.captureFire(actor);
        return true;
    }
    private RoundTime time(LivingEntity actor) { return engine.roundTime(engine.encounterOf(actor.getUUID())); }
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
        if(!value.equals(owners.put(id,value))) service.vanillaEffects().participantEffectsChanged();
    }
    private List<Timer> captureEffect(LivingEntity actor,MobEffectInstance effect) {
        var previous=owner(actor.getUUID()); var old=previous.effects().getOrDefault(key(effect),List.of());
        var nativeChain=chain(effect); var captured=new ArrayList<Timer>();
        for(int i=0;i<nativeChain.size();i++) {
            var current=nativeChain.get(i); var access=(EffectTimerAccess)current;
            Timer existing=i<old.size()?old.get(i):null;
            if(existing!=null && existing.instance().equals(access.dndturn$timerInstance())
                && existing.amplifier()==current.getAmplifier() && existing.expectedNativeTicks()==current.getDuration()) {
                captured.add(existing); continue;
            }
            var duration=RoundDuration.capture(time(actor),current.getDuration());
            access.dndturn$duration(duration.nativeTicks());
            captured.add(new Timer(access.dndturn$timerInstance(),current.getAmplifier(),current.getDuration(),duration));
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
        var captured=new Timer(instance,0,actor.getRemainingFireTicks(),duration);
        save(actor.getUUID(),previous.effects(),captured); return captured;
    }
    /** Validate persisted expectations before adopting new in-memory identities. Never replay effects. */
    public boolean reconcile(LivingEntity actor) {
        if(!restored.contains(actor.getUUID())) return true;
        var saved=owners.get(actor.getUUID()); var rebound=new HashMap<String,List<Timer>>();
        for(var entry:saved.effects().entrySet()) {
            var effect=actor.getActiveEffects().stream().filter(e->key(e).equals(entry.getKey())).findFirst().orElse(null);
            if(effect==null || !VanillaEffectTypes.supported(effect)) return false;
            var nativeChain=chain(effect); if(nativeChain.size()!=entry.getValue().size()) return false;
            var timers=new ArrayList<Timer>();
            for(int i=0;i<nativeChain.size();i++) {
                var current=nativeChain.get(i); var old=entry.getValue().get(i);
                if(current.getDuration()!=old.expectedNativeTicks() || current.getAmplifier()!=old.amplifier()) return false;
                timers.add(new Timer(((EffectTimerAccess)current).dndturn$timerInstance(),old.amplifier(),old.expectedNativeTicks(),old.duration()));
            }
            rebound.put(entry.getKey(),List.copyOf(timers));
        }
        Timer fire=saved.fire();
        if(fire!=null) {
            if(!(actor instanceof Mob) || actor.getRemainingFireTicks()!=fire.expectedNativeTicks()) return false;
            fire=new Timer(((PresentationIdentity)actor).dndturn$presentationInstance(),0,fire.expectedNativeTicks(),fire.duration());
        }
        save(actor.getUUID(),rebound,fire); restored.remove(actor.getUUID()); return true;
    }
    /** Also samples newly applied effects when END_TURN arrives before another body tick. */
    public void capture(LivingEntity actor) {
        groupEffects.capture(service, actor);
        ActorLifecycleAdapters.capture(service, actor);
        if(restored.contains(actor.getUUID()) && !reconcile(actor)) throw new IllegalStateException("effect restore evidence mismatch");
        var present=new HashSet<String>();
        for(var effect:List.copyOf(actor.getActiveEffects())) if(VanillaEffectTypes.supported(effect)) {
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
        forget(actor.getUUID()); // native timers already represent remaining rounds * captured base
    }
    public void forget(UUID owner) { groupEffects.forget(owner); if(owners.remove(owner)!=null) service.vanillaEffects().participantEffectsChanged(); restored.remove(owner); }
    private void evidence(OperationRecord.Snapshot root,Phase phase,List<Observation> observations) {
        settlements.put(root.operationId(),new Settlement(root.operationId(),root.encounterId(),root.owner(),POLICY,VERSION,phase,observations));
        service.vanillaEffects().participantEffectsChanged();
    }
    public void settle(LivingEntity actor,OperationRecord.Snapshot root) {
        if(CURRENT.get()!=null || settlements.containsKey(root.operationId())
            || !root.equals(engine.pendingOperation(root.encounterId(),root.operationId()))
            || root.kind()!=OperationRecord.Kind.END_TURN || !root.owner().equals(actor.getUUID()))
            throw new IllegalStateException("turn settlement identity unavailable");
        capture(actor);
        var observations=new ArrayList<Observation>(); evidence(root,Phase.PREPARED,observations);
        try {
            for(var effect:List.copyOf(actor.getActiveEffects())) {
                if(!actor.isAlive()) break;
                if(!VanillaEffectTypes.supported(effect) || actor.getEffect(effect.getEffect())!=effect) continue;
                settleEffect(actor,effect,root,observations);
            }
            if(actor.isAlive() && actor instanceof Mob mob) settleFire(mob,root,observations);
            evidence(root,Phase.OBSERVED,observations);
        } catch(RuntimeException failure) {
            evidence(root,Phase.UNKNOWN,observations); throw failure;
        } finally { CURRENT.remove(); }
    }
    private void settleEffect(LivingEntity actor,MobEffectInstance effect,OperationRecord.Snapshot root,List<Observation> observations) {
        var timers=captureEffect(actor,effect); var initial=timers.getFirst();
        boolean remains=initial.duration().active();
        if(remains) {
            int basePeriod=effect.getEffect().equals(MobEffects.POISON)?25:effect.getEffect().equals(MobEffects.WITHER)?40
                :effect.getEffect().equals(MobEffects.REGENERATION)?50:0;
            int period=basePeriod==0?1:Math.max(1,basePeriod>>effect.getAmplifier());
            var frame=new Frame(this,root,actor,key(effect),effect.getAmplifier(),initial.duration().time(),period);
            float health=actor.getHealth(),absorption=actor.getAbsorptionAmount();
            CURRENT.set(frame);
            try {
                // Base MobEffect.applyEffectTick has no effect. Absorption keeps its native early expiration check.
                remains=effect.getEffect().value().applyEffectTick((ServerLevel)actor.level(),actor,effect.getAmplifier());
                observations.add(new Observation(frame.effect,frame.amplifier,frame.time,frame.amount,health,actor.getHealth(),
                    absorption,actor.getAbsorptionAmount(),frame.accepted,Phase.OBSERVED));
                evidence(root,Phase.PREPARED,observations);
            } catch(RuntimeException failure) {
                observations.add(new Observation(frame.effect,frame.amplifier,frame.time,frame.amount,health,actor.getHealth(),
                    absorption,actor.getAbsorptionAmount(),frame.accepted,Phase.UNKNOWN)); throw failure;
            } finally { CURRENT.remove(); }
            if(remains && actor.getEffect(effect.getEffect())==effect) {
                // Native callbacks can replace/refresh a timer. Never overwrite a changed instance or chain.
                var after=chain(effect);
                if(after.size()!=timers.size()) { captureEffect(actor,effect); return; }
                for(int i=0;i<after.size();i++) if(!timers.get(i).instance().equals(((EffectTimerAccess)after.get(i)).dndturn$timerInstance())
                    || timers.get(i).expectedNativeTicks()!=after.get(i).getDuration() || timers.get(i).amplifier()!=after.get(i).getAmplifier()) {
                    captureEffect(actor,effect); return;
                }
                var advanced=new ArrayList<Timer>();
                for(int i=0;i<after.size();i++) {
                    var duration=timers.get(i).duration().endTurn(); var current=after.get(i);
                    ((EffectTimerAccess)current).dndturn$duration(duration.nativeTicks());
                    advanced.add(new Timer(timers.get(i).instance(),current.getAmplifier(),current.getDuration(),duration));
                }
                boolean promoted=((EffectTimerAccess)effect).dndturn$promoteHidden();
                if(promoted) {
                    advanced.removeFirst(); var top=advanced.getFirst();
                    advanced.set(0,new Timer(((EffectTimerAccess)effect).dndturn$timerInstance(),top.amplifier(),top.expectedNativeTicks(),top.duration()));
                }
                var owner=owner(actor.getUUID());var effects=new HashMap<>(owner.effects());effects.put(key(effect),List.copyOf(advanced));
                save(actor.getUUID(),effects,owner.fire());
                ((LivingEffectRoundAccess)actor).dndturn$effectUpdated(effect,promoted,null);
                remains=effect.isInfiniteDuration() || effect.getDuration()>0;
            }
        }
        if(!remains && actor.getEffect(effect.getEffect())==effect
            && !NeoForge.EVENT_BUS.post(new MobEffectEvent.Expired(actor,effect)).isCanceled()) {
            if(actor.getEffect(effect.getEffect())==effect) {
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
                    save(actor.getUUID(),previous.effects(),duration.active()?new Timer(timer.instance(),0,duration.nativeTicks(),duration):null);
                }
            }
            observations.add(new Observation(frame.effect,0,frame.time,frame.amount,health,actor.getHealth(),absorption,
                actor.getAbsorptionAmount(),frame.accepted,Phase.OBSERVED));
        } catch(RuntimeException failure) {
            observations.add(new Observation(frame.effect,0,frame.time,frame.amount,health,actor.getHealth(),absorption,
                actor.getAbsorptionAmount(),frame.accepted,Phase.UNKNOWN));throw failure;
        } finally { CURRENT.remove(); }
    }
    public static void heal(LivingEntity target,float nativeAmount) {
        var frame=CURRENT.get();
        if(frame==null || frame.actor!=target) { target.heal(nativeAmount);return; }
        if(!frame.effect.equals("minecraft:regeneration") || frame.invoked) throw new IllegalStateException("unbound periodic healing");
        frame.invoked=true; frame.amount=frame.time.periodicAmount(nativeAmount,frame.period);
        target.heal((float)frame.amount); frame.accepted=true;
    }
    public static boolean hurt(ServerLevel level,LivingEntity target,DamageSource source,float nativeAmount) {
        var frame=CURRENT.get();
        if(frame==null || frame.actor!=target) return target.hurtServer(level,source,nativeAmount);
        boolean sourceMatches=switch(frame.effect) {
            case "minecraft:poison" -> source.is(net.neoforged.neoforge.common.NeoForgeMod.POISON_DAMAGE) || source.is(DamageTypes.MAGIC);
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
            Set.of(root.owner()),Set.of(EncounterPhase.CANDIDATE,EncounterPhase.ACTIVE),1,engine.stateView(root.encounterId()).round()));
        var child=new OperationRecord.Snapshot(operation,root.operationId(),root.encounterId(),root.owner(),root.owner(),root.owner(),
            frame.runtime.service.actionHost().planClock(),engine.stateView(root.encounterId()).version(),root.sourceCell(),root.sourceCell(),
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
