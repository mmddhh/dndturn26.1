package cc.sighs.dndturn.platform.server.persistence;

public final class PersistenceTestAccess {
    private PersistenceTestAccess() {}
    public static CombatSavedData saved() { return new CombatSavedData(); }
    public static CombatSavedData saved(String json) { return new CombatSavedData(json); }
}
