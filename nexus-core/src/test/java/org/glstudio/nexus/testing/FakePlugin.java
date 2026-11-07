package org.glstudio.nexus.testing;

import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A {@link Plugin} stand-in for code that only needs a data folder, bundled resources and a logger.
 */
public final class FakePlugin {

    private FakePlugin() {
    }

    /**
     * Creates a plugin whose data folder is {@code dataFolder} and whose jar holds {@code resources}.
     *
     * @param dataFolder the data folder
     * @param resources  resource path to file contents
     * @return the proxy
     */
    public static Plugin create(File dataFolder, Map<String, String> resources) {
        Logger logger = Logger.getLogger("FakePlugin");
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDataFolder" -> dataFolder;
                    case "getLogger" -> logger;
                    case "getName" -> "FakePlugin";
                    case "getResource" -> resource(resources, (String) args[0]);
                    case "saveResource" -> {
                        save(dataFolder, resources, (String) args[0]);
                        yield null;
                    }
                    case "isEnabled" -> true;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "FakePlugin";
                    default -> null;
                });
    }

    private static InputStream resource(Map<String, String> resources, String path) {
        String content = resources.get(path);
        return content == null ? null : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static void save(File dataFolder, Map<String, String> resources, String path) {
        try {
            File target = new File(dataFolder, path);
            target.getParentFile().mkdirs();
            Files.writeString(target.toPath(), resources.get(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
