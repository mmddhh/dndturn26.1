package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.PresentationIdentity;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class PresentationIdentityMixin implements PresentationIdentity {
    @Unique private final UUID dndturn$presentationInstance = UUID.randomUUID();
    @Override public UUID dndturn$presentationInstance() { return dndturn$presentationInstance; }
}
