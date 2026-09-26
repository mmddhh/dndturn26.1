package cc.sighs.dndturn.client;

import cc.sighs.dndturn.combat.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.SwingAnimation;

/** Opt-in physical-client assertions using disposable client entities and explicit test projections. */
final class PresentationOwnershipRegression {
    private static final List<Integer> fixtures = new ArrayList<>();
    private static net.minecraft.client.renderer.entity.ZombieRenderer custom;
    static void customRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
        custom = new net.minecraft.client.renderer.entity.ZombieRenderer(context) {
            @Override public void extractRenderState(Zombie entity, ZombieRenderState state, float partial) {
                super.extractRenderState(entity, state, partial);
                state.ageInTicks = 1234; state.walkAnimationPos = 67; state.attackTime = .75F;
            }
        };
    }
    private static void checkCustomRenderer(Entity entity) {
        var dispatcher = (cc.sighs.dndturn.mixin.client.PresentationRendererProbeAccessor)Minecraft.getInstance().getEntityRenderDispatcher();
        var original = dispatcher.dndturn$renderers();
        var replacement = new HashMap<>(original); replacement.put(EntityType.ZOMBIE, custom);
        try {
            dispatcher.dndturn$renderers(replacement);
            var state = (ZombieRenderState)state(entity);
            require(!PresentationAdapters.supported(entity) && state.ageInTicks == 1234
                && state.walkAnimationPos == 67 && state.attackTime == .75F, "custom renderer fields overwritten");
        } finally { dispatcher.dndturn$renderers(original); }
    }
    private static UUID generation, firstDomain, secondDomain, operation;
    private static long sequence = 1_000_000;
    private static int ticks;
    private static float speed, frozenAge;
    private static CombatNetwork.TacticalSwing swing;
    private PresentationOwnershipRegression() {}

    static boolean tick() {
        var mc = Minecraft.getInstance();
        if (ticks == 0) {
            generation = ClientEntitySimulation.projection(mc.player).generation();
            firstDomain = UUID.randomUUID(); secondDomain = UUID.randomUUID(); operation = UUID.randomUUID();
            for (var type : List.of(EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.BAT, EntityType.ITEM, EntityType.FROG, EntityType.WARDEN)) {
                var entity = type.create(mc.level, EntitySpawnReason.COMMAND);
                entity.setId(-7000 - fixtures.size()); entity.tickCount = 40;
                entity.setPos(mc.player.getX() + 2, mc.player.getY(), mc.player.getZ());
                if (entity instanceof net.minecraft.world.entity.item.ItemEntity item) item.setItem(new ItemStack(Items.APPLE));
                mc.level.addEntity(entity); fixtures.add(entity.getId());
                project(entity, fixtures.size() == 2 ? secondDomain : firstDomain, null, PresentationState.Use.STOPPED);
            }
        }
        Entity a = entity(0), b = entity(1);
        if (ticks == 1) {
            project(a, firstDomain, new PresentationState.Movement(operation, 0, 1, PresentationState.Motion.ACTIVE, .125), PresentationState.Use.STOPPED);
            require(state(entity(4)).ageInTicks == entity(4).tickCount + .5F, "Frog age was overwritten");
            require(state(entity(5)).ageInTicks == entity(5).tickCount + .5F, "Warden age was overwritten");
            require(!PresentationAdapters.supported(entity(4)) && !PresentationAdapters.supported(entity(5)), "unknown models admitted");
            checkUse((Zombie)b);
            checkCustomRenderer(b);
        }
        if (ticks == 2) {
            speed = gait(a);
            require(speed > 0, "active evidence did not produce gait");
            project(a, firstDomain, new PresentationState.Movement(operation, 0, 1, PresentationState.Motion.ACTIVE, .125), PresentationState.Use.STOPPED);
            a.setPos(a.getX() + .1, a.getY(), a.getZ()); // A short correction is not walking evidence.
        }
        if (ticks >= 3 && ticks <= 6) {
            float current = gait(a);
            require(current < speed, "duplicate/forced/passive/mixed/correction evidence advanced gait at " + ticks + ": " + speed + " -> " + current); speed = current;
            var cause = PresentationState.Motion.values()[ticks - 2];
            project(a, firstDomain, new PresentationState.Movement(operation, ticks, ticks, cause, .5), PresentationState.Use.STOPPED);
        }
        if (ticks == 6) {
            var baseline = ClientEntitySimulation.projection(a);
            swing = new CombatNetwork.TacticalSwing(generation, firstDomain, a.getUUID(), a.getId(), baseline.instance(),
                UUID.randomUUID(), ++sequence, InteractionHand.OFF_HAND, new SwingAnimation(SwingAnimationType.STAB, 11));
            require(ClientPresentation.swing(swing) && !ClientPresentation.swing(swing), "swing event dedup failed");
            project(a, null, null, PresentationState.Use.STOPPED);
            ((LivingEntity)b).hurtTime = 10; ClientPresentation.hurt(b);
        }
        if (ticks == 10) {
            require(state(a).ageInTicks == a.tickCount + .5F, "age ownership survived four normal ticks");
            require(state(b).ageInTicks > b.tickCount + 5, "release cleared another encounter");
            require(state(entity(2)).ageInTicks > entity(2).tickCount + 5, "Bat presentation time did not advance");
            require(((BatRenderState)state(entity(2))).flyAnimationState.isStarted(), "Bat fly origin not owned");
            require(state(entity(3)).ageInTicks > entity(3).tickCount + 5, "item bob/spin time did not advance");
            var armed = (ArmedEntityRenderState)state(a);
            require(Math.abs(armed.attackTime - ClientPresentation.attack(a, .5F, 0)) < .0001F
                && ClientPresentation.attackHand(a, InteractionHand.MAIN_HAND) == InteractionHand.OFF_HAND
                && armed.swingAnimationType == SwingAnimationType.STAB, "hand and body swing diverged");
        }
        if (ticks == 11) mc.player.connection.sendCommand("tick freeze");
        if (ticks == 12) { if (!mc.level.tickRateManager().isFrozen()) return false; frozenAge = state(b).ageInTicks; }
        if (ticks == 13 || ticks == 14) require(state(b).ageInTicks == frozenAge, "global freeze advanced presentation clock");
        if (ticks == 14) mc.player.connection.sendCommand("tick unfreeze");
        if (ticks == 15 && mc.level.tickRateManager().isFrozen()) return false;
        if (ticks == 24) {
            require(!ClientPresentation.swing(swing), "completed event replayed after pose release");
            require(((ArmedEntityRenderState)state(a)).attackTime == ((LivingEntity)a).getAttackAnim(.5F), "swing ownership did not end");
            require(!((LivingEntityRenderState)state(b)).hasRedOverlay && ((LivingEntity)b).hurtTime == 10, "consumed frozen hurt replayed");
            project(b, null, null, PresentationState.Use.STOPPED);
            mc.getConnection().handleAnimate(new net.minecraft.network.protocol.game.ClientboundAnimatePacket(a,
                net.minecraft.network.protocol.game.ClientboundAnimatePacket.SWING_MAIN_HAND));
            require(((LivingEntity)a).swinging, "ordinary vanilla swing was cancelled");
        }
        if (ticks == 25) require(!((LivingEntityRenderState)state(b)).hasRedOverlay, "release replayed consumed hurt");
        if (ticks == 26) {
            ((LivingEntity)b).hurtTime = 10; ClientPresentation.hurt(b);
            require(((LivingEntityRenderState)state(b)).hasRedOverlay, "new hurt event was suppressed");
            var stale = ClientEntitySimulation.projection(a);
            var uuid = a.getUUID(); int id = a.getId();
            mc.level.removeEntity(id, Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var replacement = EntityType.ZOMBIE.create(mc.level, EntitySpawnReason.COMMAND);
            replacement.setUUID(uuid); replacement.setId(id); replacement.setPos(mc.player.position());
            mc.level.addEntity(replacement);
            ClientEntitySimulation.accept(stale);
            require(ClientEntitySimulation.projection(replacement) == null, "unload/UUID/runtime ID reuse retained old baseline");
            require(!ClientPresentation.swing(swing), "stale event acquired replacement entity");
            // A later complete baseline can bind the replacement; old generation cannot overwrite it.
            project(replacement, firstDomain, null, PresentationState.Use.STOPPED);
            var current = ClientEntitySimulation.projection(replacement);
            ClientEntitySimulation.accept(new CombatNetwork.EntitySimulation(UUID.randomUUID(), ++sequence, replacement.getUUID(), id,
                UUID.randomUUID(), current.dimension(), true, current.facts(), null));
            require(ClientEntitySimulation.projection(replacement).generation().equals(generation), "foreign generation replaced baseline");
        }
        if (++ticks < 29) return false;
        for (int id : fixtures) mc.level.removeEntity(id, Entity.RemovalReason.DISCARDED);
        fixtures.clear();
        return true;
    }
    private static void checkUse(Zombie entity) {
        entity.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BOW));
        var use = new PresentationState.Use(UUID.randomUUID(), InteractionHand.OFF_HAND,
            TacticalItems.revision(entity, entity.getOffhandItem()), 7, 71993);
        project(entity, secondDomain, null, use);
        var rendered = (HumanoidRenderState)state(entity);
        require(rendered.isUsingItem && rendered.ticksUsingItem == 7 && rendered.useItemHand == InteractionHand.OFF_HAND,
            "Humanoid use snapshot diverged");
        require(new net.minecraft.client.renderer.item.properties.numeric.UseDuration(false).get(entity.getOffhandItem(),
            Minecraft.getInstance().level, entity, 0) == 7, "bow item model diverged");
        entity.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CROSSBOW));
        require(PresentationUse.snapshot(entity) == null, "changed item reused old progress");
        use = new PresentationState.Use(UUID.randomUUID(), InteractionHand.OFF_HAND,
            TacticalItems.revision(entity, entity.getOffhandItem()), 8, 71992);
        project(entity, secondDomain, null, use);
        require(new net.minecraft.client.renderer.item.properties.numeric.CrossbowPull().get(entity.getOffhandItem(),
            Minecraft.getInstance().level, entity, 0) == 8F / CrossbowItem.getChargeDuration(entity.getOffhandItem(), entity), "crossbow model diverged");
        project(entity, secondDomain, null, PresentationState.Use.STOPPED);
        require(!((HumanoidRenderState)state(entity)).isUsingItem && PresentationUse.remaining(entity) == 0, "stop retained use progress");
    }
    private static float gait(Entity entity) {
        return ((LivingEntityRenderState)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity, 1)).walkAnimationSpeed;
    }
    private static Entity entity(int index) { return Minecraft.getInstance().level.getEntity(fixtures.get(index)); }
    private static EntityRenderState state(Entity entity) {
        return Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity, .5F);
    }
    private static void project(Entity entity, UUID controller, PresentationState.Movement movement, PresentationState.Use use) {
        var previous = ClientEntitySimulation.projection(entity);
        UUID instance = previous == null ? UUID.randomUUID() : previous.instance();
        ClientEntitySimulation.accept(new CombatNetwork.EntitySimulation(generation, ++sequence, entity.getUUID(), entity.getId(), instance,
            entity.level().dimension().identifier().toString(), true,
            new PresentationState.Facts(controller, controller, controller == null ? "RELEASED" : "CANDIDATE", controller != null, use), movement));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
