package cc.sighs.dndturn.platform.client.ui;

import cc.sighs.dndturn.platform.network.CharacterProtocol;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.ApricityScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Read-only sheet mode of the same classes page. */
public final class CharacterSheetScreen extends ApricityScreen {
    private static final String PAGE = "dndturn/class_creation.html";
    private static final String STATE = "page-state";
    private final String json;
    private String last;

    public CharacterSheetScreen(String json) {
        super(PAGE);
        this.json = json;
    }

    public static void receiveSheet(CharacterProtocol.Sheet payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.level != null) {
                minecraft.setScreen(new CharacterSheetScreen(payload.json()));
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        Document document = getLinkedDocument();
        if (document == null) return;
        Element state = document.getElementById(STATE);
        if (state != null && !json.equals(state.getValue())) {
            state.setValue(json);
        }
        Element action = document.getElementById("sheet-action");
        if (action == null) return;
        String value = action.getValue();
        if (value == null || value.isBlank()) return;
        action.setValue("");
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(new CharacterProtocol.SheetAction(value));
        }
    }
}
