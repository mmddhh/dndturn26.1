package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.platform.bootstrap.DNDTurnNeoForge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Iron-tier equipment used by the classes tables and starting kits; no real-time combat behavior. */
public final class DndEquipmentItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DNDTurnNeoForge.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DNDTurnNeoForge.MOD_ID);
    private static final List<DeferredItem<Item>> ALL = new ArrayList<>();

    static {
        for (String id : List.of("iron_arrow", "iron_bolt", "iron_blowgun_needle")) {
            ALL.add(ITEMS.registerItem(id, Item::new, () -> new Item.Properties()
                .component(DataComponents.LORE, new ItemLore(List.of(
                    Component.translatable("tooltip.dndturn.ammunition"))))));
        }
        for (String type : DndEquipmentCatalog.WEAPONS.keySet()) {
            String id = "iron_" + type;
            var spec = DndEquipmentCatalog.WEAPONS.get(type);
            List<Component> lore = new ArrayList<>();
            if (spec.dice() > 0) {
                String dice = spec.dice() + "d" + spec.sides();
                lore.add(Component.translatable("tooltip.dndturn.damage", dice,
                    Component.translatable("damage_type.dndturn." + spec.damageType())));
                if (spec.versatileSides() > 0) {
                    String versatile = spec.dice() + "d" + spec.versatileSides();
                    lore.add(Component.translatable("tooltip.dndturn.versatile", versatile,
                        Component.translatable("damage_type.dndturn." + spec.damageType())));
                }
            }
            if (!spec.tags().isBlank()) lore.add(tagLine(spec.tags()));
            ALL.add(ITEMS.registerItem(id, properties -> weapon(type, properties), () -> new Item.Properties()
                .durability(250)
                .component(DataComponents.LORE, new ItemLore(lore))));
        }
        for (String set : DndEquipmentCatalog.ARMOR.keySet()) {
            var spec = DndEquipmentCatalog.ARMOR.get(set);
            ArmorMaterial material = switch (spec.category()) {
                case "light" -> ArmorMaterials.LEATHER;
                case "medium" -> ArmorMaterials.CHAINMAIL;
                default -> ArmorMaterials.IRON;
            };
            for (ArmorType type : List.of(ArmorType.HELMET, ArmorType.CHESTPLATE,
                    ArmorType.LEGGINGS, ArmorType.BOOTS)) {
                String id = "iron_" + set + "_" + type.getSerializedName();
                List<Component> lore = new ArrayList<>();
                lore.add(Component.translatable("tooltip.dndturn.armor", spec.baseAc(),
                    Component.translatable("armor_category.dndturn." + spec.category())));
                if (spec.strength() > 0) lore.add(Component.translatable("tooltip.dndturn.strength", spec.strength()));
                if (spec.stealth()) lore.add(Component.translatable("tooltip.dndturn.stealth"));
                ALL.add(ITEMS.registerItem(id, Item::new, () -> new Item.Properties()
                    .durability(type.getDurability(material.durability()))
                    .component(DataComponents.LORE, new ItemLore(lore))
                    .component(DataComponents.EQUIPPABLE, Equippable.builder(type.getSlot())
                        .setAsset(material.assetId())
                        .setEquipSound(material.equipSound())
                        .build())
                    .component(DataComponents.ATTRIBUTE_MODIFIERS, material.createAttributes(type))));
            }
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EQUIPMENT_TAB = TABS.register("equipment",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dndturn.equipment"))
            .icon(() -> new ItemStack(ALL.get(0).get()))
            .displayItems((parameters, output) -> {
                for (DeferredItem<Item> item : ALL) output.accept(item.get());
            }).build());

    private DndEquipmentItems() {}

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        TABS.register(eventBus);
    }

    public static Item get(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath(DNDTurnNeoForge.MOD_ID, path))
            .map(net.minecraft.core.Holder.Reference::value)
            .orElse(net.minecraft.world.item.Items.AIR);
    }

    private static Item weapon(String type, Item.Properties properties) {
        return switch (type) {
            case "longbow", "shortbow" -> new BowItem(properties);
            case "light_crossbow", "hand_crossbow", "heavy_crossbow" -> new CrossbowItem(properties);
            default -> new Item(properties);
        };
    }

    private static Component tagLine(String tags) {
        var line = Component.literal("");
        boolean first = true;
        for (String tag : tags.split(",")) {
            String value = tag.trim();
            if (value.isEmpty()) continue;
            if (!first) line.append(", ");
            line.append(Component.translatable("weapon_property.dndturn." + value.replace(' ', '_')));
            first = false;
        }
        return line;
    }

    private static String title(String value) {
        StringBuilder out = new StringBuilder();
        for (String part : value.split("_")) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
