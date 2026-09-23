package cc.sighs.dndturn.platform.client.presentation;

import cc.sighs.dndturn.platform.client.simulation.ClientEntitySimulation;
import cc.sighs.dndturn.platform.client.state.ClientCombatState;
import cc.sighs.dndturn.platform.mixin.client.presentation.LivingEffectParticlesAccessor;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import cc.sighs.dndturn.platform.projection.PresentationState;
import com.google.common.reflect.TypeToken;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.component.SwingAnimation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** AGE owns a monotonic decorative offset for the tracked renderer/instance lifetime.
 * Pose channels hand off separately; no simulation clock is modified. */
public final class ClientPresentation {
    private static final Map<UUID, Pose> poses = new HashMap<>();
    private static final RandomSource random = RandomSource.create();
    private ClientPresentation() {}

    private static final class Pose {
        final int entityId;
        final UUID generation, instance, localInstance;
        final Object renderer;
        final List<PresentationAdapters.State> channels;
        final Set<PresentationAdapters.Channel> ownership;
        List<PresentationAdapters.Frame> frames = List.of();
        float age, oldAge;
        int handoff, swing = -1, hurt, previousHurt, death;
        boolean controlled, suppressHurt, ageOffset;
        long movementSequence = -1, swingSequence = -1;
        UUID movementOperation, swingOperation;
        int movementStep = -1;
        double horizontal;
        InteractionHand hand = InteractionHand.MAIN_HAND;
        SwingAnimation animation = SwingAnimation.DEFAULT;
        Pose(Entity entity, EncounterProtocol.EntitySimulation state) {
            entityId = entity.getId(); generation = state.generation(); instance = state.instance();
            localInstance = ((PresentationIdentity)entity).dndturn$presentationInstance();
            age = oldAge = entity.tickCount;
            renderer = PresentationAdapters.renderer(entity);
            var adapters = PresentationAdapters.resolve(entity);
            channels = adapters.stream().map(a -> a.create().apply(entity)).toList();
            var owned = EnumSet.noneOf(PresentationAdapters.Channel.class);
            adapters.forEach(a -> owned.addAll(a.channels())); ownership = Set.copyOf(owned);
            if (entity instanceof LivingEntity living) {
                hurt = previousHurt = living.hurtTime; death = living.deathTime;
            }
        }
        void ownership(boolean next) {
            if (controlled && !next) handoff = 4;
            if (next) handoff = 0;
            if (next && ownership.contains(PresentationAdapters.Channel.AGE)) ageOffset = true;
            controlled = next;
        }
        boolean fields() { return controlled || handoff > 0; }
    }

