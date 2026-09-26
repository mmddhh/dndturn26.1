package cc.sighs.dndturn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Opt-in physical client probe, called only by the existing control regression harness. */
public final class ClientPresentationRegression {
    public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
        if (Boolean.getBoolean("dndturn.controlProbe.visual")) PresentationOwnershipRegression.customRenderer(event.getContext());
    }
    private static int ticks;
    private static Vec3 position, velocity;
    private static int bodyTicks, duration, swingTime, playerDuration;
    private static float age;
    private static java.util.UUID target;
    private ClientPresentationRegression() {}

    static boolean tick() {
        var mc = Minecraft.getInstance();
        if (ticks == 0) {
            mc.options.framerateLimit().set(Integer.getInteger("dndturn.controlProbe.fps", 60));
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof net.minecraft.world.entity.monster.zombie.Zombie
                    && ClientEntitySimulation.paused(entity)) { target = entity.getUUID(); break; }
            }
            if (target == null || !ClientCombatState.bodySimulationPaused()) return false;
        }
        LivingEntity entity = null;
        for (Entity candidate : mc.level.entitiesForRendering())
            if (candidate.getUUID().equals(target)) entity = (LivingEntity)candidate;
        require(entity != null, "fixture vanished");
        var renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        var state = (ArmedEntityRenderState)renderer.createRenderState(entity, .5F);
        if (ticks++ == 0) {
            position = entity.position(); velocity = entity.getDeltaMovement(); bodyTicks = entity.tickCount;
            swingTime = entity.swingTime;
            duration = entity.getActiveEffects().stream().mapToInt(e -> e.getDuration()).sum();
            playerDuration = mc.player.getActiveEffects().stream().mapToInt(e -> e.getDuration()).sum();
            require(playerDuration > 0, "local fixture lacks an active effect");
            // Remote clients receive synchronized particle data, not active effect instances.
            require(!entity.getEntityData().get(cc.sighs.dndturn.mixin.client.LivingEffectParticlesAccessor.dndturn$particles()).isEmpty(),
                "fixture lacks synchronized effect particles");
            age = state.ageInTicks;
            sendSwing(entity, net.minecraft.world.InteractionHand.OFF_HAND);
            sendSwing(mc.player, net.minecraft.world.InteractionHand.OFF_HAND);
            return false;
        }
        require(entity.position().equals(position) && entity.getDeltaMovement().equals(velocity)
            && entity.tickCount == bodyTicks && entity.swingTime == swingTime, "presentation changed frozen body");
        require(entity.getActiveEffects().stream().mapToInt(e -> e.getDuration()).sum() == duration,
            "presentation advanced effect duration");
        require(mc.player.getActiveEffects().stream().mapToInt(e -> e.getDuration()).sum() == playerDuration,
            "presentation advanced local effect duration");
        var second = (ArmedEntityRenderState)renderer.createRenderState(entity, .5F);
        require(state.ageInTicks == second.ageInTicks && state.attackTime == second.attackTime,
            "render extraction advanced presentation");
        if (ticks == 3) {
            require(state.attackTime > 0 && state.attackTime < 1, "remote swing did not advance");
            var local = (ArmedEntityRenderState)mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player, .5F);
            require(local.attackTime > 0 && local.attackTime < 1, "avatar modifier missed local swing");
        }
        if (ticks < 25) return false;
        require(state.ageInTicks > age + 15 && state.attackTime == 0 && state.walkAnimationSpeed < .001,
            "idle clock or bounded swing/walk completion failed");
        return PresentationOwnershipRegression.tick();
    }

    private static void sendSwing(LivingEntity entity, net.minecraft.world.InteractionHand hand) {
        var state = ClientEntitySimulation.projection(entity);
        require(state != null, "missing server baseline");
        var swing = new cc.sighs.dndturn.combat.CombatNetwork.TacticalSwing(state.generation(), state.facts().member(),
            entity.getUUID(), entity.getId(), state.instance(), java.util.UUID.randomUUID(), state.sequence(), hand,
            entity.getItemInHand(hand).getSwingAnimation());
        require(ClientPresentation.swing(swing), "tactical event rejected");
        require(!ClientPresentation.swing(swing), "duplicate swing replayed");
    }

    private static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
}
