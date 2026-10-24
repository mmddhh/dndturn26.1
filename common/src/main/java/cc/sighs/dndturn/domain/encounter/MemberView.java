package cc.sighs.dndturn.domain.encounter;

import java.util.*;

/** Detached immutable encounter read value. */
public record MemberView(UUID id, int initiative, int tieBreak, int movementTicks, boolean action,
                         boolean reaction, boolean dodging, boolean disengaged, long eligibleRound) {}
