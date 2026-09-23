package cc.sighs.dndturn.platform.server.effect;

import cc.sighs.dndturn.domain.ability.ProcessState;
import cc.sighs.dndturn.domain.encounter.operation.CausalEvent;
import cc.sighs.dndturn.domain.encounter.operation.Intervention;
import cc.sighs.dndturn.platform.server.ability.NativeObservations;

import cc.sighs.dndturn.domain.ability.AbilityBinding;
import cc.sighs.dndturn.domain.action.ExecutionRequest;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.actor.TriggeredAbilityInvocation;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.encounter.operation.TriggeredExecutionRecord;
import cc.sighs.dndturn.domain.resolution.ResolutionContext;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.ability.AbilityExecutor;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.action.MinecraftCoordinates;
import cc.sighs.dndturn.platform.server.actor.MinecraftSnapshotCapture;
import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
import java.util.*;

/** Server-thread driver for durable rule emissions. Uses the shared registry, resolver and operation authority. */
public final class TriggeredAbilities {
    public record Context(EncounterRuntime service, EncounterAuthority engine, LiveActorContext actor,
                          TriggeredExecutionRecord emission, OperationRecord.Snapshot operation) {}
    private final EncounterRuntime service;
    private final EncounterAuthority engine;
    public TriggeredAbilities(EncounterRuntime service, EncounterAuthority engine) { this.service = service; this.engine = engine; }

