package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.ClientControlRegression;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ClientPacketProbeMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void dndturn$observe(Packet<?> packet, CallbackInfo ci) { ClientControlRegression.packet(packet); }
}
