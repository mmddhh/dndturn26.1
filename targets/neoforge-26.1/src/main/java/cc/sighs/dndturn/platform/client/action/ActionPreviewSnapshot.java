package cc.sighs.dndturn.platform.client.action;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.platform.network.ActionProtocol;
import java.util.*;
import net.minecraft.world.phys.*;

public record ActionPreviewSnapshot(UUID query, ActionIntent intent, List<ActionProtocol.Candidate> candidates,
                    List<ActionProtocol.PreviewStep> route, List<ActionIntent.Point> trajectory,
                    ActionIntent.Point contact, String status, String reason, double reachableDistance) {}
