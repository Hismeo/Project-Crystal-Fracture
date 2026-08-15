package org.hismeo.fractureclient.client.avatar.skin;

import com.kaleblangley.haikalat.backend.texture.Texture2D;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reference-counted lease over one cached Haikalat skin texture. */
public final class PlayerSkinHandle implements AutoCloseable {
    private final PlayerSkinCache owner;
    private final PlayerSkinCache.CacheKey key;
    private final Texture2D texture;
    private final PlayerSkinSource source;
    private final boolean fallback;
    private final AtomicBoolean closed = new AtomicBoolean();

    PlayerSkinHandle(
            PlayerSkinCache owner,
            PlayerSkinCache.CacheKey key,
            Texture2D texture,
            PlayerSkinSource source,
            boolean fallback
    ) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.key = Objects.requireNonNull(key, "key");
        this.texture = Objects.requireNonNull(texture, "texture");
        this.source = Objects.requireNonNull(source, "source");
        this.fallback = fallback;
    }

    public Texture2D texture() {
        if (closed.get()) {
            throw new IllegalStateException("skin handle is closed");
        }
        return texture;
    }

    public PlayerSkinSource source() {
        return source;
    }

    public boolean fallback() {
        return fallback;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            owner.release(key);
        }
    }
}
