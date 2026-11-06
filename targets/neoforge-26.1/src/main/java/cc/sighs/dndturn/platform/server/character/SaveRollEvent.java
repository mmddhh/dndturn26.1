package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.Ability;
import java.util.UUID;
import net.neoforged.bus.api.Event;

/**
 * Fired after the d20 is rolled but before the save result is committed.
 * Listeners may change the die, bonus or DC; the service reads the final values after the event.
 */
public final class SaveRollEvent extends Event {
    private final UUID actor;
    private final Ability ability;
    private final String source;
    private int die;
    private int bonus;
    private int dc;

    public SaveRollEvent(UUID actor, Ability ability, int die, int bonus, int dc, String source) {
        this.actor = actor;
        this.ability = ability;
        this.die = die;
        this.bonus = bonus;
        this.dc = dc;
        this.source = source;
    }

    public UUID actor() { return actor; }
    public Ability ability() { return ability; }
    public String source() { return source; }
    public int die() { return die; }
    public int bonus() { return bonus; }
    public int dc() { return dc; }
    public void setDie(int value) {
        if (value < 1 || value > 20) throw new IllegalArgumentException("save die");
        die = value;
    }
    public void setBonus(int value) {
        if (value < -50 || value > 100) throw new IllegalArgumentException("save bonus");
        bonus = value;
    }
    public void setDc(int value) {
        if (value < 0 || value > 100) throw new IllegalArgumentException("save dc");
        dc = value;
    }
    public int total() { return die + bonus; }
    public boolean success() { return total() >= dc; }
}
