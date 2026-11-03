package cc.sighs.dndturn.domain.encounter;

/** Resources are spent at one operation boundary, never by a client callback. */
public final class ActionEconomy {
    private int movementTicks;
    private boolean action = true;
    private boolean reaction = true;

    public ActionEconomy(int movementTicks) { reset(movementTicks); }

    public void reset(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("negative movement");
        movementTicks = ticks;
        action = true;
        reaction = true;
    }

    public int movementTicks() { return movementTicks; }
    public boolean hasAction() { return action; }
    public boolean hasReaction() { return reaction; }

    public boolean spendMovement(int actualTicks) {
        if (actualTicks < 0 || actualTicks > movementTicks) return false;
        movementTicks -= actualTicks;
        return true;
    }

    public void addMovement(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("negative movement");
        movementTicks = Math.addExact(movementTicks, ticks);
    }

    /** Restore the remaining movement of a persisted member without changing its turn budget. */
    public void restoreMovement(int ticks) {
        if (ticks < 0 || ticks > movementTicks) throw new IllegalArgumentException("invalid remaining movement");
        movementTicks = ticks;
    }

    public boolean spendAction() {
        if (!action) return false;
        action = false;
        return true;
    }

    /** Return a spent action after an interrupted sustained use; never stacks above one. */
    public boolean refundAction() {
        if (action) return false;
        action = true;
        return true;
    }

    public boolean spendReaction() {
        if (!reaction) return false;
        reaction = false;
        return true;
    }
}
