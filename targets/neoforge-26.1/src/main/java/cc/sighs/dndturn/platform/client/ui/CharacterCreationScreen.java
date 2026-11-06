package cc.sighs.dndturn.platform.client.ui;

import cc.sighs.dndturn.platform.network.CharacterProtocol;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.ApricityScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The classes-owned creation page, kept as a normal AUI screen instead of a second overlay stack. */
public final class CharacterCreationScreen extends ApricityScreen {
    private static final String PAGE = "dndturn/class_creation.html";
    private static final String PAYLOAD = "class-payload";
    private static final String STATE = "page-state";
    private String lastMode = "";

    public CharacterCreationScreen() {
        super(PAGE);
    }

    public static void receiveOpen(CharacterProtocol.Open payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null) return;
            if (!(minecraft.screen instanceof CharacterCreationScreen)) {
                minecraft.setScreen(new CharacterCreationScreen());
            }
        });
    }

    public static void receiveReply(CharacterProtocol.Reply payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return;
            minecraft.gui.setOverlayMessage(Component.literal(payload.message()), false);
            if (payload.success() && (minecraft.screen instanceof CharacterCreationScreen
                    || minecraft.screen instanceof CharacterAsiScreen
                    || minecraft.screen instanceof CharacterSubclassScreen)) {
                minecraft.setScreen(null);
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        Document document = getLinkedDocument();
        if (document == null) return;
        Element state = document.getElementById(STATE);
        if (state != null && (state.getValue() == null || !state.getValue().contains("\"mode\":\"create\""))) {
            lastMode = "create";
            state.setValue("{\"mode\":\"create\"}");
        }
        Element payload = document.getElementById(PAYLOAD);
        if (payload == null) return;
        String value = payload.getValue();
        if (value == null || value.isBlank()) return;
        payload.setValue("");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) return;
        ClientPacketDistributor.sendToServer(new CharacterProtocol.CreateJson(value));
    }
}
