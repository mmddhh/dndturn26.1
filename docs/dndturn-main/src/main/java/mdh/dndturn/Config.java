package mdh.dndturn;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = Dndturn.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER.comment("Whether to log the dirt block on common setup").define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER.comment("A magic number").defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER.comment("What you want the introduction message to be for the magic number").define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER.comment("A list of items to log on common setup.").defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    private static final ForgeConfigSpec.IntValue ENTER_COMBAT_RADIUS = BUILDER.comment("Radius in blocks searched for hostile monsters when entering combat").defineInRange("enterCombatRadius", 24, 4, 128);

    private static final ForgeConfigSpec.IntValue GRID_RADIUS = BUILDER.comment("Half size of the tactical grid around the encounter center").defineInRange("gridRadius", 24, 4, 64);

    private static final ForgeConfigSpec.IntValue MOVEMENT_ANIMATION_TICKS = BUILDER.comment("Server ticks spent animating movement across one grid cell").defineInRange("movementAnimationTicks", 6, 1, 40);

    private static final ForgeConfigSpec.IntValue ATTACK_ANIMATION_TICKS = BUILDER.comment("Server ticks spent playing a melee attack animation").defineInRange("attackAnimationTicks", 8, 2, 40);

    private static final ForgeConfigSpec.IntValue MONSTER_TURN_DELAY = BUILDER.comment("Ticks a monster waits before acting on its turn").defineInRange("monsterTurnDelayTicks", 20, 1, 200);

    private static final ForgeConfigSpec.BooleanValue AUTO_START_ON_AGGRO = BUILDER.comment("Start turn-based combat automatically when a monster targets a player").define("autoStartOnAggro", true);

    private static final ForgeConfigSpec.DoubleValue CAMERA_ROTATE_SPEED = BUILDER.comment("Camera yaw rotation speed in degrees per second for Q/E").defineInRange("cameraRotateSpeed", 110.0D, 10.0D, 720.0D);

    private static final ForgeConfigSpec.DoubleValue CAMERA_PAN_SPEED = BUILDER.comment("Camera pan speed multiplier for WASD").defineInRange("cameraPanSpeed", 1.0D, 0.1D, 10.0D);

    private static final ForgeConfigSpec.IntValue CREEPER_DETONATE_RANGE = BUILDER.comment("Grid range at which a creeper primes its fuse").defineInRange("creeperDetonateRange", 3, 1, 8);

    private static final ForgeConfigSpec.IntValue MAX_MOVE_TICKS_PER_CELL = BUILDER.comment("Maximum server ticks allowed to walk across one client-walked cell").defineInRange("maxMoveTicksPerCell", 16, 2, 200);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;
    public static int enterCombatRadius;
    public static int gridRadius;
    public static int movementAnimationTicks;
    public static int attackAnimationTicks;
    public static int monsterTurnDelayTicks;
    public static boolean autoStartOnAggro;
    public static double cameraRotateSpeed;
    public static double cameraPanSpeed;
    public static int creeperDetonateRange;
    public static int maxMoveTicksPerCell;

    private static boolean validateItemName(final Object obj) {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // convert the list of strings into a set of items
        items = ITEM_STRINGS.get().stream().map(itemName -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName))).collect(Collectors.toSet());

        enterCombatRadius = ENTER_COMBAT_RADIUS.get();
        gridRadius = GRID_RADIUS.get();
        movementAnimationTicks = MOVEMENT_ANIMATION_TICKS.get();
        attackAnimationTicks = ATTACK_ANIMATION_TICKS.get();
        monsterTurnDelayTicks = MONSTER_TURN_DELAY.get();
        autoStartOnAggro = AUTO_START_ON_AGGRO.get();
        cameraRotateSpeed = CAMERA_ROTATE_SPEED.get();
        cameraPanSpeed = CAMERA_PAN_SPEED.get();
        creeperDetonateRange = CREEPER_DETONATE_RANGE.get();
        maxMoveTicksPerCell = MAX_MOVE_TICKS_PER_CELL.get();
    }
}
