package cc.sighs.dndturn.platform.client.camera;

/** Pure-value provenance carried from the captured render region to its uploaded mesh. */
public interface CutawayStampAccess {
    record Stamp(CutawayMask mask, long section) {}
    Stamp dndturn$cutawayStamp();
    void dndturn$cutawayStamp(Stamp stamp);
}
