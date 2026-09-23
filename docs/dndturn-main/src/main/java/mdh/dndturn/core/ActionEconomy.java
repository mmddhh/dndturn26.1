package mdh.dndturn.core;

public class ActionEconomy {

    private int movementTotal;
    private int movementRemaining;
    private boolean actionAvailable = true;
    private boolean bonusActionAvailable = true;
    private boolean reactionAvailable = true;

    public void reset(int movementCells) {
        this.movementTotal = movementCells;
        this.movementRemaining = movementCells;
        this.actionAvailable = true;
        this.bonusActionAvailable = true;
        this.reactionAvailable = true;
    }

    public int getMovementTotal() {
        return movementTotal;
    }

    public int getMovementRemaining() {
        return movementRemaining;
    }

    public void setMovementRemaining(int value) {
        this.movementRemaining = Math.max(0, value);
    }

    public boolean spendMovement(int cells) {
        if (cells < 0 || cells > movementRemaining) {
            return false;
        }
        movementRemaining -= cells;
        return true;
    }

    public boolean hasAction() {
        return actionAvailable;
    }

    public void useAction() {
        actionAvailable = false;
    }

    public void restoreAction() {
        actionAvailable = true;
    }

    public boolean hasBonusAction() {
        return bonusActionAvailable;
    }

    public void useBonusAction() {
        bonusActionAvailable = false;
    }

    public boolean hasReaction() {
        return reactionAvailable;
    }

    public void useReaction() {
        reactionAvailable = false;
    }
}
