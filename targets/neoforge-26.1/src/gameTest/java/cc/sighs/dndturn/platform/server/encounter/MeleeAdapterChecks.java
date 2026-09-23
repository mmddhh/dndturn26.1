package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.damage.DamageReceivers;
import cc.sighs.dndturn.platform.server.damage.MeleeAdapters;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/** Independent registries exercise conflicts without mutating the frozen production registry. */
public final class MeleeAdapterChecks {
    private MeleeAdapterChecks() {}
    public static void register() {
        MeleeAdapters.server().register(new MeleeAdapters.Receiver() {
            public String id() { return "test:tagged_cow_receiver"; }
            public int version() { return 1; }
            public boolean matches(LivingEntity entity) {
                return entity.getType() == EntityType.COW && entity.entityTags().contains("dndturn_test_receiver");
            }
            public boolean unawareOf(LivingEntity receiver, LivingEntity attacker) { return false; }
        });
    }
    public static void run(GameTestHelper helper) {
        var zombie = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
        var cow = EntityType.COW.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
        rejected(helper, () -> new LiveActorContext(cow), "unloaded actor acquired a runtime context");
        helper.assertTrue(MeleeAdapters.server().attacker(zombie) != null
            && MeleeAdapters.server().receiver(zombie) != null, "built-in zombie contracts missing");
        helper.assertTrue(MeleeAdapters.server().attacker(cow) != null
            && MeleeAdapters.server().receiver(cow) != null, "default cow support missing");
        for (var type : java.util.List.of(EntityType.CREEPER, EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.WITHER_SKELETON)) {
            var target = type.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
            helper.assertTrue(DamageReceivers.server().find(target) != null, "audited receiving chain unavailable: " + type);
            helper.assertTrue(MeleeAdapters.server().attacker(target) != null, "default melee missing: " + type);
        }
        var unknownOverride = new net.minecraft.world.entity.monster.Creeper(EntityType.CREEPER, helper.getLevel()) {};
        helper.assertTrue(DamageReceivers.server().find(unknownOverride) != null, "subclass lost default receiver support");
        var registry = new MeleeAdapters();
        var attacker = new MeleeAdapters.Attacker("test:cow", 1, e -> e == cow);
        registry.register(attacker);
        helper.assertTrue(registry.attacker(cow) == attacker && registry.receiver(cow) == null,
            "attacker registration implicitly installed receiver support");
        rejected(helper, () -> registry.register(attacker), "duplicate attacker accepted");
        registry.register(new MeleeAdapters.Receiver() {
            public String id() { return "test:cow_receiver"; }
            public int version() { return 1; }
            public boolean matches(LivingEntity entity) { return entity == cow; }
            public boolean unawareOf(LivingEntity receiver, LivingEntity actor) { return false; }
        });
        helper.assertTrue(registry.receiver(cow) != null && registry.receiver(zombie) == null,
            "receiver matcher widened support");
        registry.register(new MeleeAdapters.Attacker("test:conflict", 1, e -> e == cow));
        rejected(helper, () -> registry.attacker(cow), "ambiguous attacker selected by order");
        registry.freeze();
        rejected(helper, () -> registry.register(new MeleeAdapters.Attacker("test:late", 1, e -> false)),
            "frozen registry accepted attacker");
        rejected(helper, () -> MeleeAdapters.server().register(new MeleeAdapters.Attacker("test:late", 1, e -> false)),
            "production registry did not freeze during setup");
    }
    private static void rejected(GameTestHelper helper, Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, message);
    }
}
