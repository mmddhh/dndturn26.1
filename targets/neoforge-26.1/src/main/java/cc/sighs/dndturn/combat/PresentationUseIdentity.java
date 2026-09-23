package cc.sighs.dndturn.combat;

import java.util.UUID;

/** Transient identity of an actually accepted vanilla use, not permission to use an item. */
public interface PresentationUseIdentity {
    UUID dndturn$useIdentity();
}
