package org.hismeo.fractureclient.client.avatar;

import net.minecraft.client.multiplayer.ClientLevel;

import java.lang.ref.WeakReference;
import java.util.Objects;

/** Proof that the Avatar extension completed the immediately preceding Minecraft frame. */
public final class PlayerAvatarRenderGate {
    private WeakReference<ClientLevel> level = new WeakReference<>(null);
    private long currentFrame;
    private long completedFrame = Long.MIN_VALUE;
    private boolean worldRendering;
    private boolean closed;

    public synchronized void beginFrame() {
        if (!closed) {
            currentFrame = Math.incrementExact(currentFrame);
            worldRendering = false;
        }
    }

    public synchronized void beginWorldRender() {
        if (!closed) {
            worldRendering = true;
        }
    }

    public synchronized void endWorldRender() {
        worldRendering = false;
    }

    public synchronized void heartbeat(ClientLevel currentLevel) {
        if (closed) {
            return;
        }
        level = new WeakReference<>(Objects.requireNonNull(currentLevel, "currentLevel"));
        completedFrame = currentFrame;
    }

    public synchronized boolean permits(ClientLevel currentLevel) {
        return !closed
                && worldRendering
                && level.get() == currentLevel
                && currentFrame > 0L
                && completedFrame == currentFrame - 1L;
    }

    public synchronized void reset() {
        level.clear();
        level = new WeakReference<>(null);
        completedFrame = Long.MIN_VALUE;
        worldRendering = false;
    }

    public synchronized void close() {
        closed = true;
        reset();
    }
}
