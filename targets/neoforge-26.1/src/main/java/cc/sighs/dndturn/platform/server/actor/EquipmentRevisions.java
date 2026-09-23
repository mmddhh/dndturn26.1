package cc.sighs.dndturn.platform.server.actor;

import net.minecraft.world.entity.EquipmentSlot;

/** Native equipment write generation, separate from stack content and actor instance. */
public interface EquipmentRevisions { long dndturn$equipmentRevision(EquipmentSlot slot); }
