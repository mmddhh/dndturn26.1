package cc.sighs.dndturn.domain.actor;

import java.util.*;

/** Detached current body data. Neither a checkpoint nor permission to write native state. */
public record NativeActorObservation(UUID actor, UUID instance, UUID capture, String type, String name,
        Transform transform, Vitals vitals, Body body, Controller controller,
        List<Item> equipment, List<Item> inventory, Coverage inventoryCoverage, int selectedSlot,
        Map<String, NativeAttributeObservation> attributes, Coverage attributeCoverage) {
    public enum Coverage { COMPLETE, LIMITED, UNSUPPORTED }
    public enum Controller { PLAYER_CONNECTION, MOB_AI, NATIVE_OTHER }
    public record Transform(String dimension, double x, double y, double z, double yaw, double pitch) {
        public Transform { Objects.requireNonNull(dimension); finite(x, y, z, yaw, pitch); }
    }
    public record Vitals(double health, double maximum, double absorption, boolean alive) {
        public Vitals { finite(health, maximum, absorption); if (maximum < 0 || absorption < 0) throw new IllegalArgumentException("vitals"); }
    }
    public record Body(double width, double height, double eyeHeight, String pose, boolean onGround,
                       boolean inWater, boolean onFire) {
        public Body { finite(width, height, eyeHeight); Objects.requireNonNull(pose);
            if (width < 0 || height < 0) throw new IllegalArgumentException("body dimensions"); }
    }
    /** Empty slots are explicit. Evidence identifies this observation, not a transferable item permit. */
    public record Item(String address, String id, String name, int count, int damage, int maximumDamage, String evidence) {
        public Item {
            Objects.requireNonNull(address); Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(evidence);
            if (address.length() > 64 || id.length() > 256 || name.length() > 128 || evidence.length() > 256
                    || count < 0 || damage < 0 || maximumDamage < 0) throw new IllegalArgumentException("item observation");
        }
    }
    public NativeActorObservation {
        Objects.requireNonNull(actor); Objects.requireNonNull(instance); Objects.requireNonNull(capture);
        Objects.requireNonNull(type); Objects.requireNonNull(name); Objects.requireNonNull(transform);
        Objects.requireNonNull(vitals); Objects.requireNonNull(body); Objects.requireNonNull(controller);
        Objects.requireNonNull(inventoryCoverage); Objects.requireNonNull(attributeCoverage);
        equipment = List.copyOf(equipment); inventory = List.copyOf(inventory); attributes = Map.copyOf(attributes);
        if (equipment.size() > 16 || inventory.size() > 64 || attributes.size() > 128 || selectedSlot < -1 || selectedSlot > 63
                || type.length() > 256 || name.length() > 256
                || equipment.stream().map(Item::address).distinct().count() != equipment.size()
                || inventory.stream().map(Item::address).distinct().count() != inventory.size())
            throw new IllegalArgumentException("native actor observation bounds");
    }
    private static void finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) throw new IllegalArgumentException("non-finite observation");
    }
}
