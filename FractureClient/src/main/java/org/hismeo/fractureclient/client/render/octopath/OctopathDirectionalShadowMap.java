package org.hismeo.fractureclient.client.render.octopath;

import com.kaleblangley.haikalat.backend.framebuffer.Framebuffer;
import com.kaleblangley.haikalat.backend.shader.ShaderProgram;
import com.kaleblangley.haikalat.backend.vertex.VertexAttribute;
import com.kaleblangley.haikalat.backend.vertex.VertexSemantic;
import com.kaleblangley.haikalat.core.mesh.InstanceDataLayout;
import com.kaleblangley.haikalat.core.mesh.InstancedMeshBatch;
import com.kaleblangley.haikalat.core.mesh.Mesh;
import org.hismeo.haikalathost.api.client.advanced.HaikalatFrameContext;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the bounded proxy scene from the sun's direction into a depth texture.
 *
 * <p>This is deliberately a real depth pass, rather than a screen-space darkening decal: every
 * caster is rasterized from light space first, then the final lighting pass performs the depth
 * comparison against the reconstructed Minecraft world position.</p>
 */
final class OctopathDirectionalShadowMap implements AutoCloseable {
    private static final String SHADER_ROOT = "/assets/fracture_client/shaders/postprocess/";
    private static final int MAX_CASTERS = OctopathDirectionalShadowScanner.MAX_TERRAIN_COLUMNS
            + OctopathDirectionalShadowScanner.MAX_ENTITY_CASTERS;

    private static final float[] UNIT_CUBE_VERTICES = {
            // -Z
            -0.5F, -0.5F, -0.5F, 0.5F, -0.5F, -0.5F, 0.5F, 0.5F, -0.5F,
            -0.5F, -0.5F, -0.5F, 0.5F, 0.5F, -0.5F, -0.5F, 0.5F, -0.5F,
            // +Z
            -0.5F, -0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, -0.5F, 0.5F,
            -0.5F, -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F,
            // -X
            -0.5F, -0.5F, -0.5F, -0.5F, 0.5F, -0.5F, -0.5F, 0.5F, 0.5F,
            -0.5F, -0.5F, -0.5F, -0.5F, 0.5F, 0.5F, -0.5F, -0.5F, 0.5F,
            // +X
            0.5F, -0.5F, -0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, -0.5F,
            0.5F, -0.5F, -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, 0.5F, 0.5F,
            // -Y
            -0.5F, -0.5F, -0.5F, -0.5F, -0.5F, 0.5F, 0.5F, -0.5F, 0.5F,
            -0.5F, -0.5F, -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, -0.5F, -0.5F,
            // +Y
            -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, 0.5F, -0.5F, 0.5F, 0.5F,
            -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, -0.5F, 0.5F, 0.5F, 0.5F
    };

    private final ShaderProgram depthShader;
    private final Mesh cubeMesh;
    private final InstancedMeshBatch cubeBatch;
    private final List<Matrix4f> submittedTransforms = new ArrayList<>(MAX_CASTERS);

    private Framebuffer target;
    private int resolution;
    private boolean closed;

    OctopathDirectionalShadowMap() {
        depthShader = ShaderProgram.fromResource(
                OctopathDirectionalShadowMap.class,
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

    OctopathDirectionalShadowSnapshot render(
            HaikalatFrameContext frame,
            OctopathDirectionalShadowFrame shadowFrame,
            int requestedResolution
    ) {
        ensureOpen();
        List<OctopathDirectionalShadowCaster> terrainCasters = shadowFrame.terrainCasters();
        List<OctopathDirectionalShadowCaster> entityCasters = shadowFrame.entityCasters();
        if (terrainCasters.isEmpty() && entityCasters.isEmpty()) {
            return OctopathDirectionalShadowSnapshot.unavailable();
        }

        ensureTarget(requestedResolution);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.id());
            GL11.glViewport(0, 0, resolution, resolution);
            // The colour attachment only exists to keep this cross-driver framebuffer complete.
            // The pass writes depth alone and never leaks the colour-mask state to Haikalat.
            GL11.glColorMask(false, false, false, false);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LESS);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glClearDepth(1.0D);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

            depthShader.use().setMat4("uLightViewProjection", shadowFrame.lightViewProjection());
            cubeBatch.beginFrame();
            submittedTransforms.clear();
            collectTransforms(terrainCasters);
            collectTransforms(entityCasters);
            cubeBatch.submitOwnedSnapshots(submittedTransforms);
            cubeBatch.flush();

            return new OctopathDirectionalShadowSnapshot(
                    target.depthAttachment(),
                    shadowFrame.lightViewProjection(),
                    shadowFrame.lightDirection(),
                    resolution,
                    true);
        } finally {
            // All following work is command-buffer driven. Return to the safe fullscreen-pass
            // baseline and explicitly invalidate Haikalat's direct-GL cache before it resumes.
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            // Subsequent fullscreen passes must not inherit a world-render scissor rectangle.
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

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (target != null) {
            target.close();
            target = null;
        }
        cubeBatch.close();
        cubeMesh.close();
        depthShader.close();
    }

    private void collectTransforms(List<OctopathDirectionalShadowCaster> casters) {
        for (OctopathDirectionalShadowCaster caster : casters) {
            submittedTransforms.add(caster.modelMatrixSnapshot());
        }
    }

    private void ensureTarget(int requestedResolution) {
        int selectedResolution = Math.max(256, Math.min(2_048, requestedResolution));
        if (target != null && resolution == selectedResolution) {
            return;
        }
        if (target != null) {
            target.close();
        }
        target = Framebuffer.withDepthTexture(selectedResolution, selectedResolution);
        resolution = selectedResolution;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Directional shadow map has been closed");
        }
    }
}
