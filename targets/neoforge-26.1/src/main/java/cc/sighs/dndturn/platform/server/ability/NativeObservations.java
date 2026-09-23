package cc.sighs.dndturn.platform.server.ability;

import cc.sighs.dndturn.domain.encounter.operation.NativeObservation;
import cc.sighs.dndturn.domain.encounter.operation.ObservationRegistry;

import java.util.*;
import net.minecraft.world.entity.LivingEntity;

/** Typed value capture only. Observing a body makes no claim about unobserved indirect mutations. */
public final class NativeObservations {
    public static final ObservationRegistry REGISTRY = new ObservationRegistry();
    public static final ObservationRegistry.Key BODY = new ObservationRegistry.Key("dndturn:body_state", 1);
    static {
        REGISTRY.register(new ObservationRegistry.Definition(BODY,
                Map.of("x", NativeObservation.Type.REAL, "y", NativeObservation.Type.REAL, "z", NativeObservation.Type.REAL,
                        "health", NativeObservation.Type.REAL, "absorption", NativeObservation.Type.REAL, "removed", NativeObservation.Type.BOOLEAN),
                Set.of("x", "y", "z", "health", "absorption", "removed")));
    }
    private NativeObservations() {}
    private static NativeObservation.Value real(double value) {
        return new NativeObservation.Value(NativeObservation.Type.REAL, Double.toString(value));
    }
    public static NativeObservation body(UUID operation, LivingEntity body) {
        if (!body.level().getServer().isSameThread()) throw new IllegalStateException("server observation thread");
        return new NativeObservation(BODY.id(), BODY.version(), operation, body.getUUID(), NativeObservation.Certainty.KNOWN,
                Map.of("x", real(body.getX()), "y", real(body.getY()), "z", real(body.getZ()),
                        "health", real(body.getHealth()), "absorption", real(body.getAbsorptionAmount()),
                        "removed", new NativeObservation.Value(NativeObservation.Type.BOOLEAN, Boolean.toString(body.isRemoved()))));
    }
}
