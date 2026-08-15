package org.hismeo.fractureclient.client.avatar.skin;

import com.kaleblangley.haikalat.backend.texture.Sampler;
import com.kaleblangley.haikalat.backend.texture.Texture2D;
import com.kaleblangley.haikalat.backend.texture.TextureColorSpace;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Uploads each Minecraft skin once as an sRGB, alpha-preserving texture. */
public final class PlayerSkinCache implements AutoCloseable {
    private final Map<CacheKey, Entry> entries = new HashMap<>();
    private Sampler sampler;
    private boolean closed;

    public synchronized PlayerSkinHandle acquire(
            PlayerSkinSource requested,
            PlayerSkinSource.SkinLayout targetLayout
    ) {
        ensureOpen();
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(targetLayout, "targetLayout");
        if (!requested.downloaded()) {
            return acquireFallback(requested, targetLayout);
        }
        CacheKey key = new CacheKey(
                requested.texture(),
                requested.revision(),
                TextureColorSpace.SRGB,
                Sampling.NEAREST,
                targetLayout);
        Entry existing = entries.get(key);
        if (existing != null) {
            existing.references++;
            return new PlayerSkinHandle(this, key, existing.texture, requested, false);
        }
        try {
            Texture2D texture = uploadMinecraftTexture(requested, targetLayout);
            entries.put(key, new Entry(texture));
            return new PlayerSkinHandle(this, key, texture, requested, false);
        } catch (RuntimeException failure) {
            return acquireFallback(requested, targetLayout);
        }
    }

    public synchronized Sampler sampler() {
        ensureOpen();
        if (sampler == null) {
            sampler = Sampler.create(new Sampler.Descriptor(
                    GL11.GL_NEAREST,
                    GL11.GL_NEAREST,
                    GL12.GL_CLAMP_TO_EDGE,
                    GL12.GL_CLAMP_TO_EDGE));
        }
        return sampler;
    }

