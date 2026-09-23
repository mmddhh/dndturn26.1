package cc.sighs.dndturn.combat;

import java.util.UUID;

/** Transient instance identity, never persisted or used as an execution permit. */
public interface PresentationIdentity {
    UUID dndturn$presentationInstance();
}
