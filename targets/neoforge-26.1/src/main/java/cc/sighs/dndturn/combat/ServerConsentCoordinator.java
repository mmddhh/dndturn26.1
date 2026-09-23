package cc.sighs.dndturn.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread preflight workflow; pending windows never register a simulation region. */
final class ServerConsentCoordinator {
    private static final long WINDOW_TICKS = 400;
    private final MinecraftServer server;
    @FunctionalInterface interface RegionSampler {
        EncounterRegion sample(ServerPlayer player, EncounterRegion.Discovery discovery);
    }
    @FunctionalInterface interface EncounterCommitter {
        void commit(ServerPlayer anchor, EncounterRegion region, Set<UUID> players, Map<UUID, UUID> requestOwners);
    }
    private final UUID generation;
    private final java.util.function.Predicate<UUID> isMember;
    private final RegionSampler sampler;
    private final EncounterCommitter committer;
    private final Map<UUID, Group> requests = new LinkedHashMap<>();
    private final Map<UUID, Group> aliases = new HashMap<>();
    private final Map<UUID, UUID> owners = new HashMap<>();
    private final Map<UUID, CombatNetwork.ConsentReply> replies = new HashMap<>();
    private long sequence;

    private static final class Group {
        final ConsentWindow window;
        EncounterRegion region;
        final Map<UUID, UUID> requestOwners = new LinkedHashMap<>();
        final Set<UUID> recipients = new HashSet<>();
        String reason = "等待名单内所有玩家同意";
        Group(UUID id, long tick, EncounterRegion region, Set<UUID> members) {
            this.window = new ConsentWindow(id, tick, WINDOW_TICKS, members);
            this.region = region;
            recipients.addAll(members);
        }
    }

    ServerConsentCoordinator(MinecraftServer server, UUID generation,
            java.util.function.Predicate<UUID> isMember, RegionSampler sampler, EncounterCommitter committer) {
        this.server = server; this.generation = generation;
        this.isMember = isMember; this.sampler = sampler; this.committer = committer;
    }

    ConsentWindow.View view(UUID id) {
        Group group = aliases.get(id);
        return group == null ? null : group.window.view();
    }

    String reason(UUID id) {
        Group group = aliases.get(id);
        return group == null ? "unknown start request" : group.reason;
    }

    void resend(ServerPlayer player, UUID id) {
        Group group = aliases.get(id);
        if (group != null && group.recipients.contains(player.getUUID())) send(group);
    }

    void request(ServerPlayer player, UUID requestId) {
        if (replies.containsKey(requestId)) throw new IllegalStateException("consent operation ID payload conflict");
        UUID priorOwner = owners.get(requestId);
        if (priorOwner != null) {
            if (!priorOwner.equals(player.getUUID())) throw new IllegalStateException("start operation ID payload conflict");
            send(aliases.get(requestId));
            return;
        }
        for (Group group : requests.values()) if (group.requestOwners.containsValue(player.getUUID()))
            throw new IllegalStateException("a consent request from this player is already pending");
        EncounterRegion region = sampler.sample(player, null);
        Group group = new Group(requestId, server.getTickCount(), region, members(region));
        group.requestOwners.put(requestId, player.getUUID());
        group.recipients.add(player.getUUID());
        // START itself is the initiating player's consent; leaving later still revokes it.
        group.window.respond(player.getUUID(), 0, true, server.getTickCount());
        owners.put(requestId, player.getUUID());
        aliases.put(requestId, group);
        requests.put(requestId, group);
        tick();
        send(aliases.get(requestId));
    }

    void reply(ServerPlayer player, CombatNetwork.ConsentReply reply) {
        if (!generation.equals(reply.generation())) throw new IllegalStateException("consent epoch expired");
        if (owners.containsKey(reply.operationId())) throw new IllegalStateException("consent operation ID payload conflict");
        Group group = aliases.get(reply.requestId());
        if (group == null) throw new IllegalStateException("unknown consent request");
        CombatNetwork.ConsentReply previous = replies.get(reply.operationId());
        if (previous != null) {
            if (!previous.equals(reply) || !player.getUUID().equals(reply.playerId()))
                throw new IllegalStateException("consent operation ID payload conflict");
            send(group);
            return;
        }
        if (!player.getUUID().equals(reply.playerId())) throw new IllegalStateException("consent identity mismatch");
        // Refresh immediately before authorization; a packet cannot preserve consent after walking out.
        group.window.refresh(members(group.region), server.getTickCount());
        group.window.respond(player.getUUID(), reply.revision(), reply.agree(), server.getTickCount());
        replies.put(reply.operationId(), reply);
        send(group);
        tick();
    }

    void disconnected(UUID player) {
        for (Group group : List.copyOf(requests.values())) {
            group.window.disconnected(player);
            if (group.window.view().status() == ConsentWindow.Status.DISCONNECTED) {
                group.reason = "名单内玩家掉线，请求已取消";
                finish(group);
            }
        }
    }

