package cc.sighs.dndturn.mixin;

import java.util.Arrays;
import net.minecraft.server.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Consume only our flag before the strict dedicated-server parser. No cancellation. */
@Mixin(Main.class)
public abstract class ServerDebugArgumentsMixin {
    @ModifyArg(method = "main", at = @At(value = "INVOKE",
        target = "Ljoptsimple/OptionParser;parse([Ljava/lang/String;)Ljoptsimple/OptionSet;"), index = 0)
    private static String[] dndturn$debugArgument(String[] arguments) {
        return Arrays.stream(arguments).filter(argument -> !argument.equals("-debug")).toArray(String[]::new);
    }
}
