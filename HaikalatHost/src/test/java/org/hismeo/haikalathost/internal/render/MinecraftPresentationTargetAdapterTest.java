package org.hismeo.haikalathost.internal.render;

import com.kaleblangley.haikalat.backend.RenderFormat;
import com.kaleblangley.haikalat.core.material.ResourceOwnership;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftPresentationTargetAdapterTest {
    @Test
    void createsBorrowedColorDepthTargetAndOnlyAdvancesGenerationOnReplacement() {
        MinecraftPresentationTargetAdapter adapter =
                new MinecraftPresentationTargetAdapter(texture -> switch (texture) {
                    case 12, 22 -> new MinecraftPresentationTargetAdapter.TextureFormatInfo(
                            GL11.GL_RGBA8, 0, 0);
                    case 13, 23 -> new MinecraftPresentationTargetAdapter.TextureFormatInfo(
                            GL14.GL_DEPTH_COMPONENT24, 24, 0);
                    default -> throw new AssertionError("unexpected texture " + texture);
                });
        MinecraftRenderTargetDescriptor first = target(11, 12, 13, 1280, 720, true, false);

        var mapped = adapter.adapt(first);
        var repeated = adapter.adapt(first);

        assertSame(mapped, repeated);
        assertEquals(1L, mapped.target().generation());
        assertEquals(ResourceOwnership.BORROWED, mapped.target().framebufferOwnership());
        assertEquals(RenderFormat.RGBA8, mapped.target().color().orElseThrow().format());
        assertEquals(
                RenderFormat.DEPTH_COMPONENT24,
                mapped.target().depth().orElseThrow().format());
        assertTrue(mapped.depthImported());

        var replacement = adapter.adapt(target(
                21, 22, 23, 1920, 1080, true, false));
        assertEquals(2L, replacement.target().generation());
    }

    @Test
    void mapsCombinedDepthStencilWithoutClaimingOwnership() {
        MinecraftPresentationTargetAdapter adapter =
                new MinecraftPresentationTargetAdapter(texture -> texture == 12
                        ? new MinecraftPresentationTargetAdapter.TextureFormatInfo(
                                GL11.GL_RGBA8, 0, 0)
                        : new MinecraftPresentationTargetAdapter.TextureFormatInfo(
                                GL30.GL_DEPTH24_STENCIL8, 24, 8));

        var mapped = adapter.adapt(target(11, 12, 13, 640, 480, true, true));

        assertTrue(mapped.target().hasDepth());
        assertTrue(mapped.target().hasStencil());
        assertEquals(
                ResourceOwnership.BORROWED,
                mapped.target().depth().orElseThrow().ownership());
    }

    @Test
    void keepsColorImportButReportsUnrepresentableDepth() {
        MinecraftPresentationTargetAdapter adapter =
                new MinecraftPresentationTargetAdapter(texture -> texture == 12
                        ? new MinecraftPresentationTargetAdapter.TextureFormatInfo(
                                GL11.GL_RGBA8, 0, 0)
                        : new MinecraftPresentationTargetAdapter.TextureFormatInfo(
                                GL30.GL_DEPTH_COMPONENT32F, 32, 0));

        var mapped = adapter.adapt(target(11, 12, 13, 640, 480, true, false));

        assertTrue(mapped.target().color().isPresent());
        assertFalse(mapped.target().depth().isPresent());
        assertFalse(mapped.depthImported());
        assertEquals("depth_format_unsupported", mapped.reasonCode());
    }

    @Test
    void zeroExtentDoesNotInspectOrImportLiveAttachments() {
        MinecraftPresentationTargetAdapter adapter =
                new MinecraftPresentationTargetAdapter(texture -> {
                    throw new AssertionError("minimized target must not query textures");
                });
        MinecraftRenderTargetDescriptor minimized = new MinecraftRenderTargetDescriptor(
                11, 12, 13, 0, 0, 1280, 720, true, false);

        var mapped = adapter.adapt(minimized);

        assertFalse(mapped.renderable());
        assertTrue(mapped.target().color().isEmpty());
        assertTrue(mapped.target().depth().isEmpty());
    }

    @Test
    void rejectsViewThatDoesNotDescribeTheAttachmentStorage() {
        MinecraftPresentationTargetAdapter adapter =
                new MinecraftPresentationTargetAdapter(texture ->
                        new MinecraftPresentationTargetAdapter.TextureFormatInfo(
                                GL11.GL_RGBA8, 0, 0));
        MinecraftRenderTargetDescriptor target = new MinecraftRenderTargetDescriptor(
                11, 12, -1, 640, 360, 1280, 720, false, false);

        assertThrows(IllegalArgumentException.class, () -> adapter.adapt(target));
    }

    private static MinecraftRenderTargetDescriptor target(
            int framebuffer,
            int color,
            int depth,
            int width,
            int height,
            boolean hasDepth,
            boolean hasStencil
    ) {
        return new MinecraftRenderTargetDescriptor(
                framebuffer,
                color,
                depth,
                width,
                height,
                width,
                height,
                hasDepth,
                hasStencil);
    }
}
