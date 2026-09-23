package cc.sighs.dndturn.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Bounded, resumable pure-value cursor. A failed evaluation does not discard completed entries. */
public final class BudgetedScan<I, O> {
    private final List<I> inputs;
    private final List<O> results = new ArrayList<>();
    private int cursor;
    public BudgetedScan(List<I> inputs, int maximum) {
        if (maximum < 0 || inputs.size() > maximum) throw new IllegalArgumentException("scan bound");
        this.inputs = List.copyOf(inputs);
    }
    public boolean advance(BooleanSupplier budget, Function<I, O> evaluate) {
        while (cursor < inputs.size()) {
            if (!budget.getAsBoolean()) return false;
            O value = evaluate.apply(inputs.get(cursor));
            if (value != null) results.add(value);
            cursor++;
        }
        return true;
    }
    public int completed() { return cursor; }
    public List<O> results() {
        if (cursor != inputs.size()) throw new IllegalStateException("scan incomplete");
        return List.copyOf(results);
    }
}