    synchronized void release(CacheKey key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return;
        }
        if (--entry.references == 0) {
            entries.remove(key);
            entry.texture.close();
        }
    }

    public synchronized int size() {
        return entries.size();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        entries.values().forEach(entry -> entry.texture.close());
        entries.clear();
        if (sampler != null) {
            sampler.close();
            sampler = null;
        }
    }

    private PlayerSkinHandle acquireFallback(
            PlayerSkinSource requested,
            PlayerSkinSource.SkinLayout targetLayout
    ) {
        PlayerSkin defaultSkin = DefaultPlayerSkin.get(requested.playerId());
        PlayerSkinSource fallback = new PlayerSkinSource(
                requested.playerId(),
                defaultSkin.texture(),
                defaultSkin.model() == PlayerSkin.Model.SLIM
                        ? PlayerSkinSource.SkinLayout.SLIM
                        : PlayerSkinSource.SkinLayout.CLASSIC,
                Objects.hash(defaultSkin.texture(), defaultSkin.model()),
                null,
                true);
        CacheKey key = new CacheKey(
                fallback.texture(),
                fallback.revision(),
                TextureColorSpace.SRGB,
                Sampling.NEAREST,
                targetLayout);
        Entry entry = entries.get(key);
        if (entry == null) {
            entry = new Entry(uploadMinecraftTexture(fallback, targetLayout));
            entries.put(key, entry);
        } else {
            entry.references++;
        }
        return new PlayerSkinHandle(this, key, entry.texture, requested, true);
    }

    private static Texture2D uploadMinecraftTexture(
            PlayerSkinSource source,
            PlayerSkinSource.SkinLayout targetLayout
    ) {
        try (NativeImage image = readSkinPixels(Minecraft.getInstance(), source)) {
            int[] pixels = PlayerSkinLayoutMapper.remapArms(
                    image.getPixelsRGBA(),
                    source.layout() == PlayerSkinSource.SkinLayout.CLASSIC,
                    targetLayout == PlayerSkinSource.SkinLayout.CLASSIC);
            byte[] rgba = new byte[Math.multiplyExact(pixels.length, 4)];
            for (int index = 0; index < pixels.length; index++) {
                int abgr = pixels[index];
                int offset = index * 4;
                rgba[offset] = (byte) (abgr & 0xFF);
                rgba[offset + 1] = (byte) ((abgr >>> 8) & 0xFF);
                rgba[offset + 2] = (byte) ((abgr >>> 16) & 0xFF);
                rgba[offset + 3] = (byte) ((abgr >>> 24) & 0xFF);
            }
            return Texture2D.fromRgba8(
                    image.getWidth(),
                    image.getHeight(),
                    rgba,
                    TextureColorSpace.SRGB);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read Minecraft skin " + source.texture(), failure);
        }
    }

    private static NativeImage readSkinPixels(
            Minecraft minecraft,
            PlayerSkinSource source
    ) throws IOException {
        NativeImage image;
        if (source.textureUrl() == null) {
            try (InputStream stream = minecraft.getResourceManager()
                    .getResource(source.texture())
                    .orElseThrow(() -> new IOException("Missing skin resource " + source.texture()))
                    .open()) {
                image = NativeImage.read(stream);
            }
        } else {
            Path cachedSkin = downloadedSkinPath(minecraft, source.textureUrl());
            try (InputStream stream = Files.newInputStream(cachedSkin)) {
                image = NativeImage.read(stream);
            }
        }
        return normalizeMinecraftSkin(image, source.texture());
    }

    private static NativeImage normalizeMinecraftSkin(
            NativeImage source,
            ResourceLocation texture
    ) throws IOException {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width != 64 || (height != 32 && height != 64)) {
            source.close();
            throw new IOException(
                    "Unsupported Minecraft skin size " + width + "x" + height + " for " + texture);
        }

        boolean legacy = height == 32;
        NativeImage image = source;
        if (legacy) {
            image = new NativeImage(64, 64, true);
            image.copyFrom(source);
            source.close();
            image.fillRect(0, 32, 64, 32, 0);
            image.copyRect(4, 16, 16, 32, 4, 4, true, false);
            image.copyRect(8, 16, 16, 32, 4, 4, true, false);
            image.copyRect(0, 20, 24, 32, 4, 12, true, false);
            image.copyRect(4, 20, 16, 32, 4, 12, true, false);
            image.copyRect(8, 20, 8, 32, 4, 12, true, false);
            image.copyRect(12, 20, 16, 32, 4, 12, true, false);
            image.copyRect(44, 16, -8, 32, 4, 4, true, false);
            image.copyRect(48, 16, -8, 32, 4, 4, true, false);
            image.copyRect(40, 20, 0, 32, 4, 12, true, false);
            image.copyRect(44, 20, -8, 32, 4, 12, true, false);
            image.copyRect(48, 20, -16, 32, 4, 12, true, false);
            image.copyRect(52, 20, -8, 32, 4, 12, true, false);
        }

        setOpaque(image, 0, 0, 32, 16);
        if (legacy) {
            clearOpaqueLegacyOverlay(image, 32, 0, 64, 32);
        }
        setOpaque(image, 0, 16, 64, 32);
        setOpaque(image, 16, 48, 48, 64);
        return image;
    }

    private static void clearOpaqueLegacyOverlay(
            NativeImage image,
            int minX,
            int minY,
            int maxX,
            int maxY
    ) {
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                if (((image.getPixelRGBA(x, y) >>> 24) & 0xFF) < 128) {
                    return;
                }
            }
        }
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                image.setPixelRGBA(x, y, image.getPixelRGBA(x, y) & 0x00FF_FFFF);
            }
        }
    }

    private static void setOpaque(
            NativeImage image,
            int minX,
            int minY,
            int maxX,
            int maxY
    ) {
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                image.setPixelRGBA(x, y, image.getPixelRGBA(x, y) | 0xFF00_0000);
            }
        }
    }

    static Path downloadedSkinPath(Minecraft minecraft, String url) {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(url, "url");
        Path skinCacheRoot = ((SkinManagerCacheRoot) minecraft.getSkinManager())
                .fractureClient$skinCacheRoot();
        return resolveDownloadedSkinPath(skinCacheRoot, url);
    }

    static Path resolveDownloadedSkinPath(Path skinCacheRoot, String url) {
        Objects.requireNonNull(skinCacheRoot, "skinCacheRoot");
        Objects.requireNonNull(url, "url");
        int separator = url.lastIndexOf('/');
        String profileHash = separator < 0 ? url : url.substring(separator + 1);
        String cacheHash = sha1Utf16Le(profileHash);
        return skinCacheRoot
                .resolve(cacheHash.substring(0, 2))
                .resolve(cacheHash);
    }

    private static String sha1Utf16Le(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(StandardCharsets.UTF_16LE));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte element : digest) {
                result.append(Character.forDigit((element >>> 4) & 0xF, 16));
                result.append(Character.forDigit(element & 0xF, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM does not provide SHA-1", impossible);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("skin cache is closed");
        }
    }

    public record CacheKey(
            ResourceLocation texture,
            long revision,
            TextureColorSpace colorSpace,
            Sampling sampling,
            PlayerSkinSource.SkinLayout targetLayout
    ) {
        public CacheKey {
            texture = Objects.requireNonNull(texture, "texture");
            colorSpace = Objects.requireNonNull(colorSpace, "colorSpace");
            sampling = Objects.requireNonNull(sampling, "sampling");
            targetLayout = Objects.requireNonNull(targetLayout, "targetLayout");
        }
    }

    public enum Sampling {
        NEAREST
    }

    private static final class Entry {
        private final Texture2D texture;
        private int references = 1;

        private Entry(Texture2D texture) {
            this.texture = texture;
        }
    }
}
