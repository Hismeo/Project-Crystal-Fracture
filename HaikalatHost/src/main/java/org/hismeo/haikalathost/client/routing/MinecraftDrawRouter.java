package org.hismeo.haikalathost.client.routing;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.RenderType;
import org.hismeo.haikalathost.client.diagnostics.MinecraftInteropDiagnostics;
import org.hismeo.haikalathost.client.runtime.MinecraftHaikalatRuntime;
import org.hismeo.haikalathost.client.runtime.MinecraftRuntimeLifecycle;
import org.hismeo.haikalathost.client.runtime.HaikalatHostConfiguration;

public final class MinecraftDrawRouter {
    private MinecraftDrawRouter() {
    }

    public static DrawDecision route(RenderType renderType, MeshData.DrawState drawState) {
        MinecraftInteropDiagnostics diagnostics = MinecraftRuntimeLifecycle.diagnostics();
        diagnostics.recordCaptured();

        if (!HaikalatHostConfiguration.current().immediateCompatibility()) {
            diagnostics.recordFallback(FallbackReason.DISABLED, renderType, drawState, "global switch");
            return DrawDecision.vanilla();
        }
        if (MinecraftRuntimeLifecycle.isResourceReloadInProgress()) {
            diagnostics.recordFallback(
                    FallbackReason.RESOURCE_RELOAD_IN_PROGRESS, renderType, drawState, "resource reload");
            return DrawDecision.vanilla();
        }
        if (!RenderSystem.isOnRenderThread()) {
            diagnostics.recordFallback(
                    FallbackReason.RUNTIME_NOT_READY, renderType, drawState, "not on render thread");
            return DrawDecision.vanilla();
        }

        MinecraftHaikalatRuntime runtime = MinecraftRuntimeLifecycle.runtimeForDraw();
        if (runtime == null) {
            diagnostics.recordFallback(
                    FallbackReason.RUNTIME_NOT_READY, renderType, drawState, "runtime unavailable");
            return DrawDecision.vanilla();
        }
        DrawRoute route = runtime.route(renderType, drawState);
        return route == DrawRoute.HAIKALAT_COMPAT
                ? DrawDecision.haikalat(runtime)
                : DrawDecision.vanilla();
    }

    public record DrawDecision(DrawRoute route, MinecraftHaikalatRuntime runtime) {
        public static DrawDecision vanilla() {
            return new DrawDecision(DrawRoute.VANILLA, null);
        }

        public static DrawDecision haikalat(MinecraftHaikalatRuntime runtime) {
            return new DrawDecision(DrawRoute.HAIKALAT_COMPAT, runtime);
        }
    }
}
