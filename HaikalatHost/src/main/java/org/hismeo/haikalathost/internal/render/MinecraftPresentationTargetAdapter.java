package org.hismeo.haikalathost.internal.render;

import com.kaleblangley.haikalat.backend.RenderFormat;
import com.kaleblangley.haikalat.core.material.ResourceOwnership;
import com.kaleblangley.haikalat.core.presentation.AttachmentRole;
import com.kaleblangley.haikalat.core.presentation.ExternalAttachment;
import com.kaleblangley.haikalat.core.presentation.PresentationTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;

import java.util.Objects;

/**
 * Converts Minecraft-owned framebuffer attachments into immutable Haikalat descriptors.
 *
 * <p>The adapter only describes native objects. It never resizes, changes texture parameters, or
 * deletes a framebuffer or texture. A monotonically increasing generation is assigned whenever
 * Minecraft replaces an id or changes the target extent.</p>
 */
public final class MinecraftPresentationTargetAdapter {
    private final TextureInspector textureInspector;

    private TargetIdentity lastIdentity;
    private TargetSnapshot lastSnapshot;
    private long generation;

    public MinecraftPresentationTargetAdapter() {
        this(TextureInspector.OPENGL);
    }

    MinecraftPresentationTargetAdapter(TextureInspector textureInspector) {
        this.textureInspector = Objects.requireNonNull(textureInspector, "textureInspector");
    }

    public TargetSnapshot adapt(MinecraftRenderTargetDescriptor minecraftTarget) {
        Objects.requireNonNull(minecraftTarget, "minecraftTarget");
        TargetIdentity identity = TargetIdentity.of(minecraftTarget);
        if (identity.equals(lastIdentity)) {
            return lastSnapshot;
        }

        generation = Math.incrementExact(generation);
        TargetSnapshot candidate = createSnapshot(minecraftTarget, generation);
        lastIdentity = identity;
        lastSnapshot = candidate;
        return candidate;
    }

    public TargetSnapshot snapshot() {
        return lastSnapshot;
    }

    private TargetSnapshot createSnapshot(
            MinecraftRenderTargetDescriptor target,
            long targetGeneration
    ) {
        if (target.minimized()) {
            PresentationTarget presentation = PresentationTarget.builder(0, 0)
                    .framebuffer(target.framebufferId())
                    .generation(targetGeneration)
                    .framebufferOwnership(ResourceOwnership.BORROWED)
                    .build();
            return new TargetSnapshot(
                    presentation,
                    0,
                    0,
                    false,
                    "zero_extent",
                    "Minecraft render target is minimized");
        }
        if (target.viewWidth() != target.allocationWidth()
                || target.viewHeight() != target.allocationHeight()) {
            throw new IllegalArgumentException(
                    "Minecraft target view " + target.viewWidth() + "x" + target.viewHeight()
                            + " does not match attachment storage "
                            + target.allocationWidth() + "x" + target.allocationHeight());
        }
        if (target.framebufferId() <= 0 || target.colorTextureId() <= 0) {
            throw new IllegalArgumentException(
                    "Minecraft presentation requires positive non-default framebuffer/color ids");
        }

        TextureFormatInfo colorInfo = textureInspector.inspect(target.colorTextureId());
        RenderFormat colorFormat = colorFormat(colorInfo);
        ExternalAttachment color = ExternalAttachment.borrowedColor(
                target.colorTextureId(),
                colorFormat,
                target.viewWidth(),
                target.viewHeight());

        ExternalAttachment depth = null;
        TextureFormatInfo depthInfo = TextureFormatInfo.NONE;
        String reasonCode = "ready";
        String message = "Minecraft color target is importable";
        if (target.hasDepth() && target.depthTextureId() > 0) {
            depthInfo = textureInspector.inspect(target.depthTextureId());
            RenderFormat depthFormat = depthFormat(depthInfo);
            if (depthFormat == null) {
                reasonCode = "depth_format_unsupported";
                message = "Minecraft depth texture internal format "
                        + hex(depthInfo.internalFormat())
                        + " depthBits=" + depthInfo.depthBits()
                        + " stencilBits=" + depthInfo.stencilBits()
                        + " is not representable by Haikalat 0.20.1";
            } else if (target.hasStencil() && depthInfo.stencilBits() <= 0) {
                reasonCode = "stencil_contract_mismatch";
                message = "Minecraft reports stencil support but its depth texture has no "
                        + "stencil bits";
            } else {
                AttachmentRole role = target.hasStencil()
                        ? AttachmentRole.DEPTH_STENCIL
                        : AttachmentRole.DEPTH;
                depth = new ExternalAttachment(
                        target.depthTextureId(),
                        role,
                        depthFormat,
                        target.viewWidth(),
                        target.viewHeight(),
                        1,
                        ResourceOwnership.BORROWED);
                message = target.hasStencil()
                        ? "Minecraft color and depth/stencil targets are importable"
                        : "Minecraft color and depth targets are importable";
            }
        }

        PresentationTarget presentation = PresentationTarget.builder(
                        target.viewWidth(),
                        target.viewHeight())
                .framebuffer(target.framebufferId())
                .generation(targetGeneration)
                .framebufferOwnership(ResourceOwnership.BORROWED)
                .color(color)
                .depth(depth)
                .build();
        return new TargetSnapshot(
                presentation,
                colorInfo.internalFormat(),
                depthInfo.internalFormat(),
                depth != null,
                reasonCode,
                message);
    }

