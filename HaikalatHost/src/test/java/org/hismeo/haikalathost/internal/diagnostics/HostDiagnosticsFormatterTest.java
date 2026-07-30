package org.hismeo.haikalathost.internal.diagnostics;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.api.content.HaikalatAssetKind;
import org.hismeo.haikalathost.api.content.HaikalatAssetState;
import org.hismeo.haikalathost.api.content.HaikalatAssetStatus;
import org.hismeo.haikalathost.api.runtime.HostCapabilities;
import org.hismeo.haikalathost.api.runtime.HostLifecycleState;
import org.hismeo.haikalathost.api.runtime.HostStatus;
import org.hismeo.haikalathost.internal.render.MinecraftRenderTargetDescriptor;
import org.hismeo.haikalathost.internal.extension.HaikalatExtensionScheduler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostDiagnosticsFormatterTest {
    @Test
    void formatsNotStartedRuntimeWithoutRenderTarget() {
        List<String> lines = HostDiagnosticsFormatter.format(HostStatus.notStarted(), null);

        assertTrue(lines.getFirst().contains("lifecycle=NOT_STARTED"));
        assertTrue(lines.getFirst().contains("available=false"));
        assertTrue(lines.getFirst().contains("resourceGeneration=0"));
        assertEquals(
                "OpenGL context=unavailable version=unavailable profile=unavailable",
                lines.get(2));
        assertEquals("RenderTarget=unavailable (not captured yet)", lines.get(5));
        assertEquals("Scenes=unavailable (repository not initialized)", lines.getLast());
    }

    @Test
    void formatsCapabilitiesGenerationAndBorrowedRenderTarget() {
        HostStatus status = new HostStatus(
                HostLifecycleState.READY,
                "ready",
                "ready",
                new HostCapabilities(
                        true,
                        4,
                        6,
                        true,
                        "Test Vendor",
                        "Test Renderer",
                        "Test Driver",
                        List.of(),
                        List.of("bindless textures", "sparse textures")),
                7L);
        MinecraftRenderTargetDescriptor target = new MinecraftRenderTargetDescriptor(
                11,
                12,
                13,
                1920,
                1080,
                2048,
                2048,
                true,
                true);

        List<String> lines = HostDiagnosticsFormatter.format(status, target);

        assertTrue(lines.getFirst().contains("lifecycle=READY"));
        assertTrue(lines.getFirst().contains("available=true"));
        assertTrue(lines.getFirst().contains("resourceGeneration=7"));
        assertEquals("OpenGL context=available version=4.6 profile=core", lines.get(2));
        assertEquals(
                "requirements missing=none optional=bindless textures, sparse textures",
                lines.get(4));
        assertEquals(
                "RenderTarget borrowed framebuffer=11 colorTexture=12 depthTexture=13"
                        + " view=1920x1080 allocation=2048x2048 depth=true stencil=true",
                lines.get(5));
        assertEquals("Scenes=unavailable (repository not initialized)", lines.getLast());
    }

    @Test
    void formatsStableAssetSnapshots() {
        HaikalatAssetStatus asset = new HaikalatAssetStatus(
                ResourceLocation.fromNamespaceAndPath(
                        "example",
                        "haikalat/scenes/test.scene.json"),
                HaikalatAssetKind.SCENE,
                "example",
                HaikalatAssetState.FAILED,
                2L,
                1L,
                true,
                "scene_prepare_failed",
                "bad json");

        List<String> lines = HostDiagnosticsFormatter.formatAssets(List.of(asset));

        assertEquals("Haikalat assets total=1", lines.getFirst());
        assertTrue(lines.getLast().contains("owner=example"));
        assertTrue(lines.getLast().contains("lastKnownGood=true"));
        assertTrue(lines.getLast().contains("bad json"));
    }

    @Test
    void appendsTheSupportVersionMatrix() {
        List<String> lines = HostDiagnosticsFormatter.format(
                HostStatus.notStarted(),
                null,
                null,
                new HostVersionSnapshot("1.0", "0.20.1", "1.21.1", "21.1.215"));

        assertEquals(
                "Versions host=1.0 haikalat=0.20.1 minecraft=1.21.1 neoforge=21.1.215",
                lines.getLast());
    }

    @Test
    void formatsAdvancedExtensionStateAndFailure() {
        var active = new HaikalatExtensionScheduler.Snapshot(
                ResourceLocation.fromNamespaceAndPath("example", "main"),
                "example",
                HaikalatExtensionScheduler.State.ACTIVE,
                1524L,
                2L,
                3L,
                null,
                null);
        var failed = new HaikalatExtensionScheduler.Snapshot(
                ResourceLocation.fromNamespaceAndPath("other", "broken"),
                "other",
                HaikalatExtensionScheduler.State.FAILED,
                4L,
                2L,
                3L,
                "render",
                "IllegalStateException: bad pipeline");

        List<String> lines =
                HostDiagnosticsFormatter.formatExtensions(List.of(active, failed));

        assertEquals("Haikalat extensions total=2", lines.getFirst());
        assertTrue(lines.get(1).contains("example:main"));
        assertTrue(lines.get(1).contains("state=ACTIVE"));
        assertTrue(lines.get(1).contains("frames=1524"));
        assertTrue(lines.get(1).contains("lastFailure=none"));
        assertTrue(lines.get(2).contains(
                "lastFailure=render: IllegalStateException: bad pipeline"));
    }
}
