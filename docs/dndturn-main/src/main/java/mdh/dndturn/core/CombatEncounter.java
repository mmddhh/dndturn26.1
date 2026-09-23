package mdh.dndturn.core;

import mdh.dndturn.grid.GridPathfinder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CombatEncounter {

    private final UUID id = UUID.randomUUID();
    private final ServerLevel level;
    private final Grid grid;
    private final List<Combatant> combatants = new ArrayList<>();
    private final Map<UUID, Combatant> byId = new LinkedHashMap<>();
    private final List<UUID> turnOrder = new ArrayList<>();
    private final Map<UUID, Boolean> previousNoAi = new HashMap<>();
    private final Map<UUID, Boolean> previousNoGravity = new HashMap<>();

    private int turnIndex;
    private int round;
    private boolean active = true;
    private boolean hadPlayers;
    private boolean hadHostile;
    private GridPathfinder.Result currentReachable;

    public CombatEncounter(ServerLevel level, Grid grid) {
        this.level = level;
        this.grid = grid;
    }

    public UUID getId() {
        return id;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public Grid getGrid() {
        return grid;
    }

    public List<Combatant> getCombatants() {
        return combatants;
    }

    public Map<UUID, Boolean> getPreviousNoAi() {
        return previousNoAi;
    }

    public Map<UUID, Boolean> getPreviousNoGravity() {
        return previousNoGravity;
    }

    public int getRound() {
        return round;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public GridPathfinder.Result getCurrentReachable() {
        return currentReachable;
    }

    public void setCurrentReachable(GridPathfinder.Result currentReachable) {
        this.currentReachable = currentReachable;
    }

    public void addCombatant(Combatant combatant) {
        combatants.add(combatant);
        byId.put(combatant.getEntityId(), combatant);
        if (combatant.isPlayerSide()) {
            hadPlayers = true;
        } else if (combatant.isHostile()) {
            hadHostile = true;
        }
    }

    public void appendTurnOrder(UUID entityId) {
        if (!turnOrder.contains(entityId)) {
            turnOrder.add(entityId);
        }
    }

    public void noteHostile() {
        this.hadHostile = true;
    }

    public boolean hasHostileMonsters() {
        for (Combatant c : combatants) {
            if (c.isPlayerSide() || !c.isHostile()) {
                continue;
            }
            LivingEntity entity = getEntity(c);
            if (entity != null && entity.isAlive()) {
                return true;
            }
        }
        return false;
    }

    public Combatant getCombatant(UUID entityId) {
        return byId.get(entityId);
    }

    public Combatant current() {
        if (turnOrder.isEmpty()) {
            return null;
        }
        return byId.get(turnOrder.get(turnIndex));
    }

    public UUID currentId() {
        if (turnOrder.isEmpty()) {
            return null;
        }
        return turnOrder.get(turnIndex);
    }

    public void buildTurnOrder() {
        turnOrder.clear();
        List<Combatant> sorted = new ArrayList<>(combatants);
        sorted.sort(Comparator.comparingInt(Combatant::getInitiative).reversed());
        for (Combatant c : sorted) {
            turnOrder.add(c.getEntityId());
        }
        turnIndex = 0;
    }

    public void beginFirstRound() {
        round = 1;
    }

    public void advanceTurn() {
        if (turnOrder.isEmpty()) {
            return;
        }
        turnIndex++;
        if (turnIndex >= turnOrder.size()) {
            turnIndex = 0;
            round++;
        }
        skipDead();
    }

    private void skipDead() {
        int guard = 0;
        while (guard++ < turnOrder.size()) {
            Combatant c = current();
            LivingEntity entity = getEntity(c);
            if (entity != null && entity.isAlive()) {
                return;
            }
            turnIndex++;
            if (turnIndex >= turnOrder.size()) {
                turnIndex = 0;
                round++;
            }
        }
    }

    public LivingEntity getEntity(Combatant combatant) {
        if (combatant == null) {
            return null;
        }
        return getEntity(combatant.getEntityId());
    }

    public LivingEntity getEntity(UUID entityId) {
        Entity entity = level.getEntity(entityId);
        return entity instanceof LivingEntity living ? living : null;
    }

    public boolean isOver() {
        boolean playersAlive = false;
        boolean hostileAlive = false;
        for (Combatant c : combatants) {
            LivingEntity entity = getEntity(c);
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            if (c.isPlayerSide()) {
                playersAlive = true;
            } else if (c.isHostile()) {
                hostileAlive = true;
            }
        }
        if (hadPlayers && !playersAlive) {
            return true;
        }
        if (hadHostile && !hostileAlive) {
            return true;
        }
        return false;
    }
}
