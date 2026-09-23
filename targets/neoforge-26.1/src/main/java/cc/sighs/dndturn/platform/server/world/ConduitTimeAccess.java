package cc.sighs.dndturn.platform.server.world;

import java.util.UUID;

/** Runtime-only rebasing of vanilla's non-persisted ambient sound deadline. */
public interface ConduitTimeAccess {
    void dndturn$rebaseAmbient(long worldTime, long projectedTime, UUID domain);
}
