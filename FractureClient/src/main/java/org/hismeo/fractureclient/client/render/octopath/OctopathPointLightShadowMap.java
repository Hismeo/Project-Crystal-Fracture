package org.hismeo.fractureclient.client.render.octopath;

import com.kaleblangley.haikalat.backend.shader.ShaderProgram;
import com.kaleblangley.haikalat.backend.vertex.VertexAttribute;
import com.kaleblangley.haikalat.backend.vertex.VertexSemantic;
import com.kaleblangley.haikalat.core.mesh.InstanceDataLayout;
import com.kaleblangley.haikalat.core.mesh.InstancedMeshBatch;
import com.kaleblangley.haikalat.core.mesh.Mesh;
import org.hismeo.haikalathost.api.client.advanced.HaikalatFrameContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders up to sixteen compact point-light depth cubes into one 2D depth atlas.
 *
 * <p>Each source owns six fixed tiles. A physical atlas is used instead of sixteen independent
 * cube resources so the lighting pass needs only one 2D sampler, leaving texture units available
 * for the normal Minecraft/Haikalat composition.</p>
 */
final class OctopathPointLightShadowMap implements AutoCloseable {
    static final int MAX_LIGHTS = 16;
    static final int FACE_COUNT = OctopathPointLightShadowSnapshot.FACE_COUNT;
    static final int ATLAS_COLUMNS = 12;
    static final int ATLAS_ROWS = 8;
    static final int ATLAS_GUTTER = 2;

    private static final String SHADER_ROOT = "/assets/fracture_client/shaders/postprocess/";
    private static final int MAX_CASTERS = 1_024;
    private static final float NEAR_PLANE = 0.20F;

    private static final float[] UNIT_CUBE_VERTICES = {
            -0.5F, -0.5F, -0.5F, 0.5F, -0.5F, -0.5F, 0.5F, 0.5F, -0.5F,
            -0.5F, -0.5F, -0.5F, 0.5F, 0.5F, -0.5F, -0.5F, 0.5F, -0.5F,
            -0.5F, -0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, -0.5F, 0.5F,
            -0.5F, -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F,
            -0.5F, -0.5F, -0.5F, -0.5F, 0.5F, -0.5F, -0.5F, 0.5F, 0.5F,
            -0.5F, -0.5F, -0.5F, -0.5F, 0.5F, 0.5F, -0.5F, -0.5F, 0.5F,
            0.5F, -0.5F, -0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, -0.5F,
            0.5F, -0.5F, -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, 0.5F, 0.5F,
            -0.5F, -0.5F, -0.5F, -0.5F, -0.5F, 0.5F, 0.5F, -0.5F, 0.5F,
            -0.5F, -0.5F, -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, -0.5F, -0.5F,
            -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, 0.5F, -0.5F, 0.5F, 0.5F,
            -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, -0.5F, 0.5F, 0.5F, 0.5F
    };

