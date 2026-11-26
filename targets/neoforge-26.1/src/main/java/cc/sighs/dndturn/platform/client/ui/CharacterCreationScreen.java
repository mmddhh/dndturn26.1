package cc.sighs.dndturn.platform.client.ui;

import java.util.UUID;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
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
    private final JsonObject page;
    private String detail;
    private UUID operation;

    private CharacterCreationScreen(String json) {
        super(PAGE);
        page = JsonParser.parseString(json).getAsJsonObject();
    }

    public static void receiveOpen(CharacterProtocol.Open payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null) return;
            var next = new CharacterCreationScreen(payload.json());
            if (next.page.has("replaces") && (!(minecraft.screen instanceof CharacterCreationScreen current)
                || !current.page.get("page").equals(next.page.get("replaces")))) return;
            minecraft.setScreen(next);
        });
    }

    public static void receiveDetail(CharacterProtocol.Detail payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(Minecraft.getInstance().screen instanceof CharacterCreationScreen screen)) return;
            var value = JsonParser.parseString(payload.json()).getAsJsonObject();
            if (!screen.page.get("page").equals(value.get("page"))) return;
            screen.detail = payload.json();
        });
    }

    public static void receiveResult(CharacterProtocol.CreationResult payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var result = JsonParser.parseString(payload.json()).getAsJsonObject();
            var game = Minecraft.getInstance();
            if (!(game.screen instanceof CharacterCreationScreen screen) || screen.operation == null
                || !screen.page.get("page").equals(result.get("page"))
                || !screen.operation.toString().equals(result.get("operation").getAsString())) return;
            screen.operation = null;
            game.gui.setOverlayMessage(Component.literal(result.get("message").getAsString()), false);
            if (result.get("success").getAsBoolean()) game.setScreen(null);
        });
    }

    public static void receiveReply(CharacterProtocol.Reply payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return;
            minecraft.gui.setOverlayMessage(Component.literal(payload.message()), false);
            if (payload.success() && (minecraft.screen instanceof CharacterAsiScreen
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
        if (state != null && !page.toString().equals(state.getValue())) state.setValue(page.toString());
        var detailElement = document.getElementById("class-detail-response");
        if (detail != null && detailElement != null) { detailElement.setValue(detail); detail = null; }
        var query = document.getElementById("class-detail-request");
        if (query != null && query.getValue() != null && !query.getValue().isBlank()) {
            ClientPacketDistributor.sendToServer(new CharacterProtocol.DetailRequest(
                UUID.fromString(page.get("page").getAsString()), page.get("revision").getAsString(), query.getValue()));
            query.setValue("");
        }
        Element payload = document.getElementById(PAYLOAD);
        if (payload == null) return;
        String value = payload.getValue();
        if (value == null || value.isBlank()) return;
        payload.setValue("");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) return;
        if (operation != null) return;
        operation = UUID.randomUUID();
        var request = JsonParser.parseString(value).getAsJsonObject();
        request.addProperty("operation", operation.toString());
        request.add("page", page.get("page"));
        request.add("revision", page.get("revision"));
        ClientPacketDistributor.sendToServer(new CharacterProtocol.CreateJson(request.toString()));
    }
}
