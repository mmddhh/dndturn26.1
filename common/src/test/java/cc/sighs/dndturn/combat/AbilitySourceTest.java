package cc.sighs.dndturn.combat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbilitySourceTest {
    @Test void statusEvidenceSeparatesGrantLifetimeFromActorInstance() {
        UUID actor = UUID.randomUUID(), instance = UUID.randomUUID(), grant = UUID.randomUUID();
        var source = AbilitySource.status("test:status", 1, actor, instance, grant);
        assertNull(source.hand()); assertNull(source.item());
        assertNotEquals(source, AbilitySource.status("test:status", 1, actor, instance, UUID.randomUUID()));
        assertNotEquals(source, AbilitySource.status("test:status", 1, actor, UUID.randomUUID(), grant));
        assertThrows(IllegalArgumentException.class, () -> AbilitySource.status("test:status", 1, actor, instance, null));
        assertThrows(IllegalArgumentException.class, () -> new AbilitySource(AbilitySource.Kind.INTRINSIC,
            null, null, "test:status", 1, actor, instance, grant));
    }
    @Test void intrinsicIntentCarriesOwnershipWithoutFabricatingEquipment() {
        var target = new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY, "minecraft:overworld",
            UUID.randomUUID(), null, -1, 0, 0, 0);
        var source = AbilitySource.intrinsic("test:natural", 1, UUID.randomUUID(), UUID.randomUUID());
        var intent = new TacticalIntent("test:attack", 1, TacticalIntent.Capability.ATTACK, target, source);
        assertEquals(source, intent.source());
        assertNull(intent.hand()); assertNull(intent.item());
        assertThrows(IllegalArgumentException.class, () -> new TacticalIntent("test:attack", 1,
            TacticalIntent.Hand.MAIN_HAND, TacticalIntent.Capability.ATTACK, target, null, source));
        assertThrows(IllegalArgumentException.class, () -> new TacticalIntent("test:attack", 1,
            TacticalIntent.Capability.ATTACK, target, AbilitySource.basic()));
    }
    @Test void existingIntentsNormalizeWithoutChangingTheirIdentityOrEvidence() {
        var target = new TacticalIntent.Target(TacticalIntent.TargetKind.BLOCK, "minecraft:overworld",
            null, new GridCell(1, 1, 1), 1, .5, 1, .5);
        var item = new TacticalIntent.ItemReference(4, "original-content");
        var equipment = new TacticalIntent("dndturn:place", 1, TacticalIntent.Capability.PLACE, target, cc.sighs.dndturn.combat.AbilitySource.equipment(TacticalIntent.Hand.OFF_HAND, item));
        assertEquals(AbilitySource.equipment(TacticalIntent.Hand.OFF_HAND, item), equipment.source());
        assertEquals(item, equipment.item());
        var basic = new TacticalIntent("dndturn:block", 1, TacticalIntent.Capability.USE_BLOCK, target, cc.sighs.dndturn.combat.AbilitySource.basic());
        assertEquals(AbilitySource.basic(), basic.source());
        assertNull(basic.item());
        assertNotEquals(equipment.source(), new TacticalIntent("dndturn:place", 1, TacticalIntent.Capability.PLACE, target, cc.sighs.dndturn.combat.AbilitySource.equipment(TacticalIntent.Hand.OFF_HAND, new TacticalIntent.ItemReference(5, item.revision()))).source());
    }
    @Test void intrinsicOwnershipDoesNotRequireEquipmentAndIncludesInstanceAndVersion() {
        UUID actor = UUID.randomUUID(), instance = UUID.randomUUID();
        var source = AbilitySource.intrinsic("test:natural", 1, actor, instance);
        assertNull(source.hand()); assertNull(source.item());
        assertNotEquals(source, AbilitySource.intrinsic("test:natural", 2, actor, instance));
        assertNotEquals(source, AbilitySource.intrinsic("test:natural", 1, actor, UUID.randomUUID()));
        assertNotEquals(source, AbilitySource.intrinsic("test:natural", 1, UUID.randomUUID(), instance));
        assertThrows(IllegalArgumentException.class, () -> AbilitySource.intrinsic("unqualified", 1, actor, instance));
        assertThrows(IllegalArgumentException.class, () -> AbilitySource.intrinsic("test:natural", 0, actor, instance));
        assertThrows(IllegalArgumentException.class, () -> AbilitySource.intrinsic("test:natural", 1, actor, null));
    }
    @Test void sourcesCannotMixEvidenceOrOmitRequiredEquipment() {
        var item = new TacticalIntent.ItemReference(0, "content");
        var equipped = AbilitySource.equipment(TacticalIntent.Hand.MAIN_HAND, item);
        assertEquals(item, equipped.item());
        assertNull(AbilitySource.basic(TacticalIntent.Hand.MAIN_HAND).item());
        assertThrows(IllegalArgumentException.class, () -> new AbilitySource(AbilitySource.Kind.INTRINSIC,
            TacticalIntent.Hand.MAIN_HAND, item, "test:natural", 1, UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new AbilitySource(AbilitySource.Kind.EQUIPMENT,
            TacticalIntent.Hand.MAIN_HAND, null, null, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AbilitySource(AbilitySource.Kind.BASIC,
            TacticalIntent.Hand.MAIN_HAND, item, null, 0, null, null));
    }
}
