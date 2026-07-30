package org.hismeo.haikalathost.internal.render;

import com.kaleblangley.haikalat.core.AntiAliasingMode;
import com.kaleblangley.haikalat.core.device.GlRenderDevice;
import com.kaleblangley.haikalat.core.presentation.PresentationResult;
import com.kaleblangley.haikalat.runtime.HaikalatRuntime;
import com.kaleblangley.haikalat.runtime.RenderSettings;
import com.kaleblangley.haikalat.runtime.ToneMappingMode;
import com.kaleblangley.haikalat.subsystems.render3d.Camera;
import com.kaleblangley.haikalat.subsystems.render3d.ExternalCamera;
import com.kaleblangley.haikalat.subsystems.render3d.RenderPipeline;
import com.kaleblangley.haikalat.subsystems.render3d.Scene;
import org.hismeo.haikalathost.HaikalatHost;
import org.hismeo.haikalathost.internal.interop.GlStateFootprint;
import org.hismeo.haikalathost.internal.interop.MinecraftGlInteropScope;

import java.util.Objects;

/**
 * Owns Haikalat's windowless runtime and the first host-directed pipeline.
 *
 * <p>The current pipeline is an opt-in pass-through contract probe. It imports Minecraft color
 * and compatible depth, executes the complete external-target path, and writes back to the same
 * non-zero framebuffer without adding scene geometry. Public advanced extensions reuse this owner
 * rather than creating another context, device, or render thread.</p>
 */
public final class MinecraftEmbeddedRenderBridge implements AutoCloseable {
    public static final String PROBE_PROPERTY = "haikalathost.embeddedPipelineProbe";

    private final GlRenderDevice renderDevice;
    private final HaikalatRuntime runtime;

    private RenderPipeline pipeline;
    private State state = State.READY;
    private PresentationResult lastResult;
    private long renderedFrames;
    private String failureMessage;

    public MinecraftEmbeddedRenderBridge(GlRenderDevice renderDevice) {
        this.renderDevice = Objects.requireNonNull(renderDevice, "renderDevice");
        runtime = HaikalatRuntime.createEmbedded(renderDevice);
    }

    public static boolean probeEnabled() {
        return Boolean.getBoolean(PROBE_PROPERTY);
    }

    /**
     * Runs one Host-scheduled callback through the shared embedded runtime.
     *
     * <p>GL-state isolation remains the caller's responsibility so every third-party extension can
     * receive an independent boundary.</p>
     */
    public void executeCallback(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        if (state == State.CLOSED) {
            throw new IllegalStateException("embedded Haikalat runtime is closed");
        }
        runtime.execute(callback);
    }

    /**
     * Executes the 0.20.1 embedded pipeline when the explicit probe property is enabled.
     */
    public void renderProbe(
            ExternalCamera camera,
            MinecraftPresentationTargetAdapter.TargetSnapshot target,
            float deltaSeconds
    ) {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(target, "target");
        if (!probeEnabled() || state == State.FAILED || state == State.CLOSED) {
            return;
        }
        if (!target.renderable()) {
            lastResult = PresentationResult.SKIPPED_ZERO_EXTENT;
            return;
        }

        try (MinecraftGlInteropScope ignored =
                     MinecraftGlInteropScope.capture(GlStateFootprint.STANDARD_PIPELINE)) {
            renderDevice.invalidateState();
            ensureProbePipeline(target);
            lastResult = runtime.execute(() -> pipeline.render(
                    renderDevice,
                    camera,
                    target.target(),
                    sanitizeDelta(deltaSeconds)));
            if (lastResult == PresentationResult.RENDERED) {
                renderedFrames = Math.incrementExact(renderedFrames);
                state = State.ACTIVE;
                if (renderedFrames == 1L) {
                    HaikalatHost.LOGGER.info(
                            "Haikalat 0.20.1 embedded pipeline rendered its first frame "
                                    + "to Minecraft target generation {} (framebuffer={})",
                            target.target().generation(),
                            target.target().drawFramebufferId());
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            state = State.FAILED;
            failureMessage = messageOf(failure);
            HaikalatHost.LOGGER.error(
                    "Haikalat embedded presentation probe failed and has been disabled",
                    failure);
        } finally {
            renderDevice.invalidateState();
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(
                probeEnabled(),
                state,
                pipeline != null,
                lastResult,
                renderedFrames,
                runtime.renderThreadId(),
                failureMessage);
    }

    @Override
    public void close() {
        if (state == State.CLOSED) {
            return;
        }
        RuntimeException primary = null;
        RenderPipeline currentPipeline = pipeline;
        pipeline = null;
        if (currentPipeline != null) {
            try (MinecraftGlInteropScope ignored =
                         MinecraftGlInteropScope.capture(GlStateFootprint.STANDARD_PIPELINE)) {
                renderDevice.invalidateState();
                runtime.execute(currentPipeline::close);
            } catch (RuntimeException failure) {
                primary = failure;
            } finally {
                renderDevice.invalidateState();
            }
        }
        try {
            runtime.close();
        } catch (RuntimeException failure) {
            if (primary == null) {
                primary = failure;
            } else {
                primary.addSuppressed(failure);
            }
        }
        state = State.CLOSED;
        if (primary != null) {
            throw primary;
        }
    }

    private void ensureProbePipeline(
            MinecraftPresentationTargetAdapter.TargetSnapshot target
    ) {
        if (pipeline != null) {
            return;
        }
        RenderSettings settings = RenderSettings.builder()
                .vsync(false)
                .antiAliasingMode(AntiAliasingMode.NONE)
                .toneMappingMode(ToneMappingMode.NONE)
                .build();
        RenderPipeline candidate = new RenderPipeline(
                target.target(),
                new Scene(new Camera()),
                null,
                settings);
        try {
            runtime.execute(candidate::build);
            pipeline = candidate;
        } catch (RuntimeException | Error failure) {
            try {
                candidate.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static float sanitizeDelta(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0.0F) {
            return 0.0F;
        }
        return Math.min(deltaSeconds, 0.25F);
    }

    private static String messageOf(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }

    public enum State {
        READY,
        ACTIVE,
        FAILED,
        CLOSED
    }

    public record Snapshot(
            boolean probeEnabled,
            State state,
            boolean pipelineBuilt,
            PresentationResult lastResult,
            long renderedFrames,
            long renderThreadId,
            String failureMessage
    ) {
        public Snapshot {
            state = Objects.requireNonNull(state, "state");
            if (renderedFrames < 0L || renderThreadId < 0L) {
                throw new IllegalArgumentException("render counters must be non-negative");
            }
        }
    }
}
