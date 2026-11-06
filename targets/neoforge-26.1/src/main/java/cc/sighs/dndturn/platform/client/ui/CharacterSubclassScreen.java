package cc.sighs.dndturn.platform.client.ui;

import cc.sighs.dndturn.platform.network.CharacterProtocol;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.ApricityScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Subclass choice page migrated from classes. */
public final class CharacterSubclassScreen extends ApricityScreen {
    private static final String PAGE = "dndturn/subclass.html";
    private final String json;

    public CharacterSubclassScreen(String json) {
        super(PAGE);
        this.json = json;
    }

    public static void receiveOpen(CharacterProtocol.SubclassOpen payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.level != null) {
                minecraft.setScreen(new CharacterSubclassScreen(payload.json()));
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        Document document = getLinkedDocument();
        if (document == null) return;
        Element data = document.getElementById("choice-data");
        if (data != null && (data.getValue() == null || data.getValue().isBlank())) {
            data.setValue(json);
        }
        Element payload = document.getElementById("choice-payload");
        if (payload == null) return;
        String value = payload.getValue();
        if (value == null || value.isBlank()) return;
        payload.setValue("");
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(new CharacterProtocol.SubclassChoice(value));
        }
    }
}