    public static void clear() { poses.clear(); PresentationUse.clear(); }
    public static void forget(UUID entity) { poses.remove(entity); PresentationUse.clear(); }
    public static void leave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) ClientEntitySimulation.leave(event.getEntity());
    }
    private static Pose pose(Entity entity, EncounterProtocol.EntitySimulation state) {
        Pose p = poses.get(entity.getUUID());
        if (p == null || p.renderer != PresentationAdapters.renderer(entity) || p.entityId != entity.getId() || !p.instance.equals(state.instance())
            || !p.generation.equals(state.generation()) || !p.localInstance.equals(((PresentationIdentity)entity).dndturn$presentationInstance())) {
            p = new Pose(entity, state); poses.put(entity.getUUID(), p);
        }
        return p;
    }
    public static boolean supported(Entity entity) { return PresentationAdapters.supported(entity); }

    public static void project(Entity entity, EncounterProtocol.EntitySimulation state) {
        if (!supported(entity)) return;
        boolean controlled = state.facts().paused();
        if (!controlled && !state.facts().use().active() && !poses.containsKey(entity.getUUID())) return;
        Pose p = pose(entity, state);
        p.ownership(controlled);
        var movement = state.movement();
        if (controlled && movement != null && movement.sequence() > p.movementSequence) {
            boolean duplicate = movement.operation().equals(p.movementOperation) && movement.step() <= p.movementStep;
            p.movementSequence = movement.sequence();
            p.movementOperation = movement.operation(); p.movementStep = movement.step();
            if (!duplicate && movement.cause() == PresentationState.Motion.ACTIVE) p.horizontal += movement.horizontal();
        }
    }
    public static void register(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, ClientPresentation::render);
    }
    private static boolean clockPaused() {
        var mc = Minecraft.getInstance();
        return mc.isPaused() || mc.level == null || !mc.level.tickRateManager().runsNormally();
    }
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() == null) { clear(); return; }
        PresentationUse.beginUpdate();
        if (clockPaused()) return;
        var seen = new HashSet<UUID>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.isRemoved()) continue;
            var state = ClientEntitySimulation.projection(entity);
            // .84 TNT smoke is pure visual. Never run its gravity/move/fuse/discard path to emit it.
            if (entity.getClass() == net.minecraft.world.entity.item.PrimedTnt.class
                && ClientEntitySimulation.consumeEnvironmentVisual(entity)
                && ((net.minecraft.world.entity.item.PrimedTnt)entity).getFuse() > 0)
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    entity.getX(), entity.getY() + .5, entity.getZ(), 0, 0, 0);
            // Particle bridge has its own body-pause scope, independent of model field ownership.
            if (entity instanceof LivingEntity living && (ClientEntitySimulation.paused(entity)
                || entity == mc.player && ClientCombatState.holdBodySubsystemsDuringMovement())) particles(living);
            if (state == null || !supported(entity)) continue;
            project(entity, state);
            Pose p = poses.get(entity.getUUID());
            if (p == null) continue;
            seen.add(entity.getUUID());
            p.oldAge = p.age;
            p.age = p.ageOffset ? Math.max(p.age + 1, entity.tickCount) : entity.tickCount;
            if (!p.controlled && p.handoff > 0) p.handoff--;
            if (p.swing >= 0 && ++p.swing >= p.animation.duration()) p.swing = -1;
            if (entity instanceof LivingEntity living) {
                if (living.hurtTime > p.previousHurt) {
                    p.hurt = p.controlled ? living.hurtTime : 0; p.suppressHurt = false;
                } else if (p.hurt > 0 && --p.hurt == 0) p.suppressHurt = living.hurtTime > 0;
                if (living.hurtTime == 0) p.suppressHurt = false;
                p.previousHurt = living.hurtTime;
                p.death = living.isDeadOrDying() && p.fields() ? Math.max(p.death + 1, living.deathTime) : 0;
            }
            var attack = p.swing < 0 ? null : new PresentationAdapters.Attack(p.swingOperation,
                p.swingSequence, p.hand, p.animation, p.swing);
            var input = new PresentationAdapters.Input(p.controlled, p.age, p.horizontal,
                state.facts().phase(), state.facts().use(), attack, p.hurt, p.death);
            p.frames = p.channels.stream().map(channel -> channel.update(entity, input)).toList();
            p.horizontal = 0;
            // An AGE offset is deliberately retained until this tracked renderer/instance
            // retires. Other channels lose their write weight after four updates.
            if (!p.fields() && !p.ageOffset && !state.facts().use().active()
                && p.swing < 0 && p.hurt == 0 && !p.suppressHurt) poses.remove(entity.getUUID());
        }
        poses.keySet().retainAll(seen);
    }

    public static void receiveSwing(EncounterProtocol.TacticalSwing event, IPayloadContext context) {
        var connection = context.connection(); var level = Minecraft.getInstance().level;
        context.enqueueWork(() -> {
            ClientCombatState.refreshSession();
            var mc = Minecraft.getInstance();
            if (mc.getConnection() != null && mc.getConnection().getConnection() == connection && level != null && level == mc.level)
                swing(event);
        });
    }
    static boolean swing(EncounterProtocol.TacticalSwing event) {
        var mc = Minecraft.getInstance();
        Entity entity = mc.level == null ? null : mc.level.getEntity(event.runtimeId());
        var state = ClientEntitySimulation.projection(entity);
        if (state == null || !supported(entity) || !(entity instanceof LivingEntity) || !event.entity().equals(entity.getUUID())
            || !state.generation().equals(event.generation()) || !state.instance().equals(event.instance())
            || !event.encounter().equals(state.facts().member())) return false;
        if (!ClientEntitySimulation.consumeSwing(event)) return false;
        Pose p = pose(entity, state);
        if (!p.ownership.contains(PresentationAdapters.Channel.ATTACK)) return false;
        if (event.sequence() <= p.swingSequence || event.operation().equals(p.swingOperation)) return false;
        p.swingSequence = event.sequence(); p.swingOperation = event.operation();
        p.hand = event.hand(); p.animation = event.animation(); p.swing = 0;
        return true;
    }
    public static void hurt(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !supported(entity)) return;
        var state = ClientEntitySimulation.projection(entity);
        if (state == null) return;
        Pose p = poses.get(entity.getUUID());
        if (state.facts().paused()) p = pose(entity, state);
        if (p != null) {
            p.hurt = state.facts().paused() ? living.hurtTime : 0;
            p.previousHurt = living.hurtTime; p.suppressHurt = false;
        }
    }
    private static Pose current(Entity entity) {
        if (entity == null || !supported(entity)) return null;
        var state = ClientEntitySimulation.projection(entity);
        var p = poses.get(entity.getUUID());
        return state != null && p != null && p.renderer == PresentationAdapters.renderer(entity) && p.instance.equals(state.instance()) && p.generation.equals(state.generation()) ? p : null;
    }
    public static float attack(Entity entity, float partial, float vanilla) {
        Pose p = current(entity);
        return p == null || !p.ownership.contains(PresentationAdapters.Channel.ATTACK) || p.swing < 0 ? vanilla : Math.min(1, (p.swing + (clockPaused() ? 1 : partial)) / p.animation.duration());
    }
    public static InteractionHand attackHand(Entity entity, InteractionHand vanilla) {
        Pose p = current(entity); return p == null || !p.ownership.contains(PresentationAdapters.Channel.ATTACK) || p.swing < 0 ? vanilla : p.hand;
    }
    public static SwingAnimation swingAnimation(Entity entity, InteractionHand hand, SwingAnimation vanilla) {
        Pose p = current(entity); return p == null || !p.ownership.contains(PresentationAdapters.Channel.ATTACK) || p.swing < 0 || hand != p.hand ? vanilla : p.animation;
    }

    private static void render(Entity entity, EntityRenderState state) {
        Pose p = current(entity);
        if (p == null) return;
        float partial = clockPaused() ? 1 : state.partialTick;
        float weight = p.controlled ? 1 : p.handoff / 4F;
        if (p.ageOffset)
            state.ageInTicks = Mth.lerp(partial, p.oldAge, p.age);
        for (var frame : p.frames) frame.render(state, weight, partial);
        if (state instanceof LivingEntityRenderState living && p.ownership.contains(PresentationAdapters.Channel.HURT)) {
            if (p.hurt > 0 || p.suppressHurt) living.hasRedOverlay = p.hurt > 0 || p.death > 0;
            if (p.death > 0 && p.fields()) { living.deathTime = p.death + partial; living.hasRedOverlay = true; }
        }
    }

    private static void particles(LivingEntity entity) {
        var data = entity.getEntityData();
        var particles = data.get(LivingEffectParticlesAccessor.dndturn$particles());
        if (particles.isEmpty()) return;
        int bound = (entity.isInvisible() ? 15 : 4) * (data.get(LivingEffectParticlesAccessor.dndturn$ambient()) ? 5 : 1);
        if (random.nextInt(bound) != 0) return;
        entity.level().addParticle(particles.get(random.nextInt(particles.size())),
            entity.getX() + (random.nextDouble() - .5) * entity.getBbWidth(),
            entity.getY() + random.nextDouble() * entity.getBbHeight(),
            entity.getZ() + (random.nextDouble() - .5) * entity.getBbWidth(), 1, 1, 1);
    }
}
