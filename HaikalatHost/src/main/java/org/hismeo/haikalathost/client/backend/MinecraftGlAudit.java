package org.hismeo.haikalathost.client.backend;

import java.util.Objects;

/** Render-thread ownership guard used by GL-entry Mixins in strict takeover mode. */
public final class MinecraftGlAudit {
    private final ValidationMode validation;
    private long renderThreadId = -1L;
    private int frameDepth;
    private int externalAllowanceDepth;

    public MinecraftGlAudit(ValidationMode validation) {
        this.validation = Objects.requireNonNull(validation, "validation");
    }

    public Scope beginHaikalatFrame() {
        bindOrAssertRenderThread();
        if (frameDepth != 0) throw new IllegalStateException("A Haikalat frame is already active");
        frameDepth = 1;
        return new Scope(this, false);
    }

    public Scope allowExternalGl(String reason) {
        bindOrAssertRenderThread();
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("allowance reason is required");
        externalAllowanceDepth++;
        return new Scope(this, true);
    }

    public void checkMinecraftGl(String operation) {
        bindOrAssertRenderThread();
        if (frameDepth == 0 || externalAllowanceDepth > 0) return;
        String message = "Minecraft GL call during exclusive Haikalat frame: " + operation;
        if (validation == ValidationMode.STRICT) throw new IllegalStateException(message);
    }

    public boolean frameActive() {
        return frameDepth != 0;
    }

    private void close(boolean allowance) {
        bindOrAssertRenderThread();
        if (allowance) {
            if (externalAllowanceDepth == 0) throw new IllegalStateException("External GL allowance underflow");
            externalAllowanceDepth--;
        } else {
            if (frameDepth == 0) throw new IllegalStateException("No Haikalat frame is active");
            if (externalAllowanceDepth != 0) {
                throw new IllegalStateException("External GL allowance leaked past frame end");
            }
            frameDepth = 0;
        }
    }

    private void bindOrAssertRenderThread() {
        long current = Thread.currentThread().threadId();
        if (renderThreadId == -1L) renderThreadId = current;
        if (renderThreadId != current) {
            throw new IllegalStateException("GL audit accessed from a non-render thread");
        }
    }

    public static final class Scope implements AutoCloseable {
        private MinecraftGlAudit owner;
        private final boolean allowance;

        private Scope(MinecraftGlAudit owner, boolean allowance) {
            this.owner = owner;
            this.allowance = allowance;
        }

        @Override
        public void close() {
            MinecraftGlAudit current = owner;
            if (current == null) return;
            owner = null;
            current.close(allowance);
        }
    }
}
