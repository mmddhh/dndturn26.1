package cc.sighs.dndturn.combat;

import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.*;

/** Exact vanilla interactions, independent of the attack-receiver registry. No ride/menu fallback. */
final class NativeEntityUse {
    private static final Set<Class<?>> FOOD = Set.of(Cow.class,Sheep.class,Pig.class,Chicken.class,
        Rabbit.class,Goat.class,Turtle.class,Bee.class,Cat.class,Wolf.class);
    static boolean supports(ItemStack stack) {
        return stack.is(Items.NAME_TAG) || stack.is(Items.SHEARS) || stack.getItem().getClass()==DyeItem.class
            || stack.has(DataComponents.FOOD) || stack.is(Items.WHEAT) || stack.is(Items.WHEAT_SEEDS)
            || stack.is(Items.BEETROOT_SEEDS) || stack.is(Items.MELON_SEEDS) || stack.is(Items.PUMPKIN_SEEDS)
            || stack.is(Items.TORCHFLOWER_SEEDS) || stack.is(Items.PITCHER_POD) || stack.is(Items.BONE)
            || stack.is(Items.SEAGRASS) || stack.is(net.minecraft.tags.ItemTags.FLOWERS)
            || stack.has(DataComponents.EQUIPPABLE) || stack.is(Items.BUCKET);
    }
    static String unavailable(ServerPlayer player, TacticalIntent intent) {
        var target=player.level().getEntity(intent.target().entity());
        if (!(target instanceof LivingEntity living) || !living.isAlive() || target instanceof net.minecraft.world.entity.player.Player)
            return "living non-player interaction target required";
        var stack=PlayerBehavior.stack(player,intent);
        if (stack.is(Items.BUCKET))
            return (target.getClass()==Cow.class || target.getClass()==Goat.class) && !living.isBaby() ? null : "adult cow or goat required for milking";
        if (stack.is(Items.NAME_TAG))
            return target instanceof Mob && stack.has(DataComponents.CUSTOM_NAME) ? null : "named tag and mob required";
        if (stack.is(Items.SHEARS)) return target.getClass()==Sheep.class && ((Sheep)target).readyForShearing() ? null : "sheep is not ready for shearing";
        if (stack.getItem().getClass()==DyeItem.class && target.getClass()==Sheep.class)
            return ((Sheep)target).getColor()!=stack.get(DataComponents.DYE) ? null : "sheep already has this color";
        var equip=stack.get(DataComponents.EQUIPPABLE);
        if (equip!=null && equip.equipOnInteract() && equip.canBeEquippedBy(living.typeHolder())
            && living.isEquippableInSlot(stack,equip.slot()) && !living.hasItemInSlot(equip.slot())) return null;
        if (target.getClass()==Wolf.class && stack.is(Items.BONE))
            return !((Wolf)target).isTame() && !((Wolf)target).isAngry() ? null : "wolf cannot be tamed now";
        if (FOOD.contains(target.getClass()) && target instanceof Animal animal && animal.isFood(stack)) {
            if (animal instanceof Cat cat && !cat.isTame()) return null;
            if (animal instanceof Wolf wolf && wolf.isTame() && wolf.getHealth()<wolf.getMaxHealth()) return null;
            if (animal instanceof Cat cat && cat.isOwnedBy(player) && cat.getHealth()<cat.getMaxHealth()) return null;
            return animal.isBaby() || animal.getAge()==0 && animal.canFallInLove() ? null : "animal cannot accept food now";
        }
        return "item/target interaction requires an adapter";
    }
    static InteractionResult invoke(ServerPlayer p,TacticalIntent i) {
        var target=(LivingEntity)p.level().getEntity(i.target().entity());
        var cancellation=net.neoforged.neoforge.common.CommonHooks.onInteractEntity(p,target,PlayerBehavior.hand(i));
        if (cancellation!=null) return cancellation;
        var stack=PlayerBehavior.stack(p,i);
        var equip=stack.get(DataComponents.EQUIPPABLE);
        if (equip!=null) return equip.equipOnTarget(p,target,stack);
        // These item hooks are sufficient; never fall through to an unrelated entity action.
        if (stack.is(Items.NAME_TAG) || stack.is(Items.SHEARS) || stack.getItem().getClass()==DyeItem.class
            || stack.has(DataComponents.EQUIPPABLE)) return stack.interactLivingEntity(p,target,PlayerBehavior.hand(i));
        return ((cc.sighs.dndturn.mixin.MobItemInteractInvoker)target).dndturn$itemInteract(p,PlayerBehavior.hand(i));
    }
}
