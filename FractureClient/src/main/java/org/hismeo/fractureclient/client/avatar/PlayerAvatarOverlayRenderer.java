package org.hismeo.fractureclient.client.avatar;

import com.kaleblangley.haikalat.backend.RenderFormat;
import com.kaleblangley.haikalat.backend.shader.ShaderProgram;
import com.kaleblangley.haikalat.core.FrontFace;
import com.kaleblangley.haikalat.core.command.CommandBuffer;
import com.kaleblangley.haikalat.core.material.MaterialInstance;
import com.kaleblangley.haikalat.subsystems.render3d.MeshRenderer;
import org.hismeo.fractureclient.FractureClient;
import org.hismeo.haikalathost.api.client.advanced.HaikalatFrameContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;

/** Draws Avatar pixels directly into Minecraft's existing color/depth target. */
final class PlayerAvatarOverlayRenderer {
    private static final Vector3f LIGHT_DIRECTION =
            new Vector3f(-0.35F, -1.0F, -0.25F).normalize();
    private static final Vector3f LIGHT_COLOR = new Vector3f(1.0F);
    private final Matrix4f model = new Matrix4f();
    private boolean readyLogged;

    void render(HaikalatFrameContext frame, List<MeshRenderer> renderers) {
        if (!frame.target().isRenderable() || renderers.isEmpty()) {
            return;
        }

        CommandBuffer commands = frame.device().createCommandBuffer();
        commands.bindFramebuffer(GL_FRAMEBUFFER, frame.target().drawFramebufferId())
                .viewport(0, 0, frame.target().width(), frame.target().height())
                .enableBlend(false)
                .depthMask(true)
                .enableDepthTest(frame.target().depth().isPresent())
                .enableCullFace(false)
                .enableScissor(false)
                .enableFramebufferSrgb(
                        frame.target().colorFormat() == RenderFormat.SRGB8_ALPHA8);

        int frameIndex = (int) frame.frameIndex();
        for (MeshRenderer renderer : renderers) {
            renderer.modelMatrix(model, frameIndex);
            commands.frontFace(model.determinant3x3() < 0.0F ? FrontFace.CW : FrontFace.CCW);

            MaterialInstance material = renderer.material();
            material.bind(commands);
            ShaderProgram shader = material.material().shader();
            commands.setUniformMat4(shader, "uProjection", frame.camera().projection())
                    .setUniformMat4(shader, "uView", frame.camera().view())
                    .setUniformMat4(shader, "uModel", model)
                    .trySetUniformInt(shader, "uSkinningEnabled",
                            renderer.drawBinding().skinningEnabled() ? 1 : 0)
                    .trySetUniformInt(shader, "uMorphTargetCount",
                            renderer.drawBinding().morphTargetCount())
                    .setUniformInt(shader, "uDirectionalLightCount", 1)
                    .setUniformVec3(shader, "uDirectionalLights[0].direction", LIGHT_DIRECTION)
                    .setUniformVec3(shader, "uDirectionalLights[0].color", LIGHT_COLOR)
                    .setUniformFloat(shader, "uDirectionalLights[0].intensity", 2.25F)
                    .setUniformInt(shader, "uEncodeSrgb",
                            frame.target().colorFormat() == RenderFormat.RGBA8 ? 1 : 0);
            renderer.drawBinding().record(
                    commands,
                    shader,
                    frameIndex,
                    com.kaleblangley.haikalat.subsystems.render3d.SceneDrawBinding.Pass.FORWARD);
            commands.bindMesh(renderer.mesh()).drawMesh(renderer.mesh());
        }
        frame.device().execute(commands);

        if (!readyLogged) {
            readyLogged = true;
            FractureClient.LOGGER.info(
                    "Player Avatar direct overlay is ready with {} renderer(s)",
                    renderers.size());
        }
    }

    void reset() {
        readyLogged = false;
    }
}
