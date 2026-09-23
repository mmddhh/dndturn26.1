package mdh.dndturn.core;

import mdh.dndturn.Config;
import mdh.dndturn.combat.CombatAction;
import mdh.dndturn.combat.CombatStats;
import mdh.dndturn.grid.GridPathfinder;
import mdh.dndturn.mixin.CreeperAccessor;
import mdh.dndturn.network.DndTurnNetwork;
import mdh.dndturn.network.packet.CombatEndS2C;
import mdh.dndturn.network.packet.CombatLogS2C;
import mdh.dndturn.network.packet.CombatStateS2C;
import mdh.dndturn.network.packet.MovePlanS2C;
import mdh.dndturn.util.Dice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CombatManager {

    private static final Map<MinecraftServer, CombatManager> MANAGERS = new HashMap<>();

    private final Map<UUID, CombatEncounter> encounters = new LinkedHashMap<>();
    private final Map<UUID, UUID> entityToEncounter = new HashMap<>();

    public static CombatManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, s -> new CombatManager());
    }

    public static CombatManager getIfPresent(MinecraftServer server) {
        return MANAGERS.get(server);
    }

    public static void clear(MinecraftServer server) {
        MANAGERS.remove(server);
    }

    public static CombatEncounter encounterOf(Entity entity) {
        MinecraftServer server = entity.getServer();
        if (server == null) {
            return null;
        }
        CombatManager manager = MANAGERS.get(server);
        if (manager == null) {
            return null;
        }
        UUID encounterId = manager.entityToEncounter.get(entity.getUUID());
        return encounterId == null ? null : manager.encounters.get(encounterId);
    }

    public static boolean inCombat(Entity entity) {
        return encounterOf(entity) != null;
    }

    public static void requestEnterCombat(ServerPlayer player) {
        requestEnterCombat(player, Config.enterCombatRadius);
    }

    public static void requestEnterCombat(ServerPlayer player, int radius) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        get(server).enterCombat(player, radius);
    }

    public static void requestToggleCombat(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CombatManager manager = get(server);
        CombatEncounter existing = encounterOf(player);
        if (existing != null) {
            if (existing.hasHostileMonsters()) {
                player.sendSystemMessage(Component.literal("有敌人正在交战，无法退出").withStyle(ChatFormatting.RED));
            } else {
                manager.endEncounter(existing, "toggle");
            }
        } else {
            manager.enterCombat(player, Config.enterCombatRadius);
        }
    }

    public static void requestEndTurn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        get(server).endTurn(player);
    }

    public static void requestMove(ServerPlayer player, BlockPos target) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        get(server).moveTo(player, target);
    }

    public static void requestAction(ServerPlayer player, CombatAction action) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        get(server).performAction(player, action);
    }

    public static void requestAttack(ServerPlayer player, int networkId) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        get(server).handleAttack(player, networkId);
    }

    public CombatEncounter start(ServerLevel level, List<LivingEntity> participants) {
        if (participants == null || participants.isEmpty()) {
            return null;
        }
        int centerX = 0;
        int centerZ = 0;
        int centerY = participants.get(0).blockPosition().getY();
        for (LivingEntity entity : participants) {
            centerX += entity.blockPosition().getX();
            centerZ += entity.blockPosition().getZ();
        }
        centerX /= participants.size();
        centerZ /= participants.size();

        Grid grid = Grid.around(new BlockPos(centerX, centerY, centerZ), Config.gridRadius, centerY);
        CombatEncounter encounter = new CombatEncounter(level, grid);
        for (LivingEntity entity : participants) {
            boolean hostile = entity instanceof Mob mob && mob.getTarget() instanceof Player;
            addCombatant(encounter, entity, hostile);
        }
        encounters.put(encounter.getId(), encounter);
        encounter.buildTurnOrder();
        encounter.beginFirstRound();
        beginTurn(encounter);
        return encounter;
    }

    private void addCombatant(CombatEncounter encounter, LivingEntity entity, boolean hostile) {
        String side = entity instanceof Player ? Combatant.SIDE_PLAYERS : Combatant.SIDE_MONSTERS;
        Combatant combatant = new Combatant(entity.getUUID(), side);
        combatant.setHostile(!(entity instanceof Player) && hostile);
        combatant.setInitiative(Dice.d20(encounter.getLevel().random) + CombatStats.dexterityModifier(entity));
        BlockPos cell = GridPathfinder.findStandable(encounter.getLevel(), entity,
                entity.blockPosition().getX(), entity.blockPosition().getZ(), entity.blockPosition().getY());
        combatant.setCell(cell != null ? cell : entity.blockPosition());
        if (entity instanceof Mob mob) {
            encounter.getPreviousNoAi().put(entity.getUUID(), mob.isNoAi());
            mob.setNoAi(true);
            encounter.getPreviousNoGravity().put(entity.getUUID(), mob.isNoGravity());
            mob.setNoGravity(true);
        }
        encounter.addCombatant(combatant);
        entityToEncounter.put(entity.getUUID(), encounter.getId());
    }

    private void markHostile(CombatEncounter encounter, LivingEntity entity) {
        Combatant combatant = encounter.getCombatant(entity.getUUID());
        if (combatant == null || combatant.isPlayerSide()) {
            return;
        }
        combatant.setHostile(true);
        encounter.noteHostile();
    }

    public void addHostileToCombat(ServerLevel level, Monster monster, Player target) {
        if (encounterOf(monster) != null) {
            markHostile(encounterOf(monster), monster);
            return;
        }
        CombatEncounter encounter = encounterOf(target);
        if (encounter == null) {
            autoStart(level, monster, target);
            return;
        }
        addCombatant(encounter, monster, true);
        encounter.appendTurnOrder(monster.getUUID());
        broadcast(encounter);
    }

    private void enterCombat(ServerPlayer player, int radius) {
        if (encounterOf(player) != null) {
            player.sendSystemMessage(Component.literal("你已经处于战斗中").withStyle(ChatFormatting.YELLOW));
            return;
        }
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(radius);
        List<LivingEntity> participants = new ArrayList<>();
        participants.add(player);
        for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (other != player && encounterOf(other) == null) {
                participants.add(other);
            }
        }
        for (Monster monster : level.getEntitiesOfClass(Monster.class, box)) {
            if (encounterOf(monster) == null) {
                participants.add(monster);
            }
        }
        start(level, participants);
    }

    public void autoStart(ServerLevel level, LivingEntity trigger, LivingEntity target) {
        if (trigger == null || encounterOf(trigger) != null || encounterOf(target) != null) {
            return;
        }
        AABB box = trigger.getBoundingBox().inflate(Config.enterCombatRadius);
        List<LivingEntity> participants = new ArrayList<>();
        participants.add(target);
        for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (encounterOf(other) == null) {
                participants.add(other);
            }
        }
        for (Monster monster : level.getEntitiesOfClass(Monster.class, box)) {
            if (encounterOf(monster) == null) {
                participants.add(monster);
            }
        }
        CombatEncounter encounter = start(level, participants);
        if (encounter != null && trigger instanceof Mob) {
            markHostile(encounter, trigger);
            broadcast(encounter);
        }
    }

    public void tick(MinecraftServer server) {
        for (CombatEncounter encounter : new ArrayList<>(encounters.values())) {
            if (encounter.isActive()) {
                tickEncounter(encounter);
                capPrimedCreepers(encounter);
            }
        }
    }

    private void capPrimedCreepers(CombatEncounter encounter) {
        for (Combatant combatant : encounter.getCombatants()) {
            if (!combatant.isPrimed() || combatant.isPlayerSide()) {
                continue;
            }
            LivingEntity entity = encounter.getEntity(combatant);
            if (entity instanceof Creeper creeper && creeper.isAlive()) {
                CreeperAccessor accessor = (CreeperAccessor) (Object) creeper;
                int max = accessor.dndturn$getMaxSwell();
                accessor.dndturn$setSwell(Math.max(0, max - 2));
            }
        }
    }

    private void tickEncounter(CombatEncounter encounter) {
        if (encounter.isOver()) {
            endEncounter(encounter, "resolved");
            return;
        }
        Combatant combatant = encounter.current();
        if (combatant == null) {
            endEncounter(encounter, "empty");
            return;
        }
        LivingEntity entity = encounter.getEntity(combatant);
        if (entity == null || !entity.isAlive()) {
            advance(encounter);
            return;
        }
        if (combatant.isAttacking()) {
            tickAttack(encounter, combatant);
            return;
        }
        if (combatant.isMoving()) {
            if (combatant.isPlayerSide() && entity instanceof ServerPlayer serverPlayer) {
                tickPlayerMove(encounter, combatant, serverPlayer);
            } else {
                tickMovement(encounter, combatant, entity);
            }
            return;
        }
        snapToCell(entity, combatant.getCell());
        if (combatant.isPlayerSide()) {
            return;
        }
        combatant.setIdleTicks(combatant.getIdleTicks() + 1);
        if (combatant.getIdleTicks() >= Config.monsterTurnDelayTicks) {
            monsterAct(encounter, combatant, entity);
        }
    }

    private void monsterAct(CombatEncounter encounter, Combatant combatant, LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            advance(encounter);
            return;
        }
        LivingEntity target = aggroTarget(encounter, combatant, mob);
        if (target == null) {
            advance(encounter);
            return;
        }
        if (mob instanceof Creeper creeper) {
            creeperAct(encounter, combatant, creeper, target);
            return;
        }
        if (mob instanceof EnderMan enderman) {
            endermanAct(encounter, combatant, enderman, target);
            return;
        }
        if (chebyshev(mob.blockPosition(), target.blockPosition()) <= 1) {
            combatant.getEconomy().useAction();
            beginAttack(encounter, combatant, mob, target, true);
            return;
        }
        moveMonsterToward(encounter, combatant, mob, target);
    }

    private LivingEntity aggroTarget(CombatEncounter encounter, Combatant self, Mob mob) {
        LivingEntity current = mob.getTarget();
        if (current != null && current.isAlive() && isEnemyPlayer(encounter, current)) {
            return current;
        }
        LivingEntity seen = findVisiblePlayer(encounter, mob);
        if (seen != null) {
            mob.setTarget(seen);
            self.setHostile(true);
            encounter.noteHostile();
            return seen;
        }
        return null;
    }

    private boolean isEnemyPlayer(CombatEncounter encounter, LivingEntity entity) {
        if (!(entity instanceof Player)) {
            return false;
        }
        Combatant combatant = encounter.getCombatant(entity.getUUID());
        return combatant != null && combatant.isPlayerSide();
    }

    private LivingEntity findVisiblePlayer(CombatEncounter encounter, Mob mob) {
        double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        double best = range * range;
        LivingEntity found = null;
        for (Combatant c : encounter.getCombatants()) {
            if (!c.isPlayerSide()) {
                continue;
            }
            LivingEntity player = encounter.getEntity(c);
            if (player == null || !player.isAlive()) {
                continue;
            }
            double distance = player.distanceToSqr(mob);
            if (distance <= best && mob.hasLineOfSight(player)) {
                best = distance;
                found = player;
            }
        }
        return found;
    }

    private void moveMonsterToward(CombatEncounter encounter, Combatant combatant, Mob mob, LivingEntity target) {
        BlockPos targetCell = target.blockPosition();
        GridPathfinder.Result reachable = encounter.getCurrentReachable();
        if (reachable == null) {
            recomputeReachable(encounter, combatant, mob);
            reachable = encounter.getCurrentReachable();
        }
        BlockPos best = null;
        int bestCost = Integer.MAX_VALUE;
        if (reachable != null) {
            for (BlockPos cell : reachable.cells()) {
                int cost = reachable.cost(cell);
                if (chebyshev(cell, targetCell) <= 1 && cost < bestCost) {
                    best = cell;
                    bestCost = cost;
                }
            }
        }
        if (best != null) {
            combatant.getEconomy().spendMovement(bestCost);
            combatant.beginMove(reachable.pathTo(best));
            combatant.setIdleTicks(0);
            encounter.setCurrentReachable(null);
            broadcast(encounter);
        } else {
            advance(encounter);
        }
    }

    private void creeperAct(CombatEncounter encounter, Combatant combatant, Creeper creeper, LivingEntity target) {
        CreeperAccessor accessor = (CreeperAccessor) (Object) creeper;
        if (combatant.isPrimed()) {
            combatant.setPrimed(false);
            accessor.dndturn$explode();
            advance(encounter);
            return;
        }
        if (chebyshev(creeper.blockPosition(), target.blockPosition()) <= Config.creeperDetonateRange) {
            creeper.setSwellDir(1);
            combatant.setPrimed(true);
            advance(encounter);
            return;
        }
        moveMonsterToward(encounter, combatant, creeper, target);
    }

    private void endermanAct(CombatEncounter encounter, Combatant combatant, EnderMan enderman, LivingEntity target) {
        if (chebyshev(enderman.blockPosition(), target.blockPosition()) <= 1) {
            combatant.getEconomy().useAction();
            beginAttack(encounter, combatant, enderman, target, true);
            return;
        }
        BlockPos destination = findBlinkCell(encounter, enderman, target.blockPosition());
        if (destination != null && enderman.randomTeleport(destination.getX() + 0.5D, destination.getY(),
                destination.getZ() + 0.5D, true)) {
            encounter.getLevel().playSound(null, enderman.getX(), enderman.getY(), enderman.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
            combatant.setCell(destination);
            if (chebyshev(destination, target.blockPosition()) <= 1) {
                combatant.getEconomy().useAction();
                beginAttack(encounter, combatant, enderman, target, true);
            } else {
                advance(encounter);
            }
            return;
        }
        moveMonsterToward(encounter, combatant, enderman, target);
    }

    private BlockPos findBlinkCell(CombatEncounter encounter, EnderMan enderman, BlockPos targetCell) {
        Set<BlockPos> occupied = occupiedCells(encounter, null);
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int x = targetCell.getX() + dx;
                int z = targetCell.getZ() + dz;
                if (!encounter.getGrid().contains(x, z)) {
                    continue;
                }
                BlockPos cell = GridPathfinder.findStandable(encounter.getLevel(), enderman, x, z, targetCell.getY());
                if (cell == null || Math.abs(cell.getY() - targetCell.getY()) > 1) {
                    continue;
                }
                if (occupied.contains(cell)) {
                    continue;
                }
                candidates.add(cell);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(encounter.getLevel().random.nextInt(candidates.size()));
    }

    private Set<BlockPos> occupiedCells(CombatEncounter encounter, Combatant except) {
        Set<BlockPos> occupied = new HashSet<>();
        for (Combatant other : encounter.getCombatants()) {
            if (other == except) {
                continue;
            }
            LivingEntity otherEntity = encounter.getEntity(other);
            if (otherEntity == null || !otherEntity.isAlive()) {
                continue;
            }
            if (other.getCell() != null) {
                occupied.add(other.getCell());
            }
        }
        return occupied;
    }

    private void beginAttack(CombatEncounter encounter, Combatant attacker, LivingEntity attackerEntity,
                             LivingEntity target, boolean advanceAfter) {
        attacker.setAttackTargetId(target.getId());
        attacker.setAttackTotalTicks(Config.attackAnimationTicks);
        attacker.setAttackTicks(Config.attackAnimationTicks);
        attacker.setAttackApplied(false);
        attacker.setAdvanceAfterAttack(advanceAfter);
        attackerEntity.swing(InteractionHand.MAIN_HAND, true);
        encounter.getLevel().playSound(null, attackerEntity.getX(), attackerEntity.getY(), attackerEntity.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private void tickAttack(CombatEncounter encounter, Combatant combatant) {
        LivingEntity attacker = encounter.getEntity(combatant);
        LivingEntity target = null;
        Entity targetEntity = encounter.getLevel().getEntity(combatant.getAttackTargetId());
        if (targetEntity instanceof LivingEntity living) {
            target = living;
        }
        int remaining = combatant.getAttackTicks() - 1;
        combatant.setAttackTicks(remaining);
        int total = Math.max(1, combatant.getAttackTotalTicks());
        int elapsed = total - remaining;

        if (attacker != null && attacker.isAlive() && combatant.getCell() != null) {
            Vec3 base = new Vec3(combatant.getCell().getX() + 0.5D, combatant.getCell().getY(),
                    combatant.getCell().getZ() + 0.5D);
            Vec3 position = base;
            if (target != null) {
                Vec3 toTarget = new Vec3(target.getX() - base.x, 0.0D, target.getZ() - base.z);
                if (toTarget.lengthSqr() > 1.0E-6D) {
                    double progress = (double) elapsed / total;
                    double lunge = Math.sin(progress * Math.PI) * 0.35D;
                    position = base.add(toTarget.normalize().scale(lunge));
                }
            }
            teleportTo(position, attacker);
        }

        if (!combatant.isAttackApplied() && elapsed >= total / 2) {
            combatant.setAttackApplied(true);
            if (attacker != null && target != null && target.isAlive()) {
                resolveAttack(encounter, attacker, target);
            }
        }

        if (remaining <= 0) {
            if (attacker != null) {
                teleportToCell(attacker, combatant.getCell());
            }
            combatant.setAttackTargetId(-1);
            if (combatant.isAdvanceAfterAttack()) {
                advance(encounter);
            } else {
                broadcast(encounter);
            }
        }
    }

    private void resolveAttack(CombatEncounter encounter, LivingEntity attacker, LivingEntity defender) {
        int roll = Dice.d20(encounter.getLevel().random);
        int total = roll + CombatStats.proficiencyBonus(attacker) + CombatStats.dexterityModifier(attacker);
        Combatant defenderCombatant = encounter.getCombatant(defender.getUUID());
        int ac = 10 + defender.getArmorValue() + (defenderCombatant != null && defenderCombatant.isDodging() ? 2 : 0);
        String attackerName = attacker.getDisplayName().getString();
        String defenderName = defender.getDisplayName().getString();
        if (total >= ac) {
            int damage = attackDamage(attacker);
            defender.invulnerableTime = 0;
            defender.hurt(damageSource(encounter, attacker), damage);
            encounter.getLevel().sendParticles(ParticleTypes.CRIT,
                    defender.getX(), defender.getY() + defender.getBbHeight() * 0.6D, defender.getZ(),
                    8, 0.3D, 0.3D, 0.3D, 0.1D);
            encounter.getLevel().playSound(null, defender.getX(), defender.getY(), defender.getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.0F);
            sendLog(encounter, attackerName + " 命中 " + defenderName
                    + "（攻击 " + total + " vs AC " + ac + "），造成 " + damage + " 点伤害");
        } else {
            encounter.getLevel().sendParticles(ParticleTypes.SMOKE,
                    defender.getX(), defender.getY() + defender.getBbHeight() * 0.6D, defender.getZ(),
                    5, 0.2D, 0.2D, 0.2D, 0.02D);
            sendLog(encounter, attackerName + " 攻击 " + defenderName
                    + " 未命中（" + total + " vs AC " + ac + "）");
        }
    }

    private net.minecraft.world.damagesource.DamageSource damageSource(CombatEncounter encounter, LivingEntity attacker) {
        if (attacker instanceof Player player) {
            return encounter.getLevel().damageSources().playerAttack(player);
        }
        return encounter.getLevel().damageSources().mobAttack(attacker);
    }

    private int attackDamage(LivingEntity attacker) {
        double value = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (value <= 0.0D) {
            value = 3.0D;
        }
        return (int) Math.max(1, Math.round(value));
    }

    private int chebyshev(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }

    private void tickMovement(CombatEncounter encounter, Combatant combatant, LivingEntity entity) {
        if (combatant.getSegmentFrom() == null || combatant.getSegmentTo() == null) {
            combatant.stopMoving();
            return;
        }
        float step = 1.0F / Math.max(1, Config.movementAnimationTicks);
        float progress = combatant.getSegmentProgress() + step;
        if (progress >= 1.0F) {
            teleportToCell(entity, combatant.getSegmentTo());
            combatant.advanceSegment();
            if (!combatant.isMoving()) {
                recomputeReachable(encounter, combatant, entity);
                broadcast(encounter);
            }
            return;
        }
        combatant.setSegmentProgress(progress);
        BlockPos from = combatant.getSegmentFrom();
        BlockPos to = combatant.getSegmentTo();
        double x = from.getX() + (to.getX() - from.getX()) * (double) progress + 0.5D;
        double y = from.getY() + (to.getY() - from.getY()) * (double) progress;
        double z = from.getZ() + (to.getZ() - from.getZ()) * (double) progress + 0.5D;
        teleportTo(new Vec3(x, y, z), entity);
    }

    private void tickPlayerMove(CombatEncounter encounter, Combatant combatant, ServerPlayer player) {
        combatant.setMoveTicks(combatant.getMoveTicks() + 1);
        int maxTicks = (combatant.getMovementPlan().size() + 1) * Config.maxMoveTicksPerCell + 10;
        if (combatant.getMoveTicks() > maxTicks) {
            failPlayerMove(encounter, combatant, player);
            return;
        }
        double dx = player.getX() - combatant.getLastMoveX();
        double dz = player.getZ() - combatant.getLastMoveZ();
        if (dx * dx + dz * dz > 2.25D) {
            failPlayerMove(encounter, combatant, player);
            return;
        }
        combatant.setLastMove(player.getX(), player.getZ());

        if (!isWithinCorridor(player, combatant)) {
            failPlayerMove(encounter, combatant, player);
            return;
        }

        BlockPos to = combatant.getSegmentTo();
        if (to == null) {
            finishPlayerMove(encounter, combatant, player);
            return;
        }
        double tx = (to.getX() + 0.5D) - player.getX();
        double tz = (to.getZ() + 0.5D) - player.getZ();
        if (tx * tx + tz * tz <= 0.1225D) {
            combatant.advanceSegment();
            combatant.setMoveTicks(0);
            if (!combatant.isMoving()) {
                finishPlayerMove(encounter, combatant, player);
            }
        }
    }

    private boolean isWithinCorridor(ServerPlayer player, Combatant combatant) {
        if (nearCell(player, combatant.getSegmentFrom()) || nearCell(player, combatant.getSegmentTo())) {
            return true;
        }
        for (BlockPos cell : combatant.getMovementPlan()) {
            if (nearCell(player, cell)) {
                return true;
            }
        }
        return false;
    }

    private boolean nearCell(ServerPlayer player, BlockPos cell) {
        if (cell == null) {
            return false;
        }
        double dx = player.getX() - (cell.getX() + 0.5D);
        double dz = player.getZ() - (cell.getZ() + 0.5D);
        return dx * dx + dz * dz <= 0.81D;
    }

    private void finishPlayerMove(CombatEncounter encounter, Combatant combatant, ServerPlayer player) {
        teleportToCell(player, combatant.getCell());
        combatant.setMoveTicks(0);
        recomputeReachable(encounter, combatant, player);
        DndTurnNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MovePlanS2C(List.of()));
        broadcast(encounter);
    }

    private void failPlayerMove(CombatEncounter encounter, Combatant combatant, ServerPlayer player) {
        player.sendSystemMessage(Component.literal("移动被中断").withStyle(ChatFormatting.YELLOW));
        teleportToCell(player, combatant.getCell());
        combatant.stopMoving();
        combatant.setMoveTicks(0);
        recomputeReachable(encounter, combatant, player);
        DndTurnNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MovePlanS2C(List.of()));
        broadcast(encounter);
    }

    private void teleportToCell(LivingEntity entity, BlockPos cell) {        if (cell == null) {
            return;
        }
        teleportTo(new Vec3(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D), entity);
    }

    private void teleportTo(Vec3 pos, LivingEntity entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.teleport(pos.x, pos.y, pos.z, serverPlayer.getYRot(), serverPlayer.getXRot());
        } else {
            entity.setPos(pos.x, pos.y, pos.z);
            entity.hasImpulse = true;
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
    }

    private void snapToCell(LivingEntity entity, BlockPos cell) {
        if (cell == null) {
            return;
        }
        double dx = entity.getX() - (cell.getX() + 0.5D);
        double dy = entity.getY() - cell.getY();
        double dz = entity.getZ() - (cell.getZ() + 0.5D);
        if (dx * dx + dy * dy + dz * dz > 0.5625D) {
            teleportToCell(entity, cell);
        }
    }

    private void beginTurn(CombatEncounter encounter) {
        Combatant combatant = encounter.current();
        if (combatant == null) {
            endEncounter(encounter, "empty");
            return;
        }
        LivingEntity entity = encounter.getEntity(combatant);
        if (entity == null || !entity.isAlive()) {
            advance(encounter);
            return;
        }
        combatant.getEconomy().reset(CombatStats.movementCells(entity));
        combatant.setIdleTicks(0);
        combatant.setDodging(false);
        combatant.setDisengaged(false);
        combatant.setAttackTicks(0);
        combatant.setAttackApplied(false);
        combatant.setAttackTargetId(-1);
        combatant.setAdvanceAfterAttack(false);
        combatant.stopMoving();
        recomputeReachable(encounter, combatant, entity);
        broadcast(encounter);
    }

    private void recomputeReachable(CombatEncounter encounter, Combatant combatant, LivingEntity entity) {
        Set<BlockPos> occupied = occupiedCells(encounter, combatant);
        GridPathfinder.Result result = GridPathfinder.compute(encounter.getLevel(), entity,
                entity.blockPosition(), combatant.getEconomy().getMovementRemaining(),
                encounter.getGrid(), occupied);
        encounter.setCurrentReachable(result);
    }

    private void endTurn(ServerPlayer player) {
        CombatEncounter encounter = encounterOf(player);
        if (encounter == null) {
            return;
        }
        Combatant combatant = encounter.getCombatant(player.getUUID());
        if (combatant == null || !player.getUUID().equals(encounter.currentId())) {
            return;
        }
        if (combatant.isMoving() || combatant.isAttacking()) {
            return;
        }
        advance(encounter);
    }

    private void advance(CombatEncounter encounter) {
        encounter.advanceTurn();
        if (encounter.isOver()) {
            endEncounter(encounter, "resolved");
            return;
        }
        beginTurn(encounter);
    }

    private void moveTo(ServerPlayer player, BlockPos target) {
        CombatEncounter encounter = encounterOf(player);
        if (encounter == null) {
            return;
        }
        Combatant combatant = encounter.getCombatant(player.getUUID());
        if (combatant == null || !player.getUUID().equals(encounter.currentId())) {
            return;
        }
        if (combatant.isMoving() || combatant.isAttacking()) {
            return;
        }
        GridPathfinder.Result reachable = encounter.getCurrentReachable();
        if (reachable == null || !reachable.contains(target)) {
            player.sendSystemMessage(Component.literal("无法移动到该格").withStyle(ChatFormatting.RED));
            return;
        }
        int cost = reachable.cost(target);
        if (cost == Integer.MAX_VALUE || !combatant.getEconomy().spendMovement(cost)) {
            return;
        }
        List<BlockPos> path = reachable.pathTo(target);
        combatant.beginMove(path);
        combatant.setMoveTicks(0);
        combatant.setLastMove(player.getX(), player.getZ());
        encounter.setCurrentReachable(null);
        DndTurnNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MovePlanS2C(path));
        broadcast(encounter);
    }

    private void performAction(ServerPlayer player, CombatAction action) {
        CombatEncounter encounter = encounterOf(player);
        if (encounter == null) {
            return;
        }
        Combatant combatant = encounter.getCombatant(player.getUUID());
        if (combatant == null || !player.getUUID().equals(encounter.currentId())) {
            return;
        }
        if (combatant.isMoving() || combatant.isAttacking()) {
            return;
        }
        LivingEntity entity = encounter.getEntity(combatant);
        if (entity == null) {
            return;
        }
        switch (action) {
            case END_TURN -> advance(encounter);
            case DASH -> {
                if (combatant.getEconomy().hasAction()) {
                    combatant.getEconomy().useAction();
                    combatant.getEconomy().setMovementRemaining(
                            combatant.getEconomy().getMovementRemaining() + CombatStats.movementCells(entity));
                    recomputeReachable(encounter, combatant, entity);
                    sendLog(encounter, player.getScoreboardName() + " 使用疾行");
                    broadcast(encounter);
                }
            }
            case DODGE -> {
                if (combatant.getEconomy().hasAction()) {
                    combatant.getEconomy().useAction();
                    combatant.setDodging(true);
                    sendLog(encounter, player.getScoreboardName() + " 进入闪避");
                    broadcast(encounter);
                }
            }
            case DISENGAGE -> {
                if (combatant.getEconomy().hasAction()) {
                    combatant.getEconomy().useAction();
                    combatant.setDisengaged(true);
                    sendLog(encounter, player.getScoreboardName() + " 脱离战斗");
                    broadcast(encounter);
                }
            }
            default -> player.sendSystemMessage(Component.literal("该动作尚未实现").withStyle(ChatFormatting.GRAY));
        }
    }

    private void handleAttack(ServerPlayer player, int networkId) {
        CombatEncounter encounter = encounterOf(player);
        if (encounter == null) {
            return;
        }
        Combatant combatant = encounter.getCombatant(player.getUUID());
        if (combatant == null || !player.getUUID().equals(encounter.currentId())) {
            return;
        }
        if (combatant.isMoving() || combatant.isAttacking()) {
            return;
        }
        if (!combatant.getEconomy().hasAction()) {
            player.sendSystemMessage(Component.literal("本回合已经没有动作").withStyle(ChatFormatting.GRAY));
            return;
        }
        LivingEntity attacker = encounter.getEntity(combatant);
        Entity targetEntity = encounter.getLevel().getEntity(networkId);
        if (attacker == null || !(targetEntity instanceof LivingEntity target)) {
            return;
        }
        Combatant targetCombatant = encounter.getCombatant(target.getUUID());
        if (targetCombatant == null || !target.isAlive()) {
            return;
        }
        if (targetCombatant.getSide().equals(combatant.getSide())) {
            player.sendSystemMessage(Component.literal("不能攻击友方单位").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (chebyshev(attacker.blockPosition(), target.blockPosition()) > 1) {
            player.sendSystemMessage(Component.literal("目标不在近战范围内").withStyle(ChatFormatting.GRAY));
            return;
        }
        combatant.getEconomy().useAction();
        if (!targetCombatant.isPlayerSide()) {
            targetCombatant.setHostile(true);
            encounter.noteHostile();
            if (target instanceof Mob mob) {
                mob.setTarget(player);
            }
        }
        beginAttack(encounter, combatant, attacker, target, false);
        broadcast(encounter);
    }

    private void endEncounter(CombatEncounter encounter, String reason) {
        encounter.setActive(false);
        for (Combatant combatant : encounter.getCombatants()) {
            entityToEncounter.remove(combatant.getEntityId());
            LivingEntity entity = encounter.getEntity(combatant);
            if (entity != null && entity.isAlive()) {
                teleportToCell(entity, combatant.getCell());
            }
            if (entity instanceof Mob mob) {
                Boolean previous = encounter.getPreviousNoAi().get(combatant.getEntityId());
                mob.setNoAi(previous != null && previous);
                Boolean gravity = encounter.getPreviousNoGravity().get(combatant.getEntityId());
                mob.setNoGravity(gravity != null && gravity);
            }
        }
        encounters.remove(encounter.getId());
        for (Combatant combatant : encounter.getCombatants()) {
            LivingEntity entity = encounter.getEntity(combatant);
            if (entity instanceof ServerPlayer player) {
                DndTurnNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MovePlanS2C(List.of()));
                DndTurnNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CombatEndS2C());
                player.sendSystemMessage(Component.literal("战斗结束").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    public void removeEntity(Entity entity) {
        UUID encounterId = entityToEncounter.remove(entity.getUUID());
        if (encounterId == null) {
            return;
        }
        CombatEncounter encounter = encounters.get(encounterId);
        if (encounter == null) {
            return;
        }
        encounter.getCombatants().removeIf(c -> c.getEntityId().equals(entity.getUUID()));
        encounter.buildTurnOrder();
        if (encounter.isOver()) {
            endEncounter(encounter, "player left");
        } else {
            beginTurn(encounter);
        }
    }

    public void forceEnd(ServerPlayer player) {
        CombatEncounter encounter = encounterOf(player);
        if (encounter != null) {
            endEncounter(encounter, "command");
        }
    }

    public void forceNext(ServerPlayer player) {
        CombatEncounter encounter = encounterOf(player);
        if (encounter != null) {
            advance(encounter);
        }
    }

    private void broadcast(CombatEncounter encounter) {
        for (Combatant combatant : encounter.getCombatants()) {
            LivingEntity entity = encounter.getEntity(combatant);
            if (entity instanceof ServerPlayer player) {
                sendState(encounter, player);
            }
        }
    }

    private void sendState(CombatEncounter encounter, ServerPlayer player) {
        Combatant mine = encounter.getCombatant(player.getUUID());
        boolean myTurn = player.getUUID().equals(encounter.currentId());
        List<BlockPos> reachable = new ArrayList<>();
        if (myTurn && encounter.getCurrentReachable() != null) {
            reachable.addAll(encounter.getCurrentReachable().cells());
        }
        List<CombatStateS2C.Entry> entries = new ArrayList<>();
        for (Combatant combatant : encounter.getCombatants()) {
            LivingEntity entity = encounter.getEntity(combatant);
            String name = entity != null ? entity.getDisplayName().getString() : "?";
            entries.add(new CombatStateS2C.Entry(combatant.getEntityId(),
                    entity != null ? entity.getId() : -1,
                    name,
                    combatant.getInitiative(), combatant.getSide(),
                    combatant.getEconomy().getMovementRemaining(),
                    combatant.getEconomy().hasAction(),
                    combatant.getEntityId().equals(encounter.currentId())));
        }
        Grid grid = encounter.getGrid();
        CombatStateS2C packet = new CombatStateS2C(encounter.getId(), encounter.getRound(),
                encounter.currentId(), entries,
                grid.getMinX(), grid.getMaxX(), grid.getMinZ(), grid.getMaxZ(), grid.getFloorY(),
                reachable, myTurn,
                mine != null ? mine.getEconomy().getMovementRemaining() : 0,
                mine != null && mine.getEconomy().hasAction(),
                mine != null && mine.getEconomy().hasBonusAction());
        DndTurnNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private void sendLog(CombatEncounter encounter, String message) {
        for (Combatant combatant : encounter.getCombatants()) {
            LivingEntity entity = encounter.getEntity(combatant);
            if (entity instanceof ServerPlayer player) {
                DndTurnNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CombatLogS2C(message));
            }
        }
    }
}
