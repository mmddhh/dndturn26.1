package mdh.dndturn.client;

import mdh.dndturn.network.packet.CombatStateS2C;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class ClientCombatState {

    public static boolean inCombat = false;
    public static UUID encounterId;
    public static int round;
    public static UUID currentId;
    public static List<CombatStateS2C.Entry> entries = new ArrayList<>();
    public static int gridMinX;
    public static int gridMaxX;
    public static int gridMinZ;
    public static int gridMaxZ;
    public static int floorY;
    public static Set<BlockPos> reachable = new HashSet<>();
    public static boolean myTurn;
    public static int myMovementRemaining;
    public static boolean myAction;
    public static boolean myBonus;
    public static boolean attackMode;
    public static final List<BlockPos> activePath = new ArrayList<>();
    public static final List<String> log = new ArrayList<>();

    private ClientCombatState() {
    }

    public static void apply(CombatStateS2C packet) {
        inCombat = true;
        encounterId = packet.encounterId;
        round = packet.round;
        currentId = packet.currentId;
        entries = packet.entries;
        gridMinX = packet.gridMinX;
        gridMaxX = packet.gridMaxX;
        gridMinZ = packet.gridMinZ;
        gridMaxZ = packet.gridMaxZ;
        floorY = packet.floorY;
        reachable = new HashSet<>(packet.reachable);
        myTurn = packet.myTurn;
        myMovementRemaining = packet.myMovementRemaining;
        myAction = packet.myAction;
        myBonus = packet.myBonus;
        if (!packet.myTurn) {
            attackMode = false;
            activePath.clear();
        }
    }

    public static void setPath(List<BlockPos> path) {
        activePath.clear();
        if (path != null) {
            activePath.addAll(path);
        }
    }

    public static void addLog(String message) {
        log.add(message);
        while (log.size() > 8) {
            log.remove(0);
        }
    }

    public static void clear() {
        inCombat = false;
        encounterId = null;
        currentId = null;
        entries = new ArrayList<>();
        reachable = new HashSet<>();
        myTurn = false;
        myMovementRemaining = 0;
        myAction = false;
        myBonus = false;
        attackMode = false;
        activePath.clear();
    }
}
