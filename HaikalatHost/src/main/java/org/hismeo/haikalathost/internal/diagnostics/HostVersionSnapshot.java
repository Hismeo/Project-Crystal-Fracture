package org.hismeo.haikalathost.internal.diagnostics;

import net.neoforged.fml.ModList;
import org.hismeo.haikalathost.HaikalatHost;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * Immutable version matrix for support reports.
 */
public record HostVersionSnapshot(
        String host,
        String haikalat,
        String minecraft,
        String neoForge
) {
    private static final String UNAVAILABLE = "unavailable";

    public HostVersionSnapshot {
        host = Objects.requireNonNull(host, "host");
        haikalat = Objects.requireNonNull(haikalat, "haikalat");
        minecraft = Objects.requireNonNull(minecraft, "minecraft");
        neoForge = Objects.requireNonNull(neoForge, "neoForge");
    }

    public static HostVersionSnapshot capture() {
        return new HostVersionSnapshot(
                modVersion(HaikalatHost.MOD_ID),
                haikalatVersion(),
                modVersion("minecraft"),
                modVersion("neoforge"));
    }

    private static String modVersion(String modId) {
        try {
            return ModList.get()
                    .getModContainerById(modId)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse(UNAVAILABLE);
        } catch (RuntimeException failure) {
            return UNAVAILABLE;
        }
    }

    private static String haikalatVersion() {
        Properties properties = new Properties();
        try (InputStream stream = HostVersionSnapshot.class.getResourceAsStream(
                "/haikalat-build.properties")) {
            if (stream == null) {
                return UNAVAILABLE;
            }
            properties.load(stream);
            return properties.getProperty("engineVersion", UNAVAILABLE);
        } catch (IOException | RuntimeException failure) {
            return UNAVAILABLE;
        }
    }
}