    private static final Vector3f[] FACE_DIRECTIONS = {
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(-1.0F, 0.0F, 0.0F),
            new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F),
            new Vector3f(0.0F, 0.0F, 1.0F), new Vector3f(0.0F, 0.0F, -1.0F)
    };
    private static final Vector3f[] FACE_UPS = {
            new Vector3f(0.0F, -1.0F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F),
            new Vector3f(0.0F, 0.0F, 1.0F), new Vector3f(0.0F, 0.0F, -1.0F),
            new Vector3f(0.0F, -1.0F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F)
    };

    private final ShaderProgram depthShader;
    private final Mesh cubeMesh;
    private final InstancedMeshBatch cubeBatch;
    private final List<Matrix4f> submittedTransforms = new ArrayList<>(MAX_CASTERS);

    private int depthAtlasTexture;
    private int framebuffer;
    private int resolution;
    private boolean closed;

    OctopathPointLightShadowMap() {
        depthShader = ShaderProgram.fromResource(
                OctopathPointLightShadowMap.class,
                SHADER_ROOT + "octopath_shadow_depth.vsh",
                SHADER_ROOT + "octopath_shadow_depth.fsh");
        VertexAttribute position = VertexAttribute.builder()
                .index(0)
                .size(3)
                .type(GL11.GL_FLOAT)
                .offsetBytes(0)
                .semantic(VertexSemantic.POSITION)
                .build();
        cubeMesh = Mesh.builder()
                .vertices(UNIT_CUBE_VERTICES, Float.BYTES * 3, position)
                .primitiveMode(GL11.GL_TRIANGLES)
                .build();
        cubeBatch = InstancedMeshBatch.of(cubeMesh, MAX_CASTERS, InstanceDataLayout.mat4Transform(3));
    }

    OctopathPointLightShadowSnapshot render(
            HaikalatFrameContext frame,
            OctopathPointLightShadowFrame shadowFrame,
            int requestedResolution,
            int lightIndex
    ) {
        ensureOpen();
        if (lightIndex < 0 || lightIndex >= MAX_LIGHTS) {
            throw new IndexOutOfBoundsException("Point-light atlas slot out of bounds: " + lightIndex);
        }
        if (shadowFrame.casters().isEmpty()) {
            return OctopathPointLightShadowSnapshot.unavailable();
        }
        ensureAtlas(requestedResolution);
        Matrix4f[] faceViewProjections = createFaceViewProjections(
                shadowFrame.lightPosition(),
                shadowFrame.range());
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        try {
            GL11.glColorMask(false, false, false, false);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LESS);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);

            for (int face = 0; face < FACE_COUNT; face++) {
                int tile = tileIndex(lightIndex, face);
                int viewportX = tileColumn(tile) * tileStride(resolution) + ATLAS_GUTTER;
                int viewportY = tileRow(tile) * tileStride(resolution) + ATLAS_GUTTER;
                GL11.glViewport(viewportX, viewportY, resolution, resolution);
                GL11.glScissor(viewportX, viewportY, resolution, resolution);
                GL11.glClearDepth(1.0D);
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                depthShader.use().setMat4("uLightViewProjection", faceViewProjections[face]);
                cubeBatch.beginFrame();
                submittedTransforms.clear();
                for (OctopathDirectionalShadowCaster caster : shadowFrame.casters()) {
                    submittedTransforms.add(caster.modelMatrixSnapshot());
                }
                cubeBatch.submitOwnedSnapshots(submittedTransforms);
                cubeBatch.flush();
            }

            return new OctopathPointLightShadowSnapshot(
                    shadowFrame.lightPosition(),
                    depthAtlasTexture,
                    resolution,
                    shadowFrame.range(),
                    true);
        } finally {
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL11.glViewport(
                    previousViewport[0],
                    previousViewport[1],
                    previousViewport[2],
                    previousViewport[3]);
            depthShader.stopUsing();
            frame.device().invalidateState();
        }
    }

    static int atlasWidth(int faceResolution) {
        return ATLAS_COLUMNS * tileStride(faceResolution);
    }

    static int atlasHeight(int faceResolution) {
        return ATLAS_ROWS * tileStride(faceResolution);
    }

    static int tileStride(int faceResolution) {
        return Math.max(1, faceResolution) + ATLAS_GUTTER * 2;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeAtlas();
        cubeBatch.close();
        cubeMesh.close();
        depthShader.close();
    }

    private void ensureAtlas(int requestedResolution) {
        int selectedResolution = Math.max(128, Math.min(512, requestedResolution));
        if (depthAtlasTexture != 0 && framebuffer != 0 && resolution == selectedResolution) {
            return;
        }
        closeAtlas();
        try {
            int atlasWidth = atlasWidth(selectedResolution);
            int atlasHeight = atlasHeight(selectedResolution);
            depthAtlasTexture = GL45.glCreateTextures(GL11.GL_TEXTURE_2D);
            GL45.glTextureStorage2D(
                    depthAtlasTexture,
                    1,
                    GL30.GL_DEPTH_COMPONENT32F,
                    atlasWidth,
                    atlasHeight);
            GL45.glTextureParameteri(depthAtlasTexture, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL45.glTextureParameteri(depthAtlasTexture, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL45.glTextureParameteri(depthAtlasTexture, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL45.glTextureParameteri(depthAtlasTexture, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            framebuffer = GL45.glCreateFramebuffers();
            GL45.glNamedFramebufferTexture(
                    framebuffer,
                    GL30.GL_DEPTH_ATTACHMENT,
                    depthAtlasTexture,
                    0);
            GL45.glNamedFramebufferDrawBuffer(framebuffer, GL11.GL_NONE);
            GL45.glNamedFramebufferReadBuffer(framebuffer, GL11.GL_NONE);
            int status = GL45.glCheckNamedFramebufferStatus(framebuffer, GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Point-light depth atlas framebuffer incomplete: 0x"
                        + Integer.toHexString(status));
            }
            resolution = selectedResolution;
        } catch (RuntimeException exception) {
            closeAtlas();
            throw exception;
        }
    }

    private void closeAtlas() {
        if (framebuffer != 0) {
            GL45.glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
        if (depthAtlasTexture != 0) {
            GL11.glDeleteTextures(depthAtlasTexture);
            depthAtlasTexture = 0;
        }
        resolution = 0;
    }

    private static int tileIndex(int lightIndex, int face) {
        return lightIndex * FACE_COUNT + face;
    }

    private static int tileColumn(int tile) {
        return tile % ATLAS_COLUMNS;
    }

    private static int tileRow(int tile) {
        return tile / ATLAS_COLUMNS;
    }

    private static Matrix4f[] createFaceViewProjections(Vector3f lightPosition, float range) {
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(90.0),
                1.0F,
                NEAR_PLANE,
                Math.max(NEAR_PLANE + 0.1F, range));
        Matrix4f[] matrices = new Matrix4f[FACE_COUNT];
        for (int face = 0; face < FACE_COUNT; face++) {
            Vector3f center = new Vector3f(lightPosition).add(FACE_DIRECTIONS[face]);
            Matrix4f view = new Matrix4f().lookAt(lightPosition, center, FACE_UPS[face]);
            matrices[face] = new Matrix4f(projection).mul(view);
        }
        return matrices;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Point-light shadow map has been closed");
        }
    }
}
