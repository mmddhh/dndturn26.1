package cc.sighs.dndturn.platform.projection;

import java.util.UUID;

/** Transient instance identity, never persisted or used as an execution permit. */
public interface PresentationIdentity {
    UUID dndturn$presentationInstance();
}
