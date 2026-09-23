package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.DebugDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(net.minecraft.server.Main.class)
public abstract class ServerDebugArgumentsMixin {
    @ModifyVariable(method = "main([Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static String[] dndturn$debugArguments(String[] args) {
        return DebugDiagnostics.launchArguments(args);
    }
}
