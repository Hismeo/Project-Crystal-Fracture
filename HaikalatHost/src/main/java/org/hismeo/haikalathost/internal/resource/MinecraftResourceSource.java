package org.hismeo.haikalathost.internal.resource;

import com.kaleblangley.haikalat.subsystems.resources.AssetId;
import com.kaleblangley.haikalat.subsystems.resources.ResourceSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reads one Haikalat namespace through Minecraft's active resource manager.
 *
 * <p>This preserves normal resource-pack override semantics and deliberately obtains the resource
 * manager for each read so a reload cannot leave the source pointing at an obsolete manager.</p>
 */
public final class MinecraftResourceSource implements ResourceSource {
    private final String namespace;
    private final Supplier<ResourceManager> resourceManager;

    public MinecraftResourceSource(
            String namespace,
            Supplier<ResourceManager> resourceManager
    ) {
        this.namespace = AssetId.of(namespace, "probe").namespace();
        this.resourceManager = Objects.requireNonNull(resourceManager, "resourceManager");
    }

    @Override
    public byte[] read(AssetId assetId, long maxBytes) throws IOException {
        Objects.requireNonNull(assetId, "assetId");
        validateMaxBytes(maxBytes);
        if (!namespace.equals(assetId.namespace())) {
            throw new FileNotFoundException("Resource namespace is not served: " + assetId);
        }

        ResourceLocation location =
                ResourceLocation.fromNamespaceAndPath(assetId.namespace(), assetId.path());
        Resource resource = Objects.requireNonNull(
                        resourceManager.get(),
                        "resourceManager supplier returned null")
                .getResource(location)
                .orElseThrow(() -> new FileNotFoundException("Resource not found: " + assetId));

        int boundedReadLength = Math.toIntExact(maxBytes + 1L);
        try (InputStream stream = resource.open()) {
            byte[] bytes = stream.readNBytes(boundedReadLength);
            if (bytes.length > maxBytes) {
                throw new IOException(
                        "Resource exceeds byte limit " + maxBytes + ": " + assetId
                                + " (pack=" + resource.sourcePackId() + ')');
            }
            return bytes;
        }
    }

    public String namespace() {
        return namespace;
    }

    private static void validateMaxBytes(long maxBytes) {
        if (maxBytes < 0L || maxBytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "maxBytes must be in 0.." + (Integer.MAX_VALUE - 1L));
        }
    }
}
