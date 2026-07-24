package org.hismeo.haikalathost.client.extraction;

import org.hismeo.haikalathost.client.scene.RenderSceneDelta;

import java.util.Objects;

public record WorldRenderSnapshot(
        long gameTick,
        long capturedNanos,
        int resourceGeneration,
        CameraSnapshot camera,
        RenderSceneDelta sceneDelta
) {
    public WorldRenderSnapshot {
        if (gameTick < 0L) throw new IllegalArgumentException("game tick must not be negative");
        if (capturedNanos < 0L) throw new IllegalArgumentException("capture time must not be negative");
        if (resourceGeneration <= 0) throw new IllegalArgumentException("resource generation must be positive");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(sceneDelta, "sceneDelta");
    }
}
