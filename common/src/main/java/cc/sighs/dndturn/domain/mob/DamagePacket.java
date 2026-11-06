package cc.sighs.dndturn.domain.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Multi-component damage; each component is resisted/immunized independently. */
public record DamagePacket(List<DamageComponent> components) {
    public DamagePacket {
        components = List.copyOf(components);
        if (components.isEmpty() || components.size() > 8) throw new IllegalArgumentException("damage packet");
    }

    public record Resolved(DamageType type, int rolled, int applied) {}

    public List<Resolved> resolve(DefenseProfile defense, java.util.Random random) {
        Objects.requireNonNull(defense);
        Objects.requireNonNull(random);
        List<Resolved> out = new ArrayList<>(components.size());
        for (DamageComponent component : components) {
            int rolled = component.roll(random);
            out.add(new Resolved(component.type(), rolled, defense.apply(component.type(), rolled)));
        }
        return List.copyOf(out);
    }

    public int total(List<Resolved> resolved) {
        int total = 0;
        for (Resolved part : resolved) total = Math.addExact(total, part.applied());
        return total;
    }
}
