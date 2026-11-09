package cc.sighs.dndturn.platform.server.damage;

import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import io.github.fatalitaya.combatnexus.api.id.NexusId;
import io.github.fatalitaya.combatnexus.combat.api.CombatAccess;
import io.github.fatalitaya.combatnexus.combat.api.CombatSource;
import io.github.fatalitaya.combatnexus.combat.api.ApplicationJournal;
import io.github.fatalitaya.combatnexus.combat.driver.api.*;
import io.github.fatalitaya.combatnexus.combat.platform.api.CombatRegisterEvent;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Native application only. The action owner supplies an admitted child and already resolved numbers. */
public final class CombatNexusAdapter {
    private static DriverRegistration registration;
    private record Applying(UUID request, DriverSession session, DamageAttempt reactions) {}
    private static final ThreadLocal<Applying> APPLYING = new ThreadLocal<>();
    private CombatNexusAdapter() {}
    public static void register(CombatRegisterEvent event) {
        var local = DriverDefinition.ApplicationOwner.COMBATNEXUS;
        registration = event.registrar().externalDriver(new DriverDefinition(NexusId.parse("dndturn:tactical"),
            Set.of(DriverScope.NUMERICAL), local, local, local));
    }
    public record Receipt(OperationRecord.Outcome outcome, float health, float absorption, double shields, String reason) {}
    public static boolean incoming(LivingIncomingDamageEvent event) {
        var applying = APPLYING.get();
        if (applying == null || !applying.session().applyingDamage(event.getEntity(), event.getSource())
                .filter(applying.request()::equals).isPresent()) return false;
        if (applying.reactions().cancelDamageAttempt(event.getEntity(), event.getSource(), event.getAmount(), applying.request()))
            event.setCanceled(true);
        event.setInvulnerabilityTicks(1);
        return true;
    }
    public static Receipt apply(ServerLevel level, LivingEntity source, LivingEntity target, UUID request,
            String nativeType, String channel, float amount, DamageAttempt reactions) {
        if (!level.getServer().isSameThread() || target.level() != level || source.level() != level
                || target.isRemoved() || !target.isAlive() || level.getEntity(target.getUUID()) != target)
            return new Receipt(OperationRecord.Outcome.REJECTED, 0, 0, 0, "target instance unavailable");
        if (APPLYING.get() != null) throw new IllegalStateException("Nested numerical application requires a child boundary");
        var world = CombatAccess.find((io.github.fatalitaya.combatnexus.combat.api.WorldLookup) level).orElseThrow();
        try (var session = world.drivers().bind(Objects.requireNonNull(registration), target.getUUID(), Set.of(DriverScope.NUMERICAL), Optional.empty())) {
            var damage = new ResolvedDamage(request,
                new CombatSource(NexusId.parse("dndturn:tactical"), Optional.of(source.getUUID()), Optional.empty()),
                NexusId.parse(nativeType), List.of(new ResolvedDamage.Component(NexusId.parse(channel), amount)));
            APPLYING.set(new Applying(request, session, reactions));
            try {
                var result = session.damage(damage);
                var settlement = result.settlement();
                if (settlement.isEmpty()) return new Receipt(result.status() == io.github.fatalitaya.combatnexus.combat.damage.api.damage.DamageSubmissionStatus.REJECTED
                    ? OperationRecord.Outcome.REJECTED : OperationRecord.Outcome.UNKNOWN, 0, 0, 0, result.status().name());
                var value = settlement.get();
                var evidence = value.evidence();
                boolean unknown = evidence.journal().entries().stream().anyMatch(e -> e.observation() == ApplicationJournal.Observation.UNKNOWN);
                double written = evidence.healthLoss() + value.absorptionDamage() + evidence.shieldLoss();
                var outcome = unknown ? OperationRecord.Outcome.UNKNOWN
                    : evidence.applicationCompleted() && (result.applied() || written > 0) ? OperationRecord.Outcome.COMPLETED
                    : written > 0 ? OperationRecord.Outcome.PARTIAL
                    : result.status() == io.github.fatalitaya.combatnexus.combat.damage.api.damage.DamageSubmissionStatus.FAILED ? OperationRecord.Outcome.UNKNOWN
                    : OperationRecord.Outcome.REJECTED;
                return new Receipt(outcome, evidence.healthLoss(), value.absorptionDamage(), evidence.shieldLoss(), result.status().name());
            } finally { APPLYING.remove(); }
        }
    }
}
