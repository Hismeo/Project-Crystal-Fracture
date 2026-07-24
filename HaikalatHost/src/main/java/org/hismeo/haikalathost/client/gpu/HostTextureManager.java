package org.hismeo.haikalathost.client.gpu;

import com.kaleblangley.haikalat.backend.texture.Texture2D;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.hismeo.haikalathost.HaikalatHost;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import static org.lwjgl.opengl.GL12.GL_REPEAT;
import static org.lwjgl.opengl.GL21.GL_SRGB8_ALPHA8;

/**
 * Generation-scoped Host texture ownership. Minecraft contributes encoded resource bytes only;
 * its GL texture names are never observed or reused.
 */
public final class HostTextureManager implements AutoCloseable {
    private final Map<ResourceLocation, Texture2D> textures = new HashMap<>();
    private final Map<ResourceLocation, CompletableFuture<DecodedTexture>> pending =
            new HashMap<>();
    private final Set<ResourceLocation> unavailable = new HashSet<>();
    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "HaikalatHost-TextureDecode");
        thread.setDaemon(true);
        return thread;
    });
    private Texture2D placeholder;
    private boolean closed;

    public Texture2D resolve(ResourceLocation location) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        Objects.requireNonNull(location, "location");
        Texture2D existing = textures.get(location);
        if (existing != null) return existing;
        if (unavailable.contains(location)) return placeholder();

        CompletableFuture<DecodedTexture> pendingDecode = pending.get(location);
        if (pendingDecode == null) {
            Optional<Resource> resource = Minecraft.getInstance()
                    .getResourceManager()
                    .getResource(location);
            if (resource.isEmpty()) {
                unavailable.add(location);
                HaikalatHost.LOGGER.warn(
                        "Host texture {} is runtime-only; using visible placeholder", location);
                return placeholder();
            }
            pendingDecode = CompletableFuture.supplyAsync(
                    () -> decode(location, resource.orElseThrow()), decodeExecutor);
            pending.put(location, pendingDecode);
        }
        if (!pendingDecode.isDone()) return placeholder();

        pending.remove(location);
        try {
            DecodedTexture decoded = pendingDecode.join();
            Texture2D created = createTexture(
                    decoded.width(), decoded.height(), decoded.pixels());
            textures.put(location, created);
            return created;
        } catch (CompletionException failure) {
            unavailable.add(location);
            HaikalatHost.LOGGER.warn(
                    "Failed to decode Host texture {}; using visible placeholder",
                    location,
                    failure.getCause());
            return placeholder();
        }
    }

    public int size() {
        return textures.size();
    }

    public void reload() {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        clearGeneration();
    }

    @Override
    public void close() {
        if (closed) return;
        RenderSystem.assertOnRenderThread();
        clearGeneration();
        decodeExecutor.shutdownNow();
        closed = true;
    }

    private static DecodedTexture decode(ResourceLocation location, Resource resource) {
        try (InputStream input = resource.open()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IOException("Unsupported or corrupt image payload");
            }
            return new DecodedTexture(
                    image.getWidth(), image.getHeight(), HostTexturePixels.toBottomUpRgba(image));
        } catch (IOException failure) {
            throw new CompletionException(
                    new IOException("Failed to decode Host texture " + location, failure));
        }
    }

    private static Texture2D createTexture(int width, int height, ByteBuffer pixels) {
        Texture2D texture = Texture2D.createEmpty(width, height, GL_SRGB8_ALPHA8);
        try {
            texture.uploadRegion(0, 0, width, height, pixels);
            texture.setWrap(GL_REPEAT, GL_REPEAT);
            return texture;
        } catch (Throwable failure) {
            texture.close();
            throw failure;
        }
    }

    private static ByteBuffer missingPixels() {
        ByteBuffer pixels = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        int[] colors = {0xFFFF00FF, 0xFF000000, 0xFF000000, 0xFFFF00FF};
        for (int argb : colors) {
            pixels.put((byte) (argb >>> 16));
            pixels.put((byte) (argb >>> 8));
            pixels.put((byte) argb);
            pixels.put((byte) (argb >>> 24));
        }
        return pixels.flip();
    }

    private Texture2D placeholder() {
        if (placeholder == null) placeholder = createTexture(2, 2, missingPixels());
        return placeholder;
    }

    private void clearGeneration() {
        pending.values().forEach(future -> future.cancel(true));
        pending.clear();
        unavailable.clear();
        textures.values().forEach(Texture2D::close);
        textures.clear();
        if (placeholder != null) {
            placeholder.close();
            placeholder = null;
        }
    }

    private record DecodedTexture(int width, int height, ByteBuffer pixels) { }


    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Host texture manager is closed");
    }
}
