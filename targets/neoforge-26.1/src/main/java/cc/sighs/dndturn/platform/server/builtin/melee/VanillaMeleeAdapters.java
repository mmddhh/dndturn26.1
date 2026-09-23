package cc.sighs.dndturn.platform.server.builtin.melee;

import cc.sighs.dndturn.platform.server.damage.DamageReceivers;
import cc.sighs.dndturn.platform.server.damage.MeleeAdapters;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** Default Minecraft families and fallbacks installed by the composition root. */
public final class VanillaMeleeAdapters {
    private VanillaMeleeAdapters() {}
    public static void register() { receivers(); melee(); }
    private static void melee() {
        var registry = MeleeAdapters.server();
        registry.register(new MeleeAdapters.Attacker("dndturn:player_melee", 1, e -> e instanceof ServerPlayer));
        registry.register(new MeleeAdapters.Attacker("dndturn:zombie_melee", 1, e -> e instanceof Zombie));
        registry.register(new MeleeAdapters.Attacker("dndturn:enderman_melee", 1,
            e -> e instanceof net.minecraft.world.entity.monster.EnderMan));
        registry.registerFallback(new MeleeAdapters.Attacker("dndturn:mob_melee", 1, e -> e instanceof net.minecraft.world.entity.Mob,
            e -> {
                var attribute = e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                return attribute == null ? 0.0 : Math.max(0, attribute.getValue());
            }));

        }
    private static void receivers() {
        var registry = DamageReceivers.server();
        registry.register(new DamageReceivers.Receiver() {
            public String id() { return "dndturn:player_receiver"; }
            public int version() { return 1; }
            public boolean matches(LivingEntity entity) { return entity instanceof ServerPlayer; }
            public boolean unawareOf(LivingEntity receiver, LivingEntity attacker) { return false; }
        });
        registry.register(new DamageReceivers.Receiver() {
            public String id() { return "dndturn:enderman_receiver"; }
            public int version() { return 1; }
            public boolean matches(LivingEntity entity) { return entity instanceof net.minecraft.world.entity.monster.EnderMan; }
            public boolean unawareOf(LivingEntity receiver, LivingEntity attacker) {
                return ((net.minecraft.world.entity.monster.EnderMan)receiver).getTarget() != attacker;
            }
        });
        registry.register(new DamageReceivers.Receiver() {
            public String id() { return "dndturn:zombie_receiver"; }
            public int version() { return 1; }
            public boolean matches(LivingEntity entity) { return entity instanceof Zombie; }
            public boolean unawareOf(LivingEntity receiver, LivingEntity attacker) {
                return ((Zombie)receiver).getTarget() != attacker;
            }
        });
        // Default support is independent of species and of attacker/AI registration.
        // Explicit registrations still resolve first, with their existing conflict checks.
        registry.registerFallback(new DamageReceivers.Receiver() {
            public String id() { return "dndturn:standard_living_receiver"; }
            public int version() { return 1; }
            public boolean matches(LivingEntity entity) { return true; }
            public boolean unawareOf(LivingEntity receiver, LivingEntity attacker) {
                return receiver instanceof Mob mob && mob.getTarget() != attacker;
            }
        });

        }
}
