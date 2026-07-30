package org.hismeo.haikalathost.internal.extension;

import com.kaleblangley.haikalat.core.device.RenderDevice;
import com.kaleblangley.haikalat.core.presentation.PresentationTarget;
import com.kaleblangley.haikalat.subsystems.render3d.ExternalCamera;
import com.kaleblangley.haikalat.subsystems.resources.ResourceCatalog;
import org.hismeo.haikalathost.api.client.advanced.HaikalatEngineContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatFrameContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatReloadContext;

import java.util.Objects;

/**
 * Validated immutable implementations of the public advanced callback contexts.
 */
public final class HaikalatExtensionContexts {
    private HaikalatExtensionContexts() {
    }

    public static HaikalatEngineContext engine(
            RenderDevice device,
            ResourceCatalog resources,
            long resourceGeneration
    ) {
        return new Engine(device, resources, resourceGeneration);
    }

    public static HaikalatReloadContext reload(
            RenderDevice device,
            ResourceCatalog resources,
            long resourceGeneration
    ) {
        return new Reload(device, resources, resourceGeneration);
    }

    public static HaikalatFrameContext frame(
            RenderDevice device,
            ResourceCatalog resources,
            long resourceGeneration,
            ExternalCamera camera,
            PresentationTarget target,
            float deltaSeconds,
            long frameIndex
    ) {
        return new Frame(
                device,
                resources,
                resourceGeneration,
                camera,
                target,
                deltaSeconds,
                frameIndex);
    }

    private record Engine(
            RenderDevice device,
            ResourceCatalog resources,
            long resourceGeneration
    ) implements HaikalatEngineContext {
        private Engine {
            device = Objects.requireNonNull(device, "device");
            resources = Objects.requireNonNull(resources, "resources");
            requireGeneration(resourceGeneration);
        }
    }

    private record Reload(
            RenderDevice device,
            ResourceCatalog resources,
            long resourceGeneration
    ) implements HaikalatReloadContext {
        private Reload {
            device = Objects.requireNonNull(device, "device");
            resources = Objects.requireNonNull(resources, "resources");
            requireGeneration(resourceGeneration);
        }
    }

    private record Frame(
            RenderDevice device,
            ResourceCatalog resources,
            long resourceGeneration,
            ExternalCamera camera,
            PresentationTarget target,
            float deltaSeconds,
            long frameIndex
    ) implements HaikalatFrameContext {
        private Frame {
            device = Objects.requireNonNull(device, "device");
            resources = Objects.requireNonNull(resources, "resources");
            requireGeneration(resourceGeneration);
            camera = Objects.requireNonNull(camera, "camera");
            target = Objects.requireNonNull(target, "target");
            if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0.0F) {
                throw new IllegalArgumentException(
                        "deltaSeconds must be finite and non-negative");
            }
            if (frameIndex < 0L) {
                throw new IllegalArgumentException("frameIndex must be non-negative");
            }
        }
    }

    private static void requireGeneration(long resourceGeneration) {
        if (resourceGeneration < 0L) {
            throw new IllegalArgumentException("resourceGeneration must be non-negative");
        }
    }
}
