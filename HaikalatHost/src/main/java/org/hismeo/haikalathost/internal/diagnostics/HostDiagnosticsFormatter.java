package org.hismeo.haikalathost.internal.diagnostics;

import org.hismeo.haikalathost.api.runtime.HostCapabilities;
import org.hismeo.haikalathost.api.runtime.HostStatus;
import org.hismeo.haikalathost.api.content.HaikalatAssetStatus;
import org.hismeo.haikalathost.internal.content.RegisteredSceneRepository;
import org.hismeo.haikalathost.internal.extension.HaikalatExtensionScheduler;
import org.hismeo.haikalathost.internal.render.MinecraftCameraDescriptor;
import org.hismeo.haikalathost.internal.render.MinecraftEmbeddedRenderBridge;
import org.hismeo.haikalathost.internal.render.MinecraftPresentationTargetAdapter;
import org.hismeo.haikalathost.internal.render.MinecraftRenderTargetDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the text form of the developer status command without touching Minecraft or OpenGL.
 */
public final class HostDiagnosticsFormatter {
    private HostDiagnosticsFormatter() {
    }

    public static List<String> format(
            HostStatus status,
            MinecraftRenderTargetDescriptor renderTarget
    ) {
        return format(status, renderTarget, null);
    }

    public static List<String> format(
            HostStatus status,
            MinecraftRenderTargetDescriptor renderTarget,
            RegisteredSceneRepository.RepositorySnapshot scenes
    ) {
        return format(status, renderTarget, scenes, null);
    }

    public static List<String> format(
            HostStatus status,
            MinecraftRenderTargetDescriptor renderTarget,
            RegisteredSceneRepository.RepositorySnapshot scenes,
            HostVersionSnapshot versions
    ) {
        return format(status, renderTarget, scenes, versions, null, null, null);
    }

    public static List<String> format(
            HostStatus status,
            MinecraftRenderTargetDescriptor renderTarget,
            RegisteredSceneRepository.RepositorySnapshot scenes,
            HostVersionSnapshot versions,
            MinecraftPresentationTargetAdapter.TargetSnapshot presentationTarget,
            MinecraftCameraDescriptor camera,
            MinecraftEmbeddedRenderBridge.Snapshot embeddedRenderer
    ) {
        Objects.requireNonNull(status, "status");
        HostCapabilities capabilities = status.capabilities();
        List<String> lines = new ArrayList<>();

        lines.add("HaikalatHost lifecycle=" + status.state()
                + " available=" + status.available()
                + " reason=" + status.reasonCode()
                + " resourceGeneration=" + status.resourceGeneration());
        lines.add("message=" + status.message());

        if (capabilities.contextAvailable()) {
            lines.add("OpenGL context=available version="
                    + capabilities.majorVersion() + "." + capabilities.minorVersion()
                    + " profile=" + (capabilities.coreProfile() ? "core" : "non-core"));
        } else {
            lines.add("OpenGL context=unavailable version=unavailable profile=unavailable");
        }
        lines.add("OpenGL vendor=" + capabilities.vendor()
                + " renderer=" + capabilities.renderer()
                + " driver=" + capabilities.driverVersion());
        lines.add("requirements missing=" + formatCollection(capabilities.missingRequirements())
                + " optional=" + formatCollection(capabilities.optionalFeatures()));

        if (renderTarget == null) {
            lines.add("RenderTarget=unavailable (not captured yet)");
        } else {
            lines.add("RenderTarget borrowed framebuffer=" + renderTarget.framebufferId()
                    + " colorTexture=" + renderTarget.colorTextureId()
                    + " depthTexture=" + renderTarget.depthTextureId()
                    + " view=" + renderTarget.viewWidth() + "x" + renderTarget.viewHeight()
                    + " allocation=" + renderTarget.allocationWidth()
                    + "x" + renderTarget.allocationHeight()
                    + " depth=" + renderTarget.hasDepth()
                    + " stencil=" + renderTarget.hasStencil());
        }

        if (presentationTarget != null) {
            lines.add("PresentationTarget generation="
                    + presentationTarget.target().generation()
                    + " renderable=" + presentationTarget.renderable()
                    + " colorFormat=0x"
                    + Integer.toHexString(presentationTarget.colorInternalFormat())
                    + " depthFormat=0x"
                    + Integer.toHexString(presentationTarget.depthInternalFormat())
                    + " depthImported=" + presentationTarget.depthImported()
                    + " reason=" + presentationTarget.reasonCode());
        }
        if (camera != null) {
            lines.add("ExternalCamera revision=" + camera.revision()
                    + " partialTick=" + camera.partialTick()
                    + " deltaSeconds=" + camera.deltaSeconds()
                    + " near=" + camera.nearPlane()
                    + " far=" + camera.farPlane());
        }
        if (embeddedRenderer != null) {
            lines.add("EmbeddedPipeline state=" + embeddedRenderer.state()
                    + " probeEnabled=" + embeddedRenderer.probeEnabled()
                    + " built=" + embeddedRenderer.pipelineBuilt()
                    + " lastResult="
                    + (embeddedRenderer.lastResult() == null
                    ? "none" : embeddedRenderer.lastResult())
                    + " renderedFrames=" + embeddedRenderer.renderedFrames()
                    + (embeddedRenderer.failureMessage() == null
                    ? "" : " failure=" + embeddedRenderer.failureMessage()));
        }

        if (scenes == null) {
            lines.add("Scenes=unavailable (repository not initialized)");
        } else {
            lines.add("Scenes generation=" + scenes.resourceGeneration()
                    + " total=" + scenes.scenes().size()
                    + " preparing=" + scenes.preparingCount()
                    + " cpuReady=" + scenes.readyCount()
                    + " failed=" + scenes.failedCount());
            scenes.scenes().stream()
                    .filter(scene -> scene.failure() != null)
                    .forEach(scene -> lines.add(
                            "Scene failure id=" + scene.id()
                                    + " phase=" + valueOrUnknown(scene.failure().phase())
                                    + " asset=" + valueOrUnknown(scene.failure().asset())
                                    + " type=" + scene.failure().exceptionType()
                                    + " message=" + scene.failure().message()
                                    + " lastKnownGood=" + scene.hasPreparedPlan()));
        }

        if (versions != null) {
            lines.add("Versions host=" + versions.host()
                    + " haikalat=" + versions.haikalat()
                    + " minecraft=" + versions.minecraft()
                    + " neoforge=" + versions.neoForge());
        }

        return List.copyOf(lines);
    }

