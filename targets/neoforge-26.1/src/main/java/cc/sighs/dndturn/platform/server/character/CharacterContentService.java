package cc.sighs.dndturn.platform.server.character;

import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import cc.sighs.dndturn.domain.character.RulesCatalog;
import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

/** Current creator content. Reload creates a new immutable snapshot; encounters must pin it when they start. */
public final class CharacterContentService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private RulesCatalog current = RulesCatalog.empty();
    private String lastFailure = "";
    private final Path root;
    private final Set<UUID> offered = new HashSet<>();
    boolean markOffered(UUID player) { return offered.add(player); }
    public void forget(UUID player) { offered.remove(player); }

    public CharacterContentService(MinecraftServer server) {
        root = server.getWorldPath(LevelResource.ROOT).resolve("dndturn-content");
        try {
            current = CharacterContentLoader.load(root);
            lastFailure = "";
            LOGGER.info("DNDTurn character content loaded: {} classes, {} races, {} mobs (revision {})",
                current.classes().size(), current.races().size(), current.mobs().size(), current.revision());
        } catch (RuntimeException failure) {
            lastFailure = failure.getMessage();
            current = RulesCatalog.empty();
            LOGGER.error("DNDTurn character content rejected; character creation disabled until fixed: {}", lastFailure, failure);
        }
    }

    /** Admin reload. A failed revision leaves the previous content active and reports the reason. */
    public RulesCatalog reload() {
        RulesCatalog next = CharacterContentLoader.load(root);
        current = next;
        lastFailure = "";
        return next;
    }

    public RulesCatalog current() { return current; }
    public String lastFailure() { return lastFailure; }
}
