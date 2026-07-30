package org.hismeo.haikalathost.internal.extension;

import com.kaleblangley.haikalat.core.device.GlRenderDevice;
import com.kaleblangley.haikalat.core.device.RenderDevice;
import com.kaleblangley.haikalat.core.presentation.PresentationTarget;
import com.kaleblangley.haikalat.subsystems.render3d.ExternalCamera;
import com.kaleblangley.haikalat.subsystems.resources.ResourceCatalog;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.api.client.advanced.HaikalatEngineContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatFrameContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatReloadContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatRenderExtension;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HaikalatExtensionSchedulerTest {
    private static final RenderDevice DEVICE = new GlRenderDevice();
    private static final ResourceCatalog RESOURCES = ResourceCatalog.builder()
            .mount("test", (asset, maxBytes) -> new byte[0])
            .build();
    private static final ExternalCamera CAMERA = ExternalCamera.of(
            new Matrix4f(),
            new Matrix4f(),
            new Vector3f(),
            0.0F);
    private static final PresentationTarget TARGET =
            PresentationTarget.defaultFramebuffer(16, 16);

    @Test
    void invokesLifecycleInFixedOrderAndClosesOnceInReverse() {
        List<String> calls = new ArrayList<>();
        HaikalatExtensionScheduler scheduler = scheduler(
                recording("first", calls),
                recording("second", calls));

        scheduler.initialize(engine(0L), Runnable::run);
        scheduler.render(frame(0L), Runnable::run);
        scheduler.resourcesReloaded(reload(1L), Runnable::run);
        scheduler.worldClosed(engine(1L), Runnable::run);
        scheduler.close(engine(1L), Runnable::run);
        scheduler.close(engine(1L), Runnable::run);

        assertEquals(List.of(
                "first:initialize",
                "second:initialize",
                "first:render",
                "second:render",
                "first:reload",
                "second:reload",
                "first:worldClosed",
                "second:worldClosed",
                "second:close",
                "first:close"), calls);
        assertEquals(
                List.of(
                        HaikalatExtensionScheduler.State.CLOSED,
                        HaikalatExtensionScheduler.State.CLOSED),
                scheduler.snapshot().stream()
                        .map(HaikalatExtensionScheduler.Snapshot::state)
                        .toList());
    }

    @Test
    void oneRenderFailureDoesNotAffectLaterExtensions() {
        List<String> calls = new ArrayList<>();
        HaikalatRenderExtension broken = frame -> {
            calls.add("broken");
            throw new IllegalStateException("intentional");
        };
        HaikalatRenderExtension healthy = frame -> calls.add("healthy");
        HaikalatExtensionScheduler scheduler = scheduler(broken, healthy);
        scheduler.initialize(engine(0L), Runnable::run);

        scheduler.render(frame(0L), Runnable::run);
        scheduler.render(frame(0L), Runnable::run);

        assertEquals(List.of("broken", "healthy", "healthy"), calls);
        var failed = scheduler.snapshot().get(0);
        var active = scheduler.snapshot().get(1);
        assertEquals(HaikalatExtensionScheduler.State.FAILED, failed.state());
        assertEquals(0L, failed.frames());
        assertEquals("render", failed.lastFailurePhase());
        assertNotNull(failed.lastFailure());
        assertEquals(HaikalatExtensionScheduler.State.ACTIVE, active.state());
        assertEquals(2L, active.frames());
    }

    @Test
    void frameContextRejectsMissingCameraOrTarget() {
        assertThrows(
                NullPointerException.class,
                () -> HaikalatExtensionContexts.frame(
                        DEVICE,
                        RESOURCES,
                        0L,
                        null,
                        TARGET,
                        0.0F,
                        1L));
        assertThrows(
                NullPointerException.class,
                () -> HaikalatExtensionContexts.frame(
                        DEVICE,
                        RESOURCES,
                        0L,
                        CAMERA,
                        null,
                        0.0F,
                        1L));
    }

    private static HaikalatExtensionScheduler scheduler(
            HaikalatRenderExtension... extensions
    ) {
        List<HaikalatExtensionRegistry.Registration> registrations =
                new ArrayList<>();
        for (int index = 0; index < extensions.length; index++) {
            registrations.add(new HaikalatExtensionRegistry.Registration(
                    ResourceLocation.fromNamespaceAndPath(
                            "test",
                            "extension_" + index),
                    "test",
                    extensions[index]));
        }
        return new HaikalatExtensionScheduler(registrations);
    }

    private static HaikalatRenderExtension recording(
            String name,
            List<String> calls
    ) {
        return new HaikalatRenderExtension() {
            @Override
            public void initialize(HaikalatEngineContext context) {
                calls.add(name + ":initialize");
            }

            @Override
            public void render(HaikalatFrameContext frame) {
                calls.add(name + ":render");
            }

            @Override
            public void resourcesReloaded(HaikalatReloadContext context) {
                calls.add(name + ":reload");
            }

            @Override
            public void worldClosed(HaikalatEngineContext context) {
                calls.add(name + ":worldClosed");
            }

            @Override
            public void close(HaikalatEngineContext context) {
                calls.add(name + ":close");
            }
        };
    }

    private static HaikalatEngineContext engine(long generation) {
        return HaikalatExtensionContexts.engine(DEVICE, RESOURCES, generation);
    }

    private static HaikalatReloadContext reload(long generation) {
        return HaikalatExtensionContexts.reload(DEVICE, RESOURCES, generation);
    }

    private static HaikalatFrameContext frame(long generation) {
        return HaikalatExtensionContexts.frame(
                DEVICE,
                RESOURCES,
                generation,
                CAMERA,
                TARGET,
                1.0F / 60.0F,
                7L);
    }
}
