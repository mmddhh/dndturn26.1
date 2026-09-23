package cc.sighs.dndturn.combat;

import java.util.Objects;
import java.util.UUID;

/** Captured values for an arrow's cause; a missing owner is distinct from a departed owner. */
public record ProjectileOrigin(UUID projectileId, UUID ownerId, UUID rootOperationId,
                               UUID sourceEncounterId, String dimension, long launchedServerTick,
                               double arrowBaseDamage, String weaponId, String ammoId,
                               boolean ownerWasPlayer, boolean launchVerified,
                               boolean weaponEnchanted, DamageTrace launchTrace) {
    public ProjectileOrigin(UUID projectileId, UUID ownerId, UUID rootOperationId,
                            UUID sourceEncounterId, String dimension, long launchedServerTick,
                            double arrowBaseDamage, String weaponId, String ammoId,
                            boolean ownerWasPlayer, boolean launchVerified, boolean weaponEnchanted) {
        this(projectileId, ownerId, rootOperationId, sourceEncounterId, dimension, launchedServerTick,
            arrowBaseDamage, weaponId, ammoId, ownerWasPlayer, launchVerified, weaponEnchanted, null);
    }
    public ProjectileOrigin {
        Objects.requireNonNull(projectileId);
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(weaponId);
        Objects.requireNonNull(ammoId);
        if (dimension.isBlank() || launchedServerTick < 0 || !Double.isFinite(arrowBaseDamage)
            || arrowBaseDamage < 0 || (rootOperationId != null && ownerId == null)
            || ownerWasPlayer && ownerId == null)
            throw new IllegalArgumentException("invalid projectile origin");
    }
}