    static RenderFormat colorFormat(TextureFormatInfo format) {
        return switch (format.internalFormat()) {
            case GL11.GL_RGBA, GL11.GL_RGBA8 -> RenderFormat.RGBA8;
            case GL21.GL_SRGB8_ALPHA8 -> RenderFormat.SRGB8_ALPHA8;
            case GL30.GL_RGBA16F -> RenderFormat.RGBA16F;
            case GL30.GL_R16F -> RenderFormat.R16F;
            case GL30.GL_RG16F -> RenderFormat.RG16F;
            case GL30.GL_RG32F -> RenderFormat.RG32F;
            default -> throw new IllegalArgumentException(
                    "Unsupported Minecraft color texture internal format "
                            + hex(format.internalFormat()));
        };
    }

    static RenderFormat depthFormat(TextureFormatInfo format) {
        if ((format.internalFormat() == GL11.GL_DEPTH_COMPONENT
                && format.depthBits() == 24)
                || format.internalFormat() == GL14.GL_DEPTH_COMPONENT24) {
            return RenderFormat.DEPTH_COMPONENT24;
        }
        if (format.internalFormat() == GL30.GL_DEPTH24_STENCIL8
                && format.depthBits() == 24
                && format.stencilBits() == 8) {
            return RenderFormat.DEPTH24_STENCIL8;
        }
        return null;
    }

    private static String hex(int value) {
        return "0x" + Integer.toHexString(value).toUpperCase(java.util.Locale.ROOT);
    }

    public record TargetSnapshot(
            PresentationTarget target,
            int colorInternalFormat,
            int depthInternalFormat,
            boolean depthImported,
            String reasonCode,
            String message
    ) {
        public TargetSnapshot {
            target = Objects.requireNonNull(target, "target");
            reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
            message = Objects.requireNonNull(message, "message");
        }

        public boolean renderable() {
            return target.isRenderable();
        }
    }

    record TextureFormatInfo(int internalFormat, int depthBits, int stencilBits) {
        private static final TextureFormatInfo NONE = new TextureFormatInfo(0, 0, 0);

        TextureFormatInfo {
            if (depthBits < 0 || stencilBits < 0) {
                throw new IllegalArgumentException("texture component sizes must be non-negative");
            }
        }
    }

    @FunctionalInterface
    interface TextureInspector {
        TextureInspector OPENGL = textureId -> {
            RenderSystem.assertOnRenderThread();
            return new TextureFormatInfo(
                    GL45.glGetTextureLevelParameteri(
                            textureId,
                            0,
                            GL11.GL_TEXTURE_INTERNAL_FORMAT),
                    GL45.glGetTextureLevelParameteri(
                            textureId,
                            0,
                            GL14.GL_TEXTURE_DEPTH_SIZE),
                    GL45.glGetTextureLevelParameteri(
                            textureId,
                            0,
                            GL30.GL_TEXTURE_STENCIL_SIZE));
        };

        TextureFormatInfo inspect(int textureId);
    }

    private record TargetIdentity(
            int framebufferId,
            int colorTextureId,
            int depthTextureId,
            int viewWidth,
            int viewHeight,
            int allocationWidth,
            int allocationHeight,
            boolean hasDepth,
            boolean hasStencil
    ) {
        private static TargetIdentity of(MinecraftRenderTargetDescriptor target) {
            return new TargetIdentity(
                    target.framebufferId(),
                    target.colorTextureId(),
                    target.depthTextureId(),
                    target.viewWidth(),
                    target.viewHeight(),
                    target.allocationWidth(),
                    target.allocationHeight(),
                    target.hasDepth(),
                    target.hasStencil());
        }
    }
}
