package cc.sighs.dndturn.platform.spatial;

import java.util.Map;

/** Fixed 26.1 launch/flight values shared by the client preview and server adapter. */
public final class ProjectileProfiles {
    public record Profile(double speed,double gravity,float pitchOffset,boolean throwable) {}
    private static final Map<String,Profile> VALUES=Map.ofEntries(
        Map.entry("dndturn:bow",new Profile(3,.05,0,false)),
        Map.entry("dndturn:crossbow",new Profile(3.15,.05,0,false)),
        Map.entry("dndturn:fishing_rod",new Profile(1.1,.03,0,false)),
        Map.entry("dndturn:snowball",new Profile(1.5,.03,0,true)),
        Map.entry("dndturn:egg",new Profile(1.5,.03,0,true)),
        Map.entry("dndturn:experience_bottle",new Profile(.7,.07,-20,true)),
        Map.entry("dndturn:splash_potion",new Profile(.5,.05,-20,true)));
    private ProjectileProfiles() {}
    public static Profile get(String behavior) { return behavior==null?null:VALUES.get(behavior); }
    public static boolean contains(String behavior) { return get(behavior)!=null; }
}