    void tick() {
        long now = server.getTickCount();
        for (Group group : List.copyOf(requests.values())) {
            var before = group.window.view();
            group.window.refresh(members(group.region), now);
            if (group.window.view().status() != ConsentWindow.Status.WAITING) { finish(group); continue; }
            if (!before.equals(group.window.view())) send(group);
        }
        boolean merged;
        do {
            merged = false;
            List<Group> groups = new ArrayList<>(requests.values());
            outer: for (int i = 0; i < groups.size(); i++) for (int j = i + 1; j < groups.size(); j++) {
                Group first = groups.get(i), second = groups.get(j);
                if (!first.region.overlaps(second.region)) continue;
                first.region = union(first.region, second.region);
                first.window.absorb(second.window, members(first.region), now);
                first.requestOwners.putAll(second.requestOwners);
                first.recipients.addAll(second.recipients);
                for (UUID id : second.requestOwners.keySet()) aliases.put(id, first);
                requests.remove(second.window.view().id());
                send(first);
                merged = true;
                break outer;
            }
        } while (merged);
        for (Group group : List.copyOf(requests.values())) {
            if (group.window.ready(now)) {
                try {
                    // Re-sample actual anchors before fixing the encounter. Newly covered players
                    // enter the roster and must consent; an old snapshot cannot grant their approval.
                    ServerPlayer anchor = group.requestOwners.values().stream()
                        .map(server.getPlayerList()::getPlayer).filter(java.util.Objects::nonNull)
                        .filter(player -> group.window.view().members().contains(player.getUUID())
                            && !isMember.test(player.getUUID()))
                        .filter(player -> group.region.discovery().contains(center(player)))
                        .min(Comparator.comparing(ServerPlayer::getUUID)).orElseThrow(
                            () -> new IllegalStateException("no consenting player remains inside discovery"));
                    group.region = sampler.sample(anchor, group.region.discovery());
                    group.window.refresh(members(group.region), now);
                    if (!group.window.ready(now)) { send(group); continue; }
                    // A changed proposal may now overlap another waiting request. Merge it first.
                    if (requests.values().stream().anyMatch(other -> other != group && other.region.overlaps(group.region)))
                        continue;
                    committer.commit(anchor, group.region, group.window.view().members(), group.requestOwners);
                    group.window.commit(now);
                    group.reason = "全员同意，战斗会话已建立";
                } catch (RuntimeException failure) {
                    group.window.cancel();
                    group.reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                }
                if (group.window.view().status() != ConsentWindow.Status.WAITING) finish(group);
            } else if (now % 20 == 0) send(group); // authoritative remaining time, even during global freeze
        }
    }

    private Set<UUID> members(EncounterRegion region) {
        Set<UUID> result = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || !player.level().dimension().identifier().toString().equals(region.dimension())) continue;
            var point = center(player);
            if (region.containsPoint(point.x(), point.y(), point.z())) result.add(player.getUUID());
        }
        return result;
    }

    private static EncounterRegion.Point center(ServerPlayer player) {
        var point = player.getBoundingBox().getCenter();
        return new EncounterRegion.Point(point.x, point.y, point.z);
    }

    private void finish(Group group) {
        requests.remove(group.window.view().id());
        if (group.window.view().status() == ConsentWindow.Status.EXPIRED) group.reason = "同意请求已超时";
        if (group.window.view().status() == ConsentWindow.Status.DECLINED) group.reason = "有玩家拒绝，请求已取消";
        send(group);
    }

    private void send(Group group) {
        var view = group.window.view();
        group.recipients.addAll(view.members());
        List<CombatNetwork.ConsentMember> roster = view.members().stream().sorted().map(id -> {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            return new CombatNetwork.ConsentMember(id, member == null ? id.toString() : member.getName().getString(),
                view.approvals().contains(id));
        }).toList();
        long next = Math.addExact(sequence, 1);
        sequence = next;
        for (UUID id : group.recipients) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            CombatNetwork.sendConsentState(player, new CombatNetwork.ConsentState(generation, next,
                view.id(), view.revision(), view.deadline(), server.getTickCount(),
                view.status() == ConsentWindow.Status.WAITING && view.members().contains(id),
                view.members().contains(id) ? roster : List.of(), group.reason));
        }
    }

    private static EncounterRegion union(EncounterRegion first, EncounterRegion second) {
        var a = first.discovery(); var b = second.discovery();
        var discovery = new EncounterRegion.Discovery(Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()),
            Math.min(a.minZ(), b.minZ()), Math.max(a.maxX(), b.maxX()), Math.max(a.maxY(), b.maxY()), Math.max(a.maxZ(), b.maxZ()));
        Map<UUID, EncounterRegion.Anchor> anchors = new LinkedHashMap<>();
        first.anchors().forEach(anchor -> anchors.put(anchor.entityId(), anchor));
        second.anchors().forEach(anchor -> anchors.put(anchor.entityId(), anchor));
        return EncounterRegion.generate(first.dimension(), discovery, List.copyOf(anchors.values()),
            Math.max(first.radius(), second.radius()), Math.max(first.version(), second.version()) + 1);
    }
}
