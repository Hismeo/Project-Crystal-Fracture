package org.hismeo.haikalathost.internal.interop;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.neoforged.neoforge.client.GlStateBackup;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.util.Arrays;
import java.util.Objects;

/**
 * Restores Minecraft's OpenGL state after a bounded Haikalat interop call.
 *
 * <p>Haikalat calls OpenGL directly, so Blaze3D's Java-side cache does not observe those changes.
 * Restoration therefore uses raw GL calls to return the driver to the values represented by the
 * untouched Blaze3D cache. Calling {@code RenderSystem.restoreGlState} alone would be insufficient:
 * it may skip calls because the cache still contains the pre-Haikalat values.</p>
 *
 * <p>The unindexed core state is always captured. Indexed texture, sampler, buffer and image
 * bindings are captured only when named by a {@link GlStateFootprint}; the Host never scans all
 * hardware slots. Capturing a binding does not assume ownership of its object and this scope never
 * deletes a borrowed OpenGL resource.</p>
 */
public final class MinecraftGlInteropScope implements AutoCloseable {
    private static final boolean VERIFY_RESTORE =
            Boolean.getBoolean("haikalathost.verifyGlState");

    private final long ownerThreadId;
    private final GlStateFootprint footprint;
    private final GlStateBackup cachedRasterState = new GlStateBackup();
    private final int drawFramebuffer;
    private final int readFramebuffer;
    private final int readBufferSelection;
    private final int currentProgram;
    private final int vertexArray;
    private final int arrayBuffer;
    private final int activeTexture;
    private final int frontFace;
    private final int genericUniformBuffer;
    private final int genericStorageBuffer;
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private final float[] clearColor = new float[4];
    private final boolean framebufferSrgb;
    private final TextureUnitBinding[] textureBindings;
    private final IndexedBufferBinding[] uniformBufferBindings;
    private final IndexedBufferBinding[] storageBufferBindings;
    private final ImageUnitBinding[] imageBindings;
    private boolean closed;

