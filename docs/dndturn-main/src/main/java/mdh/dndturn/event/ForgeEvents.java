package mdh.dndturn.event;

import mdh.dndturn.Config;
import mdh.dndturn.Dndturn;
import mdh.dndturn.command.DndTurnCommands;
import mdh.dndturn.core.CombatManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = Dndturn.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeEvents {

    private ForgeEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        DndTurnCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            CombatManager.get(server).tick(server);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CombatManager.clear(event.getServer());
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!Config.autoStartOnAggro) {
            return;
        }
        if (!(event.getEntity() instanceof Monster monster)) {
            return;
        }
        if (!(event.getNewTarget() instanceof Player player) || player.isCreative() || player.isSpectator()) {
            return;
        }
        if (!(monster.level() instanceof ServerLevel level)) {
            return;
        }
        CombatManager.get(level.getServer()).addHostileToCombat(level, monster, player);
    }

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (CombatManager.inCombat(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CombatManager manager = CombatManager.getIfPresent(server);
        if (manager != null && CombatManager.inCombat(player)) {
            manager.removeEntity(player);
        }
    }
}