    private static String formatCollection(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    public static List<String> formatAssets(List<HaikalatAssetStatus> assets) {
        Objects.requireNonNull(assets, "assets");
        if (assets.isEmpty()) {
            return List.of("No Haikalat assets are registered");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Haikalat assets total=" + assets.size());
        assets.forEach(asset -> lines.add(
                asset.id()
                        + " kind=" + asset.kind()
                        + " owner=" + asset.ownerModId()
                        + " state=" + asset.state()
                        + " requestedGeneration=" + asset.requestedResourceGeneration()
                        + " preparedGeneration=" + asset.preparedResourceGeneration()
                        + " lastKnownGood=" + asset.lastKnownGood()
                        + " reason=" + asset.reasonCode()
                        + " message=" + asset.message()));
        return List.copyOf(lines);
    }

    public static List<String> formatExtensions(
            List<HaikalatExtensionScheduler.Snapshot> extensions
    ) {
        Objects.requireNonNull(extensions, "extensions");
        if (extensions.isEmpty()) {
            return List.of("No advanced Haikalat render extensions are registered");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Haikalat extensions total=" + extensions.size());
        extensions.forEach(extension -> lines.add(
                extension.id()
                        + " owner=" + extension.ownerModId()
                        + " state=" + extension.state()
                        + " frames=" + extension.frames()
                        + " targetGeneration="
                        + generationOrNone(extension.lastTargetGeneration())
                        + " resourceGeneration="
                        + generationOrNone(extension.lastResourceGeneration())
                        + " lastFailure="
                        + (extension.lastFailure() == null
                        ? "none"
                        : extension.lastFailurePhase() + ": " + extension.lastFailure())));
        return List.copyOf(lines);
    }

    private static String generationOrNone(long generation) {
        return generation < 0L ? "none" : Long.toString(generation);
    }
}
