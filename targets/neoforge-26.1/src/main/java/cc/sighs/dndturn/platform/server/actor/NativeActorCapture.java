package cc.sighs.dndturn.platform.server.actor;

import cc.sighs.dndturn.domain.actor.NativeActorObservation;
import cc.sighs.dndturn.domain.actor.NativeAttributeObservation;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.fact.NativeAttributeCapture;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Explicit character-sheet read, never used by ordinary ability discovery. */
final class NativeActorCapture {
    private NativeActorCapture() {}
    static NativeActorObservation capture(LiveActorContext actor, UUID capture) {
        actor.verifyCurrent();
        var body = actor.body();
        var equipment = new ArrayList<NativeActorObservation.Item>();
        for (var slot : EquipmentSlot.values()) equipment.add(item(capture, slot.getName(), body.getItemBySlot(slot)));
        var inventory = new ArrayList<NativeActorObservation.Item>();
        var coverage = NativeActorObservation.Coverage.UNSUPPORTED;
        int selected = -1;
        if (body instanceof Player player) {
            var storage = player.getInventory();
            selected = storage.getSelectedSlot();
            for (int i = 0; i < Math.min(64, storage.getContainerSize()); i++)
                inventory.add(item(capture, Integer.toString(i), storage.getItem(i)));
            coverage = storage.getContainerSize() > 64 ? NativeActorObservation.Coverage.LIMITED : NativeActorObservation.Coverage.COMPLETE;
        }
        var attributes = new LinkedHashMap<String, NativeAttributeObservation>();
        var attributeCoverage = NativeActorObservation.Coverage.COMPLETE;
        var budget = new NativeAttributeCapture.Budget();
        int examined = 0;
        for (var attribute : BuiltInRegistries.ATTRIBUTE) {
            if (++examined > 512 || attributes.size() >= 128) { attributeCoverage = NativeActorObservation.Coverage.LIMITED; break; }
            var holder = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute);
            if (!body.getAttributes().hasAttribute(holder)) continue;
            attributes.put(BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString(), NativeAttributeCapture.capture(actor, holder, budget));
        }
        return new NativeActorObservation(actor.id(), actor.instance(), capture,
                BuiltInRegistries.ENTITY_TYPE.getKey(body.getType()).toString(), body.getName().getString(256),
                new NativeActorObservation.Transform(actor.level().dimension().identifier().toString(), body.getX(), body.getY(), body.getZ(), body.getYRot(), body.getXRot()),
                new NativeActorObservation.Vitals(body.getHealth(), body.getMaxHealth(), body.getAbsorptionAmount(), body.isAlive()),
                new NativeActorObservation.Body(body.getBbWidth(), body.getBbHeight(), body.getEyeHeight(), body.getPose().name(), body.onGround(), body.isInWater(), body.isOnFire()),
                body instanceof Player ? NativeActorObservation.Controller.PLAYER_CONNECTION
                        : body instanceof Mob ? NativeActorObservation.Controller.MOB_AI : NativeActorObservation.Controller.NATIVE_OTHER,
                equipment, inventory, coverage, selected, attributes, attributeCoverage);
    }
    private static NativeActorObservation.Item item(UUID capture, String address, ItemStack stack) {
        return new NativeActorObservation.Item(address, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getHoverName().getString(128), stack.getCount(), stack.getDamageValue(), stack.getMaxDamage(),
                capture + "/" + address);
    }
}
