package cc.sighs.dndturn.platform.client.action;

import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.platform.network.ActionProtocol;
import java.util.List;

/** Chooses a proposal from confirmed bindings, never an execution permission. */
public final class ItemUseSelection {
    private ItemUseSelection() {}

    public static boolean includes(ActionProtocol.Ability ability) {
        return includes(ability.kind(), ability.source(), ability.id());
    }

    public static boolean includes(ActionIntent intent) {
        return includes(intent.capability(), intent.source(), intent.behaviorId());
    }

    private static boolean includes(ActionIntent.Capability kind, GrantEvidence source, String id) {
        if (source == null || source.kind() != GrantEvidence.Kind.EQUIPMENT) return false;
        return switch (kind) {
            case USE_ITEM, PLACE, EQUIP -> true;
            case ATTACK -> !id.equals("dndturn:melee");
            default -> false;
        };
    }

    public static ActionProtocol.Ability resolve(List<ActionProtocol.Ability> abilities, ActionIntent.TargetKind target) {
        var uses = abilities.stream().filter(ItemUseSelection::includes).toList();
        var dedicated = uses.stream().filter(a -> !fallback(a.id())).toList();
        var exact = dedicated.stream().filter(a -> a.targets().contains(target)).findFirst().orElse(null);
        if (exact != null) return exact;
        // Entity interaction is a distinct native entry (e.g. milk a cow with a bucket).
        // A self-use item can also have useOn semantics (e.g. a water potion on dirt).
        // Never redirect a positional ability to self when the cursor misses.
        if (!dedicated.isEmpty() && target != ActionIntent.TargetKind.ENTITY
            && !(target == ActionIntent.TargetKind.BLOCK && dedicated.stream()
                .allMatch(a -> a.targets().equals(java.util.Set.of(ActionIntent.TargetKind.SELF))))) return null;
        String id = switch (target) {
            case ENTITY -> "dndturn:entity_item";
            case BLOCK -> "dndturn:tool";
            default -> "dndturn:native_use";
        };
        return uses.stream().filter(a -> a.id().equals(id) && a.targets().contains(target)).findFirst().orElse(null);
    }

    private static boolean fallback(String id) {
        return id.equals("dndturn:tool") || id.equals("dndturn:entity_item") || id.equals("dndturn:native_use");
    }
}
