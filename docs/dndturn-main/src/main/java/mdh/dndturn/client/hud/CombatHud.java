package mdh.dndturn.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import mdh.dndturn.client.ClientCombatState;
import mdh.dndturn.network.packet.CombatStateS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

@OnlyIn(Dist.CLIENT)
public class CombatHud implements IGuiOverlay {

    private static final int PANEL_BG = 0xB0101018;
    private static final int BORDER = 0xFF6A6A8A;
    private static final int TEXT = 0xFFE8E8F0;
    private static final int TEXT_DIM = 0xFF9A9AA8;
    private static final int CURRENT = 0xFFFFD65A;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (!ClientCombatState.inCombat) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        PoseStack pose = graphics.pose();

        pose.pushPose();
        pose.translate(0, 0, 400);

        int panelX = 8;
        int panelY = 8;
        int entries = ClientCombatState.entries.size();
        int panelH = 26 + entries * 12 + 4;
        graphics.fill(panelX, panelY, panelX + 150, panelY + panelH, PANEL_BG);
        graphics.renderOutline(panelX, panelY, 150, panelH, BORDER);

        graphics.drawString(font, "第 " + ClientCombatState.round + " 轮", panelX + 6, panelY + 6, CURRENT, true);
        int y = panelY + 20;
        for (CombatStateS2C.Entry entry : ClientCombatState.entries) {
            int color = entry.isCurrent() ? CURRENT : TEXT;
            String marker = entry.isCurrent() ? "> " : "  ";
            String side = "players".equals(entry.side()) ? "[友] " : "[敌] ";
            String line = marker + side + entry.name() + " (" + entry.initiative() + ")";
            graphics.drawString(font, line, panelX + 6, y, color, false);
            y += 12;
        }

        int barW = 260;
        int barX = (width - barW) / 2;
        int barY = height - 40;
        graphics.fill(barX, barY, barX + barW, barY + 26, PANEL_BG);
        graphics.renderOutline(barX, barY, barW, 26, BORDER);
        String info = "移动 " + ClientCombatState.myMovementRemaining
                + " | 动作 " + (ClientCombatState.myAction ? "有" : "无")
                + " | 附赠 " + (ClientCombatState.myBonus ? "有" : "无");
        int infoColor = ClientCombatState.myTurn ? TEXT : TEXT_DIM;
        graphics.drawString(font, info, barX + 8, barY + 9, infoColor, false);
        if (!ClientCombatState.myTurn && ClientCombatState.currentId != null) {
            graphics.drawString(font, "等待其他单位行动...", barX + 8 + font.width(info) + 12, barY + 9, TEXT_DIM, false);
        } else if (ClientCombatState.myTurn) {
            String hint = ClientCombatState.attackMode ? "[F] 选择攻击目标..." : "[F] 攻击  [R] 结束回合";
            graphics.drawString(font, hint, barX + 8 + font.width(info) + 12, barY + 9, CURRENT, false);
        }
        String controls = "WASD平移 | 左键点选/拖动 | 右键或QE旋转 | 滚轮缩放 | V退出";
        graphics.drawString(font, controls, barX, barY - 11, TEXT_DIM, true);

        int logY = height - 80;
        for (int i = ClientCombatState.log.size() - 1; i >= 0; i--) {
            String line = ClientCombatState.log.get(i);
            int alpha = 0xFF - (ClientCombatState.log.size() - 1 - i) * 24;
            if (alpha < 0x40) {
                alpha = 0x40;
            }
            graphics.drawString(font, line, 10, logY, (alpha << 24) | 0x00E0E0, true);
            logY -= 11;
        }

        pose.popPose();
    }
}
