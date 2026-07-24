package org.hismeo.haikalathost.client.intercept;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.ShaderInstance;
import org.hismeo.haikalathost.client.backend.FullTakeoverConfiguration;
import org.hismeo.haikalathost.client.runtime.MinecraftRuntimeLifecycle;
import org.hismeo.haikalathost.client.staticmesh.StaticVertexBufferRegistry;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Converts Minecraft long-lived VertexBuffer objects into CPU identity handles in takeover mode.
 *
 * <p>No Minecraft buffer/VAO is created, bound, uploaded, drawn, or deleted. Upload data is
 * retained by the Host registry and lazily promoted into the shared static arena.</p>
 */
@Mixin(VertexBuffer.class)
public abstract class FullTakeoverVertexBufferAuditMixin {
    private static final int HOST_ONLY_GL_HANDLE = -2;

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenBuffers()I"))
    private int haikalatHost$skipMinecraftBufferCreation() {
        return takeover() ? HOST_ONLY_GL_HANDLE : GlStateManager._glGenBuffers();
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenVertexArrays()I"))
    private int haikalatHost$skipMinecraftVertexArrayCreation() {
        return takeover() ? HOST_ONLY_GL_HANDLE : GlStateManager._glGenVertexArrays();
    }

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    private void haikalatHost$captureStaticUpload(
            MeshData meshData, CallbackInfo callback) {
        if (!takeover()) return;
        VertexBuffer self = (VertexBuffer) (Object) this;
        StaticVertexBufferRegistry.capture(self, meshData);
        MinecraftRuntimeLifecycle.invalidateStaticVertexBuffer(self);
        callback.cancel();
    }

    @Inject(method = "uploadIndexBuffer", at = @At("HEAD"), cancellable = true)
    private void haikalatHost$captureStaticIndexReplacement(
            ByteBufferBuilder.Result result, CallbackInfo callback) {
        if (!takeover()) return;
        VertexBuffer self = (VertexBuffer) (Object) this;
        if (!StaticVertexBufferRegistry.replaceIndices(self, result)) {
            throw new IllegalStateException(
                    "Host received an index replacement for an unknown static VertexBuffer");
        }
        MinecraftRuntimeLifecycle.invalidateStaticVertexBuffer(self);
        callback.cancel();
    }

    @Inject(method = "bind", at = @At("HEAD"), cancellable = true)
    private void haikalatHost$skipMinecraftBind(CallbackInfo callback) {
        if (takeover()) callback.cancel();
    }

    @Inject(method = "unbind", at = @At("HEAD"), cancellable = true)
    private static void haikalatHost$skipMinecraftUnbind(CallbackInfo callback) {
        if (takeover()) callback.cancel();
    }

    @Inject(method = "drawWithShader", at = @At("HEAD"), cancellable = true)
    private void haikalatHost$captureStaticDraw(
            Matrix4f modelView,
            Matrix4f projection,
            ShaderInstance shader,
            CallbackInfo callback
    ) {
        if (!takeover()) return;
        VertexBuffer self = (VertexBuffer) (Object) this;
        if (!MinecraftRuntimeLifecycle.captureStaticVertexBuffer(
                self, modelView, projection, shader)) {
            throw new IllegalStateException(
                    "No Host static mesh payload for Minecraft VertexBuffer draw");
        }
        callback.cancel();
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void haikalatHost$auditUnclassifiedDraw(CallbackInfo callback) {
        if (!takeover()) return;
        var backend = MinecraftRuntimeLifecycle.takeoverBackend();
        if (backend != null) {
            backend.glAudit().checkMinecraftGl(
                    "VertexBuffer.draw without Host transform/material context");
        }
        callback.cancel();
    }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void haikalatHost$releaseStaticMesh(CallbackInfo callback) {
        if (!takeover()) return;
        MinecraftRuntimeLifecycle.releaseStaticVertexBuffer(
                (VertexBuffer) (Object) this);
        callback.cancel();
    }

    private static boolean takeover() {
        return FullTakeoverConfiguration.current().haikalatBackend();
    }
}
