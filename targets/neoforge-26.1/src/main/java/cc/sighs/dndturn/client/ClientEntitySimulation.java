package cc.sighs.dndturn.client;

import cc.sighs.dndturn.combat.CombatNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Display-only tracking projection. Never writes positions or submits damage/resource values. */
public final class ClientEntitySimulation {
    private static final Map<UUID, CombatNetwork.EntitySimulation> states = new HashMap<>();
    private ClientEntitySimulation() {}
    public static void clear() { states.clear(); }

    public static void receive(CombatNetwork.EntitySimulation state, IPayloadContext context) {
        var origin = context.connection();
        var world = Minecraft.getInstance().level;
        context.enqueueWork(() -> {
            ClientCombatState.refreshSession();
            var minecraft = Minecraft.getInstance();
            if (minecraft.getConnection() == null || minecraft.getConnection().getConnection() != origin
                || world == null || minecraft.level != world
                || !world.dimension().identifier().toString().equals(state.dimension())) return;
            var previous = states.get(state.entityId());
            if (previous != null && previous.generation().equals(state.generation())
                && previous.sequence() >= state.sequence()) return;
            states.put(state.entityId(), state);
        });
    }

    public static boolean paused(Entity entity) {
        if (entity == Minecraft.getInstance().player) return ClientCombatState.bodySimulationPaused();
        var state = states.get(entity.getUUID());
        return state != null && state.tracked() && state.paused()
            && state.dimension().equals(entity.level().dimension().identifier().toString());
    }
}
