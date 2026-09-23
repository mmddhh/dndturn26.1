package mdh.dndturn.network;

import mdh.dndturn.Dndturn;
import mdh.dndturn.network.packet.AttackTargetC2S;
import mdh.dndturn.network.packet.CombatActionC2S;
import mdh.dndturn.network.packet.CombatEndS2C;
import mdh.dndturn.network.packet.CombatLogS2C;
import mdh.dndturn.network.packet.CombatStateS2C;
import mdh.dndturn.network.packet.EndTurnC2S;
import mdh.dndturn.network.packet.EnterCombatC2S;
import mdh.dndturn.network.packet.MovePlanS2C;
import mdh.dndturn.network.packet.MoveToCellC2S;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class DndTurnNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Dndturn.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private DndTurnNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(EnterCombatC2S.class, id++)
                .encoder(EnterCombatC2S::encode)
                .decoder(EnterCombatC2S::decode)
                .consumerMainThread(EnterCombatC2S::handle)
                .add();
        CHANNEL.messageBuilder(EndTurnC2S.class, id++)
                .encoder(EndTurnC2S::encode)
                .decoder(EndTurnC2S::decode)
                .consumerMainThread(EndTurnC2S::handle)
                .add();
        CHANNEL.messageBuilder(MoveToCellC2S.class, id++)
                .encoder(MoveToCellC2S::encode)
                .decoder(MoveToCellC2S::decode)
                .consumerMainThread(MoveToCellC2S::handle)
                .add();
        CHANNEL.messageBuilder(CombatActionC2S.class, id++)
                .encoder(CombatActionC2S::encode)
                .decoder(CombatActionC2S::decode)
                .consumerMainThread(CombatActionC2S::handle)
                .add();
        CHANNEL.messageBuilder(AttackTargetC2S.class, id++)
                .encoder(AttackTargetC2S::encode)
                .decoder(AttackTargetC2S::decode)
                .consumerMainThread(AttackTargetC2S::handle)
                .add();
        CHANNEL.messageBuilder(CombatStateS2C.class, id++)
                .encoder(CombatStateS2C::encode)
                .decoder(CombatStateS2C::decode)
                .consumerMainThread(CombatStateS2C::handle)
                .add();
        CHANNEL.messageBuilder(CombatLogS2C.class, id++)
                .encoder(CombatLogS2C::encode)
                .decoder(CombatLogS2C::decode)
                .consumerMainThread(CombatLogS2C::handle)
                .add();
        CHANNEL.messageBuilder(CombatEndS2C.class, id++)
                .encoder(CombatEndS2C::encode)
                .decoder(CombatEndS2C::decode)
                .consumerMainThread(CombatEndS2C::handle)
                .add();
        CHANNEL.messageBuilder(MovePlanS2C.class, id++)
                .encoder(MovePlanS2C::encode)
                .decoder(MovePlanS2C::decode)
                .consumerMainThread(MovePlanS2C::handle)
                .add();
    }
}