    private MinecraftGlInteropScope(GlStateFootprint footprint) {
        RenderSystem.assertOnRenderThread();
        this.footprint = Objects.requireNonNull(footprint, "footprint");
        ownerThreadId = Thread.currentThread().threadId();

        footprint.validateAgainstLimits(
                footprint.hasTextureUnits()
                        ? GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) : 0,
                footprint.hasUniformBufferBindings()
                        ? GL11.glGetInteger(GL31.GL_MAX_UNIFORM_BUFFER_BINDINGS) : 0,
                footprint.hasStorageBufferBindings()
                        ? GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) : 0,
                footprint.hasImageUnits()
                        ? GL11.glGetInteger(GL42.GL_MAX_IMAGE_UNITS) : 0);

        RenderSystem.backupGlState(cachedRasterState);
        drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        readBufferSelection = footprint.capturesReadBufferSelection()
                ? GL11.glGetInteger(GL11.GL_READ_BUFFER) : -1;
        currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        arrayBuffer = footprint.capturesArrayBufferBinding()
                ? GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) : -1;
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        frontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        framebufferSrgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);

        textureBindings = captureTextureBindings(footprint.textureUnits(), activeTexture);
        genericUniformBuffer = footprint.hasUniformBufferBindings()
                ? GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING) : -1;
        uniformBufferBindings = captureIndexedBufferBindings(
                GL31.GL_UNIFORM_BUFFER_BINDING,
                GL31.GL_UNIFORM_BUFFER_START,
                GL31.GL_UNIFORM_BUFFER_SIZE,
                footprint.uniformBufferBindings());
        genericStorageBuffer = footprint.hasStorageBufferBindings()
                ? GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING) : -1;
        storageBufferBindings = captureIndexedBufferBindings(
                GL43.GL_SHADER_STORAGE_BUFFER_BINDING,
                GL43.GL_SHADER_STORAGE_BUFFER_START,
                GL43.GL_SHADER_STORAGE_BUFFER_SIZE,
                footprint.storageBufferBindings());
        imageBindings = captureImageBindings(footprint.imageUnits());
    }

    /**
     * Captures the current low-cost debug-probe footprint.
     */
    public static MinecraftGlInteropScope capture() {
        return capture(GlStateFootprint.MINIMAL_PROBE);
    }

    /**
     * Captures the exact indexed bindings declared by {@code footprint}.
     */
    public static MinecraftGlInteropScope capture(GlStateFootprint footprint) {
        return new MinecraftGlInteropScope(footprint);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (Thread.currentThread().threadId() != ownerThreadId) {
            throw new IllegalStateException("GL interop scope must close on its capture thread");
        }
        RenderSystem.assertOnRenderThread();

        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        if (readBufferSelection >= 0) {
            GL11.glReadBuffer(readBufferSelection);
        }
        GlStateManager._glUseProgram(currentProgram);
        RenderSystem.glBindVertexArray(vertexArray);
        if (arrayBuffer >= 0) {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        }
        restoreTextureBindings(textureBindings, activeTexture);
        restoreIndexedBufferBindings(
                GL31.GL_UNIFORM_BUFFER,
                genericUniformBuffer,
                uniformBufferBindings);
        restoreIndexedBufferBindings(
                GL43.GL_SHADER_STORAGE_BUFFER,
                genericStorageBuffer,
                storageBufferBindings);
        restoreImageBindings(imageBindings);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        GL11.glFrontFace(frontFace);
        GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        setEnabled(GL30.GL_FRAMEBUFFER_SRGB, framebufferSrgb);
        restoreRasterState(cachedRasterState);
        closed = true;

        if (VERIFY_RESTORE) {
            verifyRestored();
        }
    }

    private void verifyRestored() {
        if (drawFramebuffer != GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                || readFramebuffer != GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                || currentProgram != GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                || vertexArray != GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
                || activeTexture != GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
                || frontFace != GL11.glGetInteger(GL11.GL_FRONT_FACE)) {
            throw new IllegalStateException("HaikalatHost failed to restore a core GL binding");
        }
        if (readBufferSelection >= 0
                && readBufferSelection != GL11.glGetInteger(GL11.GL_READ_BUFFER)) {
            throw new IllegalStateException("HaikalatHost failed to restore the GL read buffer");
        }
        if (arrayBuffer >= 0
                && arrayBuffer != GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)) {
            throw new IllegalStateException(
                    "HaikalatHost failed to restore the GL array-buffer binding");
        }

        int[] restoredViewport = new int[4];
        int[] restoredScissor = new int[4];
        float[] restoredClearColor = new float[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, restoredViewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, restoredScissor);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, restoredClearColor);
        if (!Arrays.equals(viewport, restoredViewport)
                || !Arrays.equals(scissorBox, restoredScissor)
                || !Arrays.equals(clearColor, restoredClearColor)
                || framebufferSrgb != GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB)) {
            throw new IllegalStateException(
                    "HaikalatHost failed to restore viewport/scissor/clear state");
        }

        if (!Arrays.equals(
                textureBindings,
                captureTextureBindings(footprint.textureUnits(), activeTexture))) {
            throw new IllegalStateException(
                    "HaikalatHost failed to restore a texture or sampler binding");
        }
        verifyIndexedBufferBindings(
                "uniform-buffer",
                GL31.GL_UNIFORM_BUFFER_BINDING,
                GL31.GL_UNIFORM_BUFFER_START,
                GL31.GL_UNIFORM_BUFFER_SIZE,
                genericUniformBuffer,
                uniformBufferBindings);
        verifyIndexedBufferBindings(
                "shader-storage-buffer",
                GL43.GL_SHADER_STORAGE_BUFFER_BINDING,
                GL43.GL_SHADER_STORAGE_BUFFER_START,
                GL43.GL_SHADER_STORAGE_BUFFER_SIZE,
                genericStorageBuffer,
                storageBufferBindings);
        if (!Arrays.equals(imageBindings, captureImageBindings(footprint.imageUnits()))) {
            throw new IllegalStateException(
                    "HaikalatHost failed to restore an image-unit binding");
        }
    }

    private static TextureUnitBinding[] captureTextureBindings(
            int[] units,
            int originalActiveTexture
    ) {
        TextureUnitBinding[] bindings = new TextureUnitBinding[units.length];
        try {
            for (int index = 0; index < units.length; index++) {
                int unit = units[index];
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                bindings[index] = new TextureUnitBinding(
                        unit,
                        GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
                        GL11.glGetInteger(GL13.GL_TEXTURE_BINDING_CUBE_MAP),
                        GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, unit));
            }
            return bindings;
        } finally {
            GL13.glActiveTexture(originalActiveTexture);
        }
    }

    private static void restoreTextureBindings(
            TextureUnitBinding[] bindings,
            int originalActiveTexture
    ) {
        try {
            for (TextureUnitBinding binding : bindings) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + binding.unit());
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding.texture2d());
                GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, binding.textureCube());
                GL33.glBindSampler(binding.unit(), binding.sampler());
            }
        } finally {
            GL13.glActiveTexture(originalActiveTexture);
        }
    }

    private static IndexedBufferBinding[] captureIndexedBufferBindings(
            int bindingQuery,
            int startQuery,
            int sizeQuery,
            int[] indexes
    ) {
        IndexedBufferBinding[] bindings = new IndexedBufferBinding[indexes.length];
        for (int position = 0; position < indexes.length; position++) {
            int index = indexes[position];
            bindings[position] = new IndexedBufferBinding(
                    index,
                    GL30.glGetIntegeri(bindingQuery, index),
                    GL32.glGetInteger64i(startQuery, index),
                    GL32.glGetInteger64i(sizeQuery, index));
        }
        return bindings;
    }

    private static void restoreIndexedBufferBindings(
            int target,
            int genericBinding,
            IndexedBufferBinding[] bindings
    ) {
        if (genericBinding < 0) {
            return;
        }
        for (IndexedBufferBinding binding : bindings) {
            if (binding.buffer() == 0) {
                GL30.glBindBufferBase(target, binding.index(), 0);
            } else {
                GL30.glBindBufferRange(
                        target,
                        binding.index(),
                        binding.buffer(),
                        binding.offset(),
                        binding.size());
            }
        }
        // glBindBufferRange/Base also changes the generic target binding.
        GL15.glBindBuffer(target, genericBinding);
    }

    private static void verifyIndexedBufferBindings(
            String kind,
            int bindingQuery,
            int startQuery,
            int sizeQuery,
            int genericBinding,
            IndexedBufferBinding[] expected
    ) {
        if (genericBinding < 0) {
            return;
        }
        if (genericBinding != GL11.glGetInteger(bindingQuery)
                || !Arrays.equals(
                expected,
                captureIndexedBufferBindings(
                        bindingQuery,
                        startQuery,
                        sizeQuery,
                        Arrays.stream(expected)
                                .mapToInt(IndexedBufferBinding::index)
                                .toArray()))) {
            throw new IllegalStateException(
                    "HaikalatHost failed to restore a " + kind + " binding/range");
        }
    }

    private static ImageUnitBinding[] captureImageBindings(int[] units) {
        ImageUnitBinding[] bindings = new ImageUnitBinding[units.length];
        for (int position = 0; position < units.length; position++) {
            int unit = units[position];
            bindings[position] = new ImageUnitBinding(
                    unit,
                    GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_NAME, unit),
                    GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LEVEL, unit),
                    GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYERED, unit) == GL11.GL_TRUE,
                    GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYER, unit),
                    GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_ACCESS, unit),
                    GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_FORMAT, unit));
        }
        return bindings;
    }

    private static void restoreImageBindings(ImageUnitBinding[] bindings) {
        for (ImageUnitBinding binding : bindings) {
            GL42.glBindImageTexture(
                    binding.unit(),
                    binding.texture(),
                    binding.level(),
                    binding.layered(),
                    binding.layer(),
                    binding.access(),
                    binding.format());
        }
    }

    private static void restoreRasterState(GlStateBackup state) {
        setEnabled(GL11.GL_BLEND, state.blendEnabled);
        GL14.glBlendFuncSeparate(
                state.blendSrcRgb,
                state.blendDestRgb,
                state.blendSrcAlpha,
                state.blendDestAlpha);
        setEnabled(GL11.GL_DEPTH_TEST, state.depthEnabled);
        GL11.glDepthMask(state.depthMask);
        GL11.glDepthFunc(state.depthFunc);
        setEnabled(GL11.GL_CULL_FACE, state.cullEnabled);
        setEnabled(GL11.GL_POLYGON_OFFSET_FILL, state.polyOffsetFillEnabled);
        setEnabled(GL11.GL_POLYGON_OFFSET_LINE, state.polyOffsetLineEnabled);
        GL11.glPolygonOffset(state.polyOffsetFactor, state.polyOffsetUnits);
        setEnabled(GL11.GL_COLOR_LOGIC_OP, state.colorLogicEnabled);
        GL11.glLogicOp(state.colorLogicOp);
        GL11.glStencilFunc(state.stencilFuncFunc, state.stencilFuncRef, state.stencilFuncMask);
        GL11.glStencilMask(state.stencilMask);
        GL11.glStencilOp(state.stencilFail, state.stencilZFail, state.stencilZPass);
        setEnabled(GL11.GL_SCISSOR_TEST, state.scissorEnabled);
        GL11.glColorMask(
                state.colorMaskRed,
                state.colorMaskGreen,
                state.colorMaskBlue,
                state.colorMaskAlpha);
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    private record TextureUnitBinding(
            int unit,
            int texture2d,
            int textureCube,
            int sampler
    ) {
    }

    private record IndexedBufferBinding(
            int index,
            int buffer,
            long offset,
            long size
    ) {
    }

    private record ImageUnitBinding(
            int unit,
            int texture,
            int level,
            boolean layered,
            int layer,
            int access,
            int format
    ) {
    }
}
