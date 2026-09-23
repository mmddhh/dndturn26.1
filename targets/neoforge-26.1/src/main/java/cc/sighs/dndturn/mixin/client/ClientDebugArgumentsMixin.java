package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.DebugDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(net.minecraft.client.main.Main.class)
public abstract class ClientDebugArgumentsMixin {
    @ModifyVariable(method = "main([Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static String[] dndturn$debugArguments(String[] args) {
        return DebugDiagnostics.launchArguments(args);
    }
}
