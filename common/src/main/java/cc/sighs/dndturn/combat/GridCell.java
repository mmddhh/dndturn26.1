package cc.sighs.dndturn.combat;

public record GridCell(int x, int y, int z) {
    public int chebyshev(GridCell other) {
        long distance = Math.max(Math.max(Math.abs((long) x - other.x), Math.abs((long) y - other.y)),
            Math.abs((long) z - other.z));
        return (int) Math.min(distance, Integer.MAX_VALUE);
    }
}
