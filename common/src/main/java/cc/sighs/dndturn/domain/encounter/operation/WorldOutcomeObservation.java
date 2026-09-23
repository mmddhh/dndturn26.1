package cc.sighs.dndturn.domain.encounter.operation;

import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.*;

/** Bounded native observations, independent of the rule commit and release outcome. */
public record WorldOutcomeObservation(List<BlockChange> blocks, List<Motion> motions, List<Spawn> spawns, List<Damage> damage,
                                     boolean sourceRemoved, int rejectedEntities, int rejectedBlocks, List<NativeObservation> details) {
    public WorldOutcomeObservation(List<BlockChange> blocks, List<Motion> motions, List<Spawn> spawns, List<Damage> damage,
                                   boolean sourceRemoved, int rejectedEntities, int rejectedBlocks) {
        this(blocks, motions, spawns, damage, sourceRemoved, rejectedEntities, rejectedBlocks, List.of());
    }
    public record Damage(UUID operation, UUID target, float requested, float healthBefore, float healthAfter,
                         float absorptionBefore, float absorptionAfter, boolean accepted, boolean known, boolean dead) {
        public Damage {
            Objects.requireNonNull(operation); Objects.requireNonNull(target);
            for (float value : new float[] {requested,healthBefore,healthAfter,absorptionBefore,absorptionAfter})
                if (!Float.isFinite(value) || value < 0) throw new IllegalArgumentException("damage observation");
        }
    }
    public record BlockChange(int x, int y, int z, String before, String after) {
        public BlockChange {
            Objects.requireNonNull(before); Objects.requireNonNull(after);
            if (before.length() > 1024 || after.length() > 1024) throw new IllegalArgumentException("block observation size");
        }
    }
    public record Motion(UUID entity, double beforeX, double beforeY, double beforeZ, double afterX, double afterY, double afterZ) {
        public Motion {
            Objects.requireNonNull(entity);
            for (double value : new double[] {beforeX,beforeY,beforeZ,afterX,afterY,afterZ})
                if (!Double.isFinite(value)) throw new IllegalArgumentException("motion observation");
        }
    }
    public record Spawn(UUID entity, String type) {
        public Spawn { Objects.requireNonNull(entity); FactKey.requireId(type); }
    }
    public WorldOutcomeObservation {
        details = List.copyOf(details);
        if (details.size() > 128) throw new IllegalArgumentException("typed observation bound");
        blocks = List.copyOf(blocks); motions = List.copyOf(motions); spawns = List.copyOf(spawns); damage = List.copyOf(damage);
        if (blocks.size() > 8192 || motions.size() > 256 || spawns.size() > 256 || damage.size() > 256 || rejectedEntities < 0 || rejectedBlocks < 0)
            throw new IllegalArgumentException("world observation bound");
    }
    public WorldOutcomeObservation withDetails(List<NativeObservation> values) {
        return new WorldOutcomeObservation(blocks, motions, spawns, damage, sourceRemoved, rejectedEntities, rejectedBlocks, values);
    }
}
