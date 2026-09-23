package mdh.dndturn.core;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Combatant {

    public static final String SIDE_PLAYERS = "players";
    public static final String SIDE_MONSTERS = "monsters";

    private final UUID entityId;
    private final String side;
    private final ActionEconomy economy = new ActionEconomy();
    private final List<BlockPos> movementPlan = new ArrayList<>();

    private int initiative;
    private BlockPos cell;
    private int idleTicks;
    private boolean dodging;
    private boolean disengaged;

    private BlockPos segmentFrom;
    private BlockPos segmentTo;
    private float segmentProgress;

    private boolean hostile;
    private boolean primed;
    private int moveTicks;
    private double lastMoveX;
    private double lastMoveZ;

    private int attackTicks;
    private int attackTotalTicks;
    private int attackTargetId = -1;
    private boolean attackApplied;
    private boolean advanceAfterAttack;

    public Combatant(UUID entityId, String side) {
        this.entityId = entityId;
        this.side = side;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getSide() {
        return side;
    }

    public boolean isPlayerSide() {
        return SIDE_PLAYERS.equals(side);
    }

    public ActionEconomy getEconomy() {
        return economy;
    }

    public int getInitiative() {
        return initiative;
    }

    public void setInitiative(int initiative) {
        this.initiative = initiative;
    }

    public BlockPos getCell() {
        return cell;
    }

    public void setCell(BlockPos cell) {
        this.cell = cell;
    }

    public List<BlockPos> getMovementPlan() {
        return movementPlan;
    }

    public BlockPos getSegmentFrom() {
        return segmentFrom;
    }

    public BlockPos getSegmentTo() {
        return segmentTo;
    }

    public float getSegmentProgress() {
        return segmentProgress;
    }

    public void setSegmentProgress(float segmentProgress) {
        this.segmentProgress = segmentProgress;
    }

    public void beginMove(List<BlockPos> path) {
        movementPlan.clear();
        movementPlan.addAll(path);
        segmentFrom = cell;
        segmentTo = movementPlan.isEmpty() ? null : movementPlan.remove(0);
        segmentProgress = 0.0F;
    }

    public void advanceSegment() {
        cell = segmentTo;
        segmentFrom = cell;
        segmentTo = movementPlan.isEmpty() ? null : movementPlan.remove(0);
        segmentProgress = 0.0F;
    }

    public boolean isMoving() {
        return segmentTo != null;
    }

    public void stopMoving() {
        movementPlan.clear();
        segmentFrom = null;
        segmentTo = null;
        segmentProgress = 0.0F;
    }

    public int getIdleTicks() {
        return idleTicks;
    }

    public void setIdleTicks(int idleTicks) {
        this.idleTicks = idleTicks;
    }

    public boolean isDodging() {
        return dodging;
    }

    public void setDodging(boolean dodging) {
        this.dodging = dodging;
    }

    public boolean isDisengaged() {
        return disengaged;
    }

    public void setDisengaged(boolean disengaged) {
        this.disengaged = disengaged;
    }

    public boolean isAttacking() {
        return attackTicks > 0;
    }
    public int getAttackTicks() {
        return attackTicks;
    }

    public void setAttackTicks(int attackTicks) {
        this.attackTicks = attackTicks;
    }

    public int getAttackTotalTicks() {
        return attackTotalTicks;
    }

    public void setAttackTotalTicks(int attackTotalTicks) {
        this.attackTotalTicks = attackTotalTicks;
    }

    public int getAttackTargetId() {
        return attackTargetId;
    }

    public void setAttackTargetId(int attackTargetId) {
        this.attackTargetId = attackTargetId;
    }

    public boolean isAttackApplied() {
        return attackApplied;
    }

    public void setAttackApplied(boolean attackApplied) {
        this.attackApplied = attackApplied;
    }

    public boolean isAdvanceAfterAttack() {
        return advanceAfterAttack;
    }

    public void setAdvanceAfterAttack(boolean advanceAfterAttack) {
        this.advanceAfterAttack = advanceAfterAttack;
    }

    public boolean isHostile() {
        return hostile;
    }

    public void setHostile(boolean hostile) {
        this.hostile = hostile;
    }

    public boolean isPrimed() {
        return primed;
    }

    public void setPrimed(boolean primed) {
        this.primed = primed;
    }

    public int getMoveTicks() {
        return moveTicks;
    }

    public void setMoveTicks(int moveTicks) {
        this.moveTicks = moveTicks;
    }

    public double getLastMoveX() {
        return lastMoveX;
    }

    public double getLastMoveZ() {
        return lastMoveZ;
    }

    public void setLastMove(double x, double z) {
        this.lastMoveX = x;
        this.lastMoveZ = z;
    }
}
