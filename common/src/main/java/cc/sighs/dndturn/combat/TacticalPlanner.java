package cc.sighs.dndturn.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/** Plans from probe measurements. A proposal must be checked again before movement. */
public final class TacticalPlanner {
    private TacticalPlanner() {}

    public interface CellProbe {
        /** True only when the entire footprint can occupy the cell. */
        boolean canOccupy(GridCell cell);
        /** Search weight only; zero or negative means the edge is closed. It is never a movement charge. */
        int traversalCost(GridCell from, GridCell to);
    }

    public record Proposal(List<GridCell> cells, int cost, long probeVersion) {
        public Proposal { cells = List.copyOf(cells); }
    }

    public static Proposal propose(GridCell start, GridCell goal, int maxCost, int maxNodes,
                                   long probeVersion, CellProbe probe) {
        return propose(start, java.util.Set.of(Objects.requireNonNull(goal)), maxCost, maxNodes, probeVersion, probe);
    }

    public static Proposal propose(GridCell start, java.util.Set<GridCell> goals, int maxCost, int maxNodes,
                                   long probeVersion, CellProbe probe) {
        Objects.requireNonNull(start);
        goals = java.util.Set.copyOf(goals);
        Objects.requireNonNull(probe);
        if (maxCost < 0 || maxNodes < 1) throw new IllegalArgumentException("planner budget");
        record Node(GridCell cell, int cost) {}
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingInt(Node::cost));
        Map<GridCell, Integer> distance = new HashMap<>();
        Map<GridCell, GridCell> parent = new HashMap<>();
        distance.put(start, 0);
        queue.add(new Node(start, 0));
        while (!queue.isEmpty()) {
            Node node = queue.remove();
            if (node.cost() != distance.getOrDefault(node.cell(), Integer.MAX_VALUE)) continue;
            if (goals.contains(node.cell())) {
                ArrayDeque<GridCell> reverse = new ArrayDeque<>();
                GridCell cursor = node.cell();
                while (!cursor.equals(start)) {
                    reverse.addFirst(cursor);
                    cursor = parent.get(cursor);
                }
                return new Proposal(new ArrayList<>(reverse), node.cost(), probeVersion);
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if ((dx | dy | dz) == 0) continue;
                        GridCell next = offset(node.cell(), dx, dy, dz);
                        if (next == null) continue;
                        if (!probe.canOccupy(next)) continue;
                        int edge = probe.traversalCost(node.cell(), next);
                        if (edge <= 0 || edge > maxCost - node.cost()) continue;
                        int cost = node.cost() + edge;
                        if (cost >= distance.getOrDefault(next, Integer.MAX_VALUE)) continue;
                        if (!distance.containsKey(next) && distance.size() >= maxNodes)
                            throw new IllegalStateException("planner node budget exceeded");
                        distance.put(next, cost);
                        parent.put(next, node.cell());
                        queue.add(new Node(next, cost));
                    }
                }
            }
        }
        return new Proposal(List.of(), Integer.MAX_VALUE, probeVersion);
    }

    public static boolean revalidate(GridCell start, Proposal proposal, long currentProbeVersion, CellProbe probe) {
        if (proposal.probeVersion() != currentProbeVersion) return false;
        if (proposal.cells().isEmpty()) return proposal.cost() == 0;
        GridCell from = start;
        int actualCost = 0;
        for (GridCell to : proposal.cells()) {
            if (from.chebyshev(to) != 1 || !probe.canOccupy(to)) return false;
            int edge = probe.traversalCost(from, to);
            if (edge <= 0) return false;
            actualCost = Math.addExact(actualCost, edge);
            from = to;
        }
        return actualCost == proposal.cost();
    }

    private static GridCell offset(GridCell cell, int dx, int dy, int dz) {
        long x = (long) cell.x() + dx;
        long y = (long) cell.y() + dy;
        long z = (long) cell.z() + dz;
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
            || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) return null;
        return new GridCell((int) x, (int) y, (int) z);
    }
}
