package cc.sighs.dndturn.platform.mixin.client.presentation;

import cc.sighs.dndturn.platform.client.presentation.ClientPresentation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPresentationPacketMixin {
    @Shadow private ClientLevel level;

    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void dndturn$hurt(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        ClientPresentation.hurt(level.getEntity(packet.entityId()));
    }

    @Inject(method = "handleHurtAnimation", at = @At("TAIL"))
    private void dndturn$hurtAnimation(net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket packet, CallbackInfo ci) {
        ClientPresentation.hurt(level.getEntity(packet.id()));
    }
}
