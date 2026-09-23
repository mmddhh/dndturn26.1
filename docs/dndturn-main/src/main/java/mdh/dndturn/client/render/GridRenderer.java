package mdh.dndturn.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mdh.dndturn.Dndturn;
import mdh.dndturn.client.ClientCombatState;
import mdh.dndturn.client.camera.TacticalCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = Dndturn.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GridRenderer {

    private GridRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!ClientCombatState.inCombat) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        updateHovered(event, minecraft);

        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        Matrix4f matrix = pose.last().pose();

        VertexConsumer quads = buffer.getBuffer(RenderType.debugQuads());
        for (BlockPos pos : ClientCombatState.reachable) {
            addQuad(quads, matrix, pos, cellOffset(minecraft, pos), 0.20F, 0.85F, 0.35F, 0.22F);
        }
        BlockPos hovered = TacticalCamera.instance().getHovered();
        if (hovered != null && ClientCombatState.inCombat) {
            float yOff = (float) (TacticalCamera.instance().getHoverY() + 0.02D - hovered.getY());
            addQuad(quads, matrix, hovered, yOff, 1.0F, 1.0F, 1.0F, 0.30F);
        }
        if (ClientCombatState.attackMode && minecraft.level != null) {
            for (mdh.dndturn.network.packet.CombatStateS2C.Entry entry : ClientCombatState.entries) {
                if (mdh.dndturn.core.Combatant.SIDE_PLAYERS.equals(entry.side())) {
                    continue;
                }
                net.minecraft.world.entity.Entity entity = minecraft.level.getEntity(entry.networkId());
                if (entity != null && entity.isAlive()) {
                    addQuad(quads, matrix, entity.blockPosition(), 0.02F, 0.9F, 0.15F, 0.15F, 0.40F);
                }
            }
        }

        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        AABB border = new AABB(
                ClientCombatState.gridMinX, ClientCombatState.floorY + 0.03D, ClientCombatState.gridMinZ,
                ClientCombatState.gridMaxX + 1.0D, ClientCombatState.floorY + 0.03D, ClientCombatState.gridMaxZ + 1.0D);
        LevelRenderer.renderLineBox(pose, lines, border, 0.35F, 0.45F, 0.85F, 0.85F);
        if (hovered != null) {
            double hoverY = TacticalCamera.instance().getHoverY();
            AABB hoverBox = new AABB(hovered.getX(), hoverY + 0.02D, hovered.getZ(),
                    hovered.getX() + 1.0D, hoverY + 0.02D, hovered.getZ() + 1.0D);
            LevelRenderer.renderLineBox(pose, lines, hoverBox, 1.0F, 1.0F, 1.0F, 1.0F);
        }

        buffer.endBatch();
        pose.popPose();
    }

    private static void addQuad(VertexConsumer consumer, Matrix4f matrix, BlockPos pos, float yOffset,
                                float r, float g, float b, float a) {
        float y = pos.getY() + yOffset;
        float x0 = pos.getX();
        float x1 = x0 + 1.0F;
        float z0 = pos.getZ();
        float z1 = z0 + 1.0F;
        consumer.vertex(matrix, x0, y, z0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x1, y, z0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x1, y, z1).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x0, y, z1).color(r, g, b, a).endVertex();
    }

    private static float cellOffset(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level != null && minecraft.level.getFluidState(pos).is(FluidTags.WATER)) {
            return 0.85F;
        }
        return 0.02F;
    }

    private static void updateHovered(RenderLevelStageEvent event, Minecraft minecraft) {
        TacticalCamera camera = TacticalCamera.instance();
        if (!camera.isActive()) {
            camera.setHovered(null);
            return;
        }
        int width = minecraft.getWindow().getScreenWidth();
        int height = minecraft.getWindow().getScreenHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        float ndcX = (float) (2.0D * mouseX / width - 1.0D);
        float ndcY = (float) (1.0D - 2.0D * mouseY / height);

        Vec3 origin = event.getCamera().getPosition();
        Vector3f forward = new Vector3f(event.getCamera().getLookVector()).normalize();
        Vector3f up = new Vector3f(event.getCamera().getUpVector()).normalize();
        Vector3f right = new Vector3f(forward).cross(up);
        if (right.lengthSquared() < 1.0E-8F) {
            camera.setHovered(null);
            return;
        }
        right.normalize();

        double fovDegrees = minecraft.options.fov().get();
        double tanY = Math.tan(Math.toRadians(fovDegrees) * 0.5D);
        double aspect = (double) width / (double) height;
        double tanX = tanY * aspect;

        Vector3f direction = new Vector3f(forward)
                .add(right.mul((float) (ndcX * tanX)))
                .add(up.mul((float) (ndcY * tanY)))
                .normalize();

        Vec3 end = origin.add(direction.x * 256.0D, direction.y * 256.0D, direction.z * 256.0D);
        BlockHitResult hit = minecraft.level.clip(new ClipContext(origin, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, minecraft.player));
        if (hit.getType() == HitResult.Type.MISS) {
            camera.setHovered(null);
            return;
        }
        BlockPos hitPos = hit.getBlockPos();
        int cellX = hitPos.getX();
        int cellZ = hitPos.getZ();
        if (cellX < ClientCombatState.gridMinX || cellX > ClientCombatState.gridMaxX
                || cellZ < ClientCombatState.gridMinZ || cellZ > ClientCombatState.gridMaxZ) {
            camera.setHovered(null);
            return;
        }
        BlockPos matched = null;
        for (BlockPos pos : ClientCombatState.reachable) {
            if (pos.getX() == cellX && pos.getZ() == cellZ) {
                matched = pos;
                break;
            }
        }
        camera.setHoverY(hit.getLocation().y);
        camera.setHovered(matched != null ? matched : new BlockPos(cellX, ClientCombatState.floorY, cellZ));
    }
}