    public void drain(LiveActorContext actor, OperationRecord.Snapshot parent, TriggeredAbilityInvocation.Timing timing) {
        for (var emission : service.actorStates().invocations(actor.id())) {
            if (emission.event().causal() != null && emission.event().causal().phase() == CausalEvent.Phase.ATTEMPT) continue;
            if (emission.status() == TriggeredExecutionRecord.Status.STARTED) {
                service.actorStates().invocationStatus(emission.operation(), TriggeredExecutionRecord.Status.UNKNOWN, "execution interrupted; not replayed");
                continue;
            }
            if (emission.status() != TriggeredExecutionRecord.Status.PENDING || emission.invocation().timing() != timing) continue;
            if (timing == TriggeredAbilityInvocation.Timing.IMMEDIATE && !Objects.equals(parent.operationId(), emission.event().cause())) continue;
            execute(actor, parent, emission);
        }
    }
    public Intervention drainAttempt(LiveActorContext actor, OperationRecord.Snapshot parent, CausalEvent event) {
        var decision = Intervention.pass(event);
        for (var emission : service.actorStates().invocations(actor.id())) {
            if (!event.equals(emission.event().causal())) continue;
            if (emission.invocation().timing() != TriggeredAbilityInvocation.Timing.IMMEDIATE)
                throw new IllegalStateException("attempt reaction must resolve at its causal boundary");
            if (emission.status() == TriggeredExecutionRecord.Status.PENDING) execute(actor, parent, emission);
            var settled = service.actorStates().invocation(emission.operation());
            if (settled.status() == TriggeredExecutionRecord.Status.REJECTED) continue;
            if (settled.status() != TriggeredExecutionRecord.Status.COMPLETED || settled.intervention() == null)
                throw new IllegalStateException("reaction outcome uncertain; original effect not committed");
            decision = decision.combine(settled.intervention());
        }
        return decision;
    }
    private void execute(LiveActorContext actor, OperationRecord.Snapshot parent, TriggeredExecutionRecord emission) {
        OperationRecord.Snapshot operation = null;
        AbilityExecutor executor = null;
        Context context = null;
        boolean admitted = false;
        boolean processOpened = false;
        boolean started = false;
        try {
            actor.verifyCurrent();
            if (!parent.encounterId().equals(service.worldOutcomes().canonicalOutcomeEncounter(emission.encounter())))
                throw new IllegalStateException("trigger encounter changed or missing");
            if (!actor.level().tickRateManager().runsNormally()) throw new IllegalStateException("global freeze holds triggered execution");
            if (!parent.equals(engine.pendingOperation(parent.encounterId(), parent.operationId())))
                throw new IllegalStateException("trigger parent is no longer pending");
            var completed = engine.resultFor(parent.encounterId(), emission.operation());
            if (completed != null) {
                service.actorStates().invocationStatus(emission.operation(),
                        completed.outcome() == OperationRecord.Outcome.UNKNOWN ? TriggeredExecutionRecord.Status.UNKNOWN
                        : completed.outcome() == OperationRecord.Outcome.REJECTED ? TriggeredExecutionRecord.Status.REJECTED : TriggeredExecutionRecord.Status.COMPLETED,
                        "existing operation result");
                return;
            }
            var definition = AbilityAdapterRegistry.definitions().require(emission.invocation().ability(), emission.invocation().version());
            var binding = new AbilityBinding(definition, emission.source().grant());
            var intent = binding.invocation(emission.invocation().target());
            executor = AbilityAdapterRegistry.resolve(intent);
            executor.prepareTriggered(actor, emission);
            var capture = MinecraftSnapshotCapture.captureActor(actor);
            // This binding is scoped to the persisted reaction; it is never published as a current grant.
            var snapshot = new ActorSnapshot(capture.actor(), capture.instance(), capture.definition(), capture.revision(),
                    capture.facts(), List.of(binding), capture.effects());
            var state = engine.stateView(parent.encounterId());
            var nativeContext = MinecraftSnapshotCapture.capture(actor, snapshot, intent, state, binding.source(), ResolutionContext.Stage.PROPOSAL);
            var resolutionContext = new ResolutionContext(nativeContext.actor(), nativeContext.encounter(), nativeContext.invocation(),
                    nativeContext.world(), nativeContext.targetFacts(), nativeContext.stage(), emission);
            var resolution = AbilityAdapterRegistry.definitions().resolve(resolutionContext);
            AbilityAdapterRegistry.requireAccepted(resolution);
            if (resolution.status() != cc.sighs.dndturn.domain.resolution.RuleResolver.Status.ALLOWED)
                throw new IllegalStateException("trigger requires an executable current position");
            var request = ExecutionRequest.from(emission.operation(), resolutionContext, resolution);
            operation = new OperationRecord.Snapshot(emission.operation(), parent.operationId(), parent.encounterId(), actor.id(), actor.id(),
                    intent.target().entity(), service.actionHost().planClock(), state.version(), MinecraftCoordinates.cell(actor.body().blockPosition()),
                    intent.target().cell(), OperationRecord.Kind.TRIGGERED, service.generation(), intent);
            var processDefinition = executor.processDefinition();
            var processOwner = new ProcessState.Owner(ProcessState.Ownership.ACTOR, actor.id());
            var root = engine.operationRoot(parent.encounterId(), parent.operationId());
            int roundTicks = engine.movementTicksPerTurn(parent.encounterId());
            service.processes().validateOpen(emission.operation(), processDefinition, processOwner,
                    operation.operationId(), root, parent.encounterId(), actor.instance(), roundTicks, "EXECUTE");
            if (!engine.beginTriggeredOperation(operation, request)) throw new IllegalStateException("trigger admission rejected");
            admitted = true;
            context = new Context(service, engine, actor, emission, operation);
            service.processes().open(emission.operation(), processDefinition, processOwner,
                    operation.operationId(), root, parent.encounterId(), actor.instance(), roundTicks, "EXECUTE");
            processOpened = true;
            service.processes().observe(emission.operation(), "EXECUTE", 0, true,
                    ProcessState.Release.HELD, null, "");
            service.actorStates().invocationStatus(emission.operation(), TriggeredExecutionRecord.Status.STARTED, "native execution started");
            started = true;
            var outcome = executor.executeTriggered(context);
            if (outcome != OperationRecord.Outcome.COMPLETED && outcome != OperationRecord.Outcome.PARTIAL)
                throw new IllegalStateException("invalid triggered observation outcome");
            var details = NativeObservations.REGISTRY.validate(
                    executor.observeNative(actor, emission.operation(), intent), Set.of(), emission.operation());
            var observation = service.actorStates().invocation(emission.operation()).observation();
            if (observation == null) observation = new cc.sighs.dndturn.domain.encounter.operation.WorldOutcomeObservation(
                    List.of(), List.of(), List.of(), List.of(), actor.body().isRemoved(), 0, 0);
            var combined = new ArrayList<>(observation.details()); combined.addAll(details);
            service.actorStates().observeInvocation(emission.operation(), observation.withDetails(combined));
            NativeObservations.REGISTRY.validate(details, executor.requiredObservations(), emission.operation());
            if (emission.event().causal() != null && emission.event().causal().phase() == CausalEvent.Phase.ATTEMPT)
                service.actorStates().intervention(emission.operation(), executor.intervention(context));
            executor.releaseTriggered(context);
            context = null;
            service.processes().observe(emission.operation(), "TERMINAL", 0, false,
                    ProcessState.Release.RELEASED, outcome, "confirmed native execution");
            engine.publish(parent.encounterId(), emission.operation(), 0, outcome, "triggered ability settled", 0, 0, true);
            service.actorStates().invocationStatus(emission.operation(), TriggeredExecutionRecord.Status.COMPLETED, "confirmed native execution");
        } catch (RuntimeException failure) {
            boolean released = true;
            if (context != null) try { executor.releaseTriggered(context); } catch (RuntimeException release) { failure.addSuppressed(release); released = false; }
            var process = service.processes().find(emission.operation());
            if (processOpened && process != null && process.terminal() == null)
                service.processes().observe(emission.operation(), "TERMINAL", process.steps(), process.nativePending(),
                        released ? ProcessState.Release.RELEASED : ProcessState.Release.FAILED,
                        started ? OperationRecord.Outcome.UNKNOWN : OperationRecord.Outcome.REJECTED,
                        "trigger failed: " + failure.getClass().getSimpleName());
            if (admitted && engine.pendingOperation(parent.encounterId(), operation.operationId()) != null)
                engine.publish(parent.encounterId(), operation.operationId(), 0, started ? OperationRecord.Outcome.UNKNOWN : OperationRecord.Outcome.REJECTED,
                        "trigger failed: " + failure.getClass().getSimpleName(), 0, 0, true);
            service.actorStates().invocationStatus(emission.operation(), started ? TriggeredExecutionRecord.Status.UNKNOWN : TriggeredExecutionRecord.Status.REJECTED,
                    "trigger failed: " + failure.getClass().getSimpleName());
            if (started) throw failure;
        }
    }
}
