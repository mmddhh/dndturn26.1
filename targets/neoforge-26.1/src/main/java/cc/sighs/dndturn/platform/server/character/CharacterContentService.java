package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.RulesCatalog;
import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

/** Current creator content. Reload creates a new immutable snapshot; encounters must pin it when they start. */
public final class CharacterContentService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static RulesCatalog current = RulesCatalog.empty();
    private static String lastFailure = "";
    private static Path root;

    private CharacterContentService() {}

    public static synchronized void load(MinecraftServer server) {
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
    public static synchronized RulesCatalog reload() {
        if (root == null) throw new IllegalStateException("character content is not loaded");
        RulesCatalog next = CharacterContentLoader.load(root);
        current = next;
        lastFailure = "";
        return next;
    }

    public static synchronized RulesCatalog current() { return current; }
    public static synchronized String lastFailure() { return lastFailure; }
    public static synchronized Path root() { return root; }

    public static synchronized void clear() {
        current = RulesCatalog.empty();
        lastFailure = "";
        root = null;
    }
}
