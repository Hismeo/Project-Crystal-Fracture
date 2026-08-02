package org.hismeo.fractureclient.client.render.octopath;

import com.kaleblangley.haikalat.backend.framebuffer.Framebuffer;
import com.kaleblangley.haikalat.backend.framebuffer.FramebufferDescriptor;
import com.kaleblangley.haikalat.backend.shader.ShaderProgram;
import com.kaleblangley.haikalat.core.command.CommandBuffer;
import com.kaleblangley.haikalat.core.presentation.PresentationTarget;
import com.kaleblangley.haikalat.subsystems.postprocess.BloomPass;
import com.kaleblangley.haikalat.subsystems.render3d.ExternalCamera;
import com.kaleblangley.haikalat.subsystems.render3d.ScreenQuad;
import org.hismeo.fractureclient.client.config.OctopathVisualConfig;
import org.hismeo.haikalathost.api.client.advanced.HaikalatFrameContext;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;

import java.util.List;

/**
 * Owns the GPU half of the HD-2D presentation. Minecraft's colour/depth target is borrowed for
 * one frame only; all intermediate targets belong to this post-processor.
 */
final class OctopathPostProcessor implements AutoCloseable {
    private static final int MAX_LOCAL_LIGHTS = 16;
    private static final int MAX_LOCAL_LIGHT_SHADOWS = OctopathPointLightShadowMap.MAX_LIGHTS;
    private static final int MAX_SUNLIGHT_FILTERS = OctopathSunlightFilterScanner.MAX_FILTERS;
    private static final int MAX_WATER_MISTS = OctopathWaterMistScanner.MAX_MISTS;
    private static final int MAX_ENTITY_GROUND_SHADOWS = OctopathEntityGroundShadowScanner.MAX_SHADOWS;
    private static final String SHADER_ROOT = "/assets/fracture_client/shaders/postprocess/";

    private final ShaderProgram lightingShader;
    private final ShaderProgram ambientOcclusionShader;
    private final ShaderProgram volumetricSunlightShader;
    private final ShaderProgram depthOfFieldShader;
    private final ShaderProgram presentationShader;
    private final ScreenQuad screenQuad;
    private final BloomPass bloomPass;

    private Framebuffer sceneCopy;
    private Framebuffer ambientOcclusionScene;
    private Framebuffer volumetricSunlightScene;
    private Framebuffer litScene;
    private Framebuffer depthOfFieldScene;
    private Framebuffer[] bloomDown = new Framebuffer[0];
    private Framebuffer[] bloomUp = new Framebuffer[0];
    private int width;
    private int height;
    private int bloomLevels;
    private boolean closed;

    OctopathPostProcessor() {
        lightingShader = ShaderProgram.fromResource(
                OctopathPostProcessor.class,
                SHADER_ROOT + "octopath_fullscreen.vsh",
                SHADER_ROOT + "octopath_lighting.fsh");
        ambientOcclusionShader = ShaderProgram.fromResource(
                OctopathPostProcessor.class,
                SHADER_ROOT + "octopath_fullscreen.vsh",
                SHADER_ROOT + "octopath_ao.fsh");
        volumetricSunlightShader = ShaderProgram.fromResource(
                OctopathPostProcessor.class,
                SHADER_ROOT + "octopath_fullscreen.vsh",
                SHADER_ROOT + "octopath_volumetric.fsh");
        depthOfFieldShader = ShaderProgram.fromResource(
                OctopathPostProcessor.class,
                SHADER_ROOT + "octopath_fullscreen.vsh",
                SHADER_ROOT + "octopath_dof.fsh");
        presentationShader = ShaderProgram.fromResource(
                OctopathPostProcessor.class,
                SHADER_ROOT + "octopath_fullscreen.vsh",
                SHADER_ROOT + "octopath_present.fsh");
        screenQuad = new ScreenQuad();
        bloomPass = new BloomPass();
    }

    void render(HaikalatFrameContext frame, OctopathFrameState state) {
        ensureOpen();
        PresentationTarget target = frame.target();
        if (!target.isRenderable() || target.depth().isEmpty()) {
            return;
        }

        int targetWidth = target.width();
        int targetHeight = target.height();
        ensureTargets(targetWidth, targetHeight, OctopathVisualConfig.bloomLevels);

        CommandBuffer commands = frame.device().createCommandBuffer();
        commands.custom(() -> GL45.glBlitNamedFramebuffer(
                target.readFramebufferId(),
                sceneCopy.id(),
                0,
                0,
                targetWidth,
                targetHeight,
                0,
                0,
                targetWidth,
                targetHeight,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST));

        int depthTexture = target.depth().orElseThrow().textureId();
        recordAmbientOcclusion(commands, frame.camera(), depthTexture);
        recordVolumetricSunlight(commands, frame.camera(), state, depthTexture);
        recordLighting(commands, frame.camera(), state, depthTexture);
        recordDepthOfField(commands, frame.camera(), state, depthTexture);
        recordBloom(commands);
        recordPresentation(commands, target, frame.camera(), state);
        frame.device().execute(commands);
    }

    private void recordAmbientOcclusion(
            CommandBuffer commands,
            ExternalCamera camera,
            int depthTexture
    ) {
        float requestedStrength = OctopathVisualConfig.ambientOcclusionEnabled
                ? clamp(OctopathVisualConfig.ambientOcclusionStrength, 0.0F, 0.55F)
                : 0.0F;

        beginFullscreen(commands, ambientOcclusionScene);
        commands.bindShader(ambientOcclusionShader)
                .bindTexture(0, depthTexture)
                .setUniformInt(ambientOcclusionShader, "uDepth", 0)
                .setUniformMat4(ambientOcclusionShader, "uInverseViewProjection", camera.inverseViewProjection())
                .setUniformVec3(ambientOcclusionShader, "uCameraPosition", camera.position())
                .setUniformFloat(ambientOcclusionShader, "uStrength", requestedStrength)
                .setUniformFloat(ambientOcclusionShader, "uRadiusPixels", clamp(
                        OctopathVisualConfig.ambientOcclusionRadiusPixels, 1.0F, 12.0F))
                .bindVertexArray(screenQuad.id())
                .drawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    private void recordLighting(
            CommandBuffer commands,
            ExternalCamera camera,
            OctopathFrameState state,
            int depthTexture
    ) {
        Vector2f focusUv = projectToViewport(camera, state.focusPosition());
        OctopathToneProfile toneProfile = state.toneProfile();
        OctopathDirectionalShadowSnapshot directionalShadow = state.directionalShadow();
        boolean directionalShadowAvailable = OctopathVisualConfig.directionalShadowMapEnabled
                && directionalShadow.available();
        int directionalShadowTexture = directionalShadowAvailable
                ? directionalShadow.depthTextureId()
                : depthTexture;
        float directionalShadowInverseResolution = directionalShadowAvailable
                ? 1.0F / Math.max(1, directionalShadow.resolution())
                : 1.0F;
        List<OctopathPointLightShadowSnapshot> localLightShadows = state.localLightShadows();
        int selectedLocalLightShadowCount = Math.min(
                Math.min(MAX_LOCAL_LIGHT_SHADOWS, Math.max(1, OctopathVisualConfig.localLightShadowCount)),
                Math.min(state.localLights().size(), localLightShadows.size()));
        OctopathPointLightShadowSnapshot localLightShadowAtlas = firstAvailablePointLightShadow(
                localLightShadows,
                selectedLocalLightShadowCount);
        boolean localLightShadowAtlasAvailable = OctopathVisualConfig.localLightShadowsEnabled
                && localLightShadowAtlas.available();
        int localLightShadowCount = localLightShadowAtlasAvailable
                ? selectedLocalLightShadowCount
                : 0;
        int localLightShadowTexture = localLightShadowAtlasAvailable
                ? localLightShadowAtlas.depthAtlasTextureId()
                : depthTexture;
        float localLightShadowInverseResolution = localLightShadowAtlasAvailable
                ? 1.0F / Math.max(1, localLightShadowAtlas.resolution())
                : 1.0F;

        beginFullscreen(commands, litScene);
        commands.bindShader(lightingShader)
                .bindTexture(0, sceneCopy.colorAttachment())
                .bindTexture(1, depthTexture)
                .bindTexture(2, directionalShadowTexture)
                .bindTexture(3, localLightShadowTexture)
                .bindTexture(4, volumetricSunlightScene.colorAttachment())
                .bindTexture(5, ambientOcclusionScene.colorAttachment())
                .setUniformInt(lightingShader, "uScene", 0)
                .setUniformInt(lightingShader, "uDepth", 1)
                .setUniformInt(lightingShader, "uDirectionalShadowDepth", 2)
                .setUniformInt(lightingShader, "uLocalLightShadowAtlas", 3)
                .setUniformInt(lightingShader, "uVolumetricSunlight", 4)
                .setUniformInt(lightingShader, "uAmbientOcclusion", 5)
                .setUniformMat4(lightingShader, "uInverseViewProjection", camera.inverseViewProjection())
                .setUniformMat4(lightingShader, "uDirectionalShadowLightViewProjection",
                        directionalShadow.lightViewProjection())
                .setUniformVec3(lightingShader, "uDirectionalShadowLightDirection",
                        directionalShadow.lightDirection())
                .setUniformVec3(lightingShader, "uCameraPosition", camera.position())
                .setUniformVec3(lightingShader, "uSkyColor", state.skyColor())
                .setUniformFloat(lightingShader, "uDaylight", state.daylight())
                .setUniformFloat(lightingShader, "uTwilight", state.twilight())
                .setUniformFloat(lightingShader, "uRain", state.rain())
                .setUniformFloat(lightingShader, "uThunder", state.thunder())
                .setUniformFloat(lightingShader, "uEffectStrength", clamp(
                        OctopathVisualConfig.effectStrength, 0.0F, 1.0F))
                .setUniformFloat(lightingShader, "uVibrance", clamp(
                        OctopathVisualConfig.vibrance, 0.0F, 1.0F))
                .setUniformVec3(lightingShader, "uToneMidTint", toneProfile.midTint())
                .setUniformVec3(lightingShader, "uToneShadowTint", toneProfile.shadowTint())
                .setUniformVec3(lightingShader, "uToneHighlightTint", toneProfile.highlightTint())
                .setUniformVec3(lightingShader, "uToneFogTint", toneProfile.fogTint())
                .setUniformVec3(lightingShader, "uToneSkyTint", toneProfile.skyTint())
                .setUniformFloat(lightingShader, "uToneVibranceMultiplier", toneProfile.vibranceMultiplier())
                .setUniformFloat(lightingShader, "uToneStrength", clamp(
                        OctopathVisualConfig.tonePresetStrength, 0.0F, 1.0F))
                .setUniformFloat(lightingShader, "uFogDensity", clamp(
                        OctopathVisualConfig.fogDensity, 0.0F, 0.1F))
                .setUniformFloat(lightingShader, "uRainFogBoost", clamp(
                        OctopathVisualConfig.rainFogBoost, 0.0F, 0.1F))
                .setUniformFloat(lightingShader, "uFogMaximumOpacity", clamp(
                        OctopathVisualConfig.fogMaximumOpacity, 0.0F, 1.0F))
                .setUniformFloat(lightingShader, "uWaterMistStrength", OctopathVisualConfig.waterMistEnabled
                        ? clamp(OctopathVisualConfig.waterMistStrength, 0.0F, 1.0F)
                        : 0.0F)
                .setUniformFloat(lightingShader, "uWaterMistHeight", clamp(
                        OctopathVisualConfig.waterMistHeight, 0.5F, 8.0F))
                .setUniformFloat(lightingShader, "uContactOcclusionStrength", clamp(
                        OctopathVisualConfig.depthContactOcclusionStrength, 0.0F, 0.18F))
                .setUniformFloat(lightingShader, "uContactOcclusionRadiusPixels", clamp(
                        OctopathVisualConfig.depthContactOcclusionRadiusPixels, 0.5F, 4.0F))
                .setUniformFloat(lightingShader, "uDirectionalShadowStrength", directionalShadowAvailable
                        ? clamp(OctopathVisualConfig.directionalShadowStrength, 0.0F, 0.70F)
                        : 0.0F)
                .setUniformFloat(lightingShader, "uDirectionalShadowBias", clamp(
                        OctopathVisualConfig.directionalShadowBias, 0.0001F, 0.02F))
                .setUniformFloat(lightingShader, "uDirectionalShadowSoftness", clamp(
                        OctopathVisualConfig.directionalShadowSoftness, 0.5F, 3.0F))
                .setUniformVec2(lightingShader, "uDirectionalShadowInverseResolution",
                        directionalShadowInverseResolution, directionalShadowInverseResolution)
                .setUniformFloat(lightingShader, "uEntityGroundShadowStrength",
                        OctopathVisualConfig.entityGroundShadows
                                && !OctopathVisualConfig.directionalShadowMapEnabled
                                && OctopathVisualConfig.suppressNativeEntityShadows
                                ? clamp(OctopathVisualConfig.entityGroundShadowStrength, 0.0F, 0.35F)
                                : 0.0F)
                .setUniformVec2(lightingShader, "uStageFocusUv", focusUv.x, focusUv.y)
                .setUniformFloat(lightingShader, "uStageSpotlightStrength", clamp(
                        OctopathVisualConfig.stageSpotlightStrength, 0.0F, 1.0F))
                .setUniformFloat(lightingShader, "uStageSpotlightRadius", clamp(
                        OctopathVisualConfig.stageSpotlightRadius, 0.15F, 1.4F))
                .setUniformFloat(lightingShader, "uLocalLightIntensity", clamp(
                        OctopathVisualConfig.localLightIntensity, 0.0F, 5.0F))
                .setUniformFloat(lightingShader, "uLocalLightReach", clamp(
                        OctopathVisualConfig.localLightReach, 0.4F, 2.5F))
                .setUniformFloat(lightingShader, "uLocalLightDaylightMultiplier", clamp(
                        OctopathVisualConfig.localLightDaylightMultiplier, 0.0F, 1.0F))
                .setUniformInt(lightingShader, "uLocalLightShadowCount", localLightShadowCount)
                .setUniformVec2(lightingShader, "uLocalLightShadowInverseResolution",
                        localLightShadowInverseResolution, localLightShadowInverseResolution)
                .setUniformFloat(lightingShader, "uLocalLightShadowAtlasTileStride",
                        OctopathPointLightShadowMap.tileStride(
                                Math.max(1, localLightShadowAtlas.resolution())))
                .setUniformFloat(lightingShader, "uLocalLightShadowAtlasGutter",
                        OctopathPointLightShadowMap.ATLAS_GUTTER)
                .setUniformFloat(lightingShader, "uLocalLightShadowStrength", localLightShadowAtlasAvailable
                        ? clamp(OctopathVisualConfig.localLightShadowStrength, 0.0F, 1.0F)
                        : 0.0F)
                .setUniformFloat(lightingShader, "uLocalLightShadowBias", clamp(
                        OctopathVisualConfig.localLightShadowBias, 0.0001F, 0.02F))
                .setUniformFloat(lightingShader, "uLocalLightShadowSoftness", clamp(
                        OctopathVisualConfig.localLightShadowSoftness, 0.5F, 3.0F));
        recordLocalLightShadows(commands, localLightShadows, localLightShadowCount);
        recordLocalLights(commands, state.localLights());
        recordWaterMists(commands, state.waterMists());
        recordEntityGroundShadows(commands, state.entityGroundShadows());
        commands.bindVertexArray(screenQuad.id()).drawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    private void recordVolumetricSunlight(
            CommandBuffer commands,
            ExternalCamera camera,
            OctopathFrameState state,
            int depthTexture
    ) {
        OctopathDirectionalShadowSnapshot directionalShadow = state.directionalShadow();
        boolean shadowAvailable = OctopathVisualConfig.directionalShadowMapEnabled
                && directionalShadow.available();
        float requestedStrength = shadowAvailable && OctopathVisualConfig.volumetricSunlightEnabled
                ? clamp(OctopathVisualConfig.volumetricSunlightStrength, 0.0F, 0.50F)
                : 0.0F;
        int directionalTexture = shadowAvailable ? directionalShadow.depthTextureId() : depthTexture;
        float inverseResolution = shadowAvailable
                ? 1.0F / Math.max(1, directionalShadow.resolution())
                : 1.0F;

        beginFullscreen(commands, volumetricSunlightScene);
        commands.bindShader(volumetricSunlightShader)
                .bindTexture(0, depthTexture)
                .bindTexture(1, directionalTexture)
                .setUniformInt(volumetricSunlightShader, "uDepth", 0)
                .setUniformInt(volumetricSunlightShader, "uDirectionalShadowDepth", 1)
                .setUniformMat4(volumetricSunlightShader, "uInverseViewProjection", camera.inverseViewProjection())
                .setUniformMat4(volumetricSunlightShader, "uDirectionalShadowLightViewProjection",
                        directionalShadow.lightViewProjection())
                .setUniformVec3(volumetricSunlightShader, "uDirectionalShadowLightDirection",
                        directionalShadow.lightDirection())
                .setUniformVec3(volumetricSunlightShader, "uCameraPosition", camera.position())
                .setUniformFloat(volumetricSunlightShader, "uDaylight", state.daylight())
                .setUniformFloat(volumetricSunlightShader, "uTwilight", state.twilight())
                .setUniformFloat(volumetricSunlightShader, "uRain", state.rain())
                .setUniformFloat(volumetricSunlightShader, "uThunder", state.thunder())
                .setUniformFloat(volumetricSunlightShader, "uStrength", requestedStrength)
                .setUniformFloat(volumetricSunlightShader, "uMaximumDistance", clamp(
                        OctopathVisualConfig.volumetricSunlightDistance, 4.0F, 64.0F))
                .setUniformInt(volumetricSunlightShader, "uSampleCount", Math.max(
                        4, Math.min(12, OctopathVisualConfig.volumetricSunlightSampleCount)))
                .setUniformFloat(volumetricSunlightShader, "uStainedGlassStrength", clamp(
                        OctopathVisualConfig.stainedGlassSunlightTintStrength, 0.0F, 1.0F))
                .setUniformVec2(volumetricSunlightShader, "uDirectionalShadowInverseResolution",
                        inverseResolution, inverseResolution);
        recordSunlightFilters(commands, state.sunlightFilters());
        commands.bindVertexArray(screenQuad.id()).drawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    private void recordSunlightFilters(
            CommandBuffer commands,
            List<OctopathSunlightFilter> filters
    ) {
        int count = Math.min(MAX_SUNLIGHT_FILTERS, filters.size());
        commands.setUniformInt(volumetricSunlightShader, "uStainedGlassFilterCount", count);
        for (int index = 0; index < count; index++) {
            OctopathSunlightFilter filter = filters.get(index);
            commands.setUniformVec3(
                    volumetricSunlightShader,
                    "uStainedGlassFilterPositions[" + index + "]",
                    filter.position());
            commands.setUniformVec3(
                    volumetricSunlightShader,
                    "uStainedGlassFilterColors[" + index + "]",
                    filter.color());
            commands.setUniformFloat(
                    volumetricSunlightShader,
                    "uStainedGlassFilterRadii[" + index + "]",
                    filter.radius());
        }
    }

    private void recordLocalLightShadows(
            CommandBuffer commands,
            List<OctopathPointLightShadowSnapshot> shadows,
            int shadowCount
    ) {
        for (int index = 0; index < MAX_LOCAL_LIGHT_SHADOWS; index++) {
            OctopathPointLightShadowSnapshot shadow = index < shadows.size()
                    ? shadows.get(index)
                    : OctopathPointLightShadowSnapshot.unavailable();
            boolean active = index < shadowCount && shadow.available();
            commands.setUniformInt(
                    lightingShader,
                    "uLocalLightShadowActive[" + index + "]",
                    active ? 1 : 0);
            commands.setUniformVec3(
                    lightingShader,
                    "uLocalLightShadowPositions[" + index + "]",
                    active ? shadow.lightPosition() : new Vector3f());
            commands.setUniformFloat(
                    lightingShader,
                    "uLocalLightShadowRanges[" + index + "]",
                    active ? shadow.range() : 0.0F);
        }
    }

    private static OctopathPointLightShadowSnapshot firstAvailablePointLightShadow(
            List<OctopathPointLightShadowSnapshot> shadows,
            int limit
    ) {
        for (int index = 0; index < Math.min(limit, shadows.size()); index++) {
            OctopathPointLightShadowSnapshot shadow = shadows.get(index);
            if (shadow.available()) {
                return shadow;
            }
        }
        return OctopathPointLightShadowSnapshot.unavailable();
    }

    private void recordDepthOfField(
            CommandBuffer commands,
            ExternalCamera camera,
            OctopathFrameState state,
            int depthTexture
    ) {
        Vector2f focusUv = projectToViewport(camera, state.focusPosition());
        Vector3f focusPlaneNormal = tiltShiftPlaneNormal(camera);
        beginFullscreen(commands, depthOfFieldScene);
        commands.bindShader(depthOfFieldShader)
                .bindTexture(0, litScene.colorAttachment())
                .bindTexture(1, depthTexture)
                .setUniformInt(depthOfFieldShader, "uScene", 0)
                .setUniformInt(depthOfFieldShader, "uDepth", 1)
                .setUniformMat4(depthOfFieldShader, "uInverseViewProjection", camera.inverseViewProjection())
                .setUniformVec3(depthOfFieldShader, "uCameraPosition", camera.position())
                .setUniformVec3(depthOfFieldShader, "uFocusPosition", state.focusPosition())
                .setUniformVec3(depthOfFieldShader, "uFocusPlaneNormal", focusPlaneNormal)
                .setUniformVec2(depthOfFieldShader, "uFocusUv", focusUv.x, focusUv.y)
                .setUniformFloat(depthOfFieldShader, "uFocusDistance", state.focusDistance())
                .setUniformFloat(depthOfFieldShader, "uFocusRange", clamp(
                        OctopathVisualConfig.depthOfFieldFocusRange, 0.5F, 64.0F))
                .setUniformFloat(depthOfFieldShader, "uDepthOfFieldStrength", clamp(
                        OctopathVisualConfig.depthOfFieldStrength, 0.0F, 1.0F))
                .setUniformFloat(depthOfFieldShader, "uMaximumBlurPixels", clamp(
                        OctopathVisualConfig.depthOfFieldMaxRadius, 0.0F, 12.0F))
                .setUniformFloat(depthOfFieldShader, "uTiltShiftStrength", clamp(
                        OctopathVisualConfig.tiltShiftStrength, 0.0F, 1.0F))
                .setUniformFloat(depthOfFieldShader, "uTiltShiftFocusBand", clamp(
                        OctopathVisualConfig.tiltShiftFocusBand, 0.03F, 0.45F))
                .setUniformFloat(depthOfFieldShader, "uTiltShiftTransition", clamp(
                        OctopathVisualConfig.tiltShiftTransition, 0.01F, 0.60F))
                .setUniformFloat(depthOfFieldShader, "uTiltShiftFocusLineTilt", clamp(
                        OctopathVisualConfig.tiltShiftFocusLineTilt, -0.5F, 0.5F))
                .setUniformFloat(depthOfFieldShader, "uTiltShiftEdgeBlur", clamp(
                        OctopathVisualConfig.tiltShiftEdgeBlur, 0.0F, 1.0F))
                .setUniformVec2(depthOfFieldShader, "uInverseResolution", 1.0F / width, 1.0F / height)
                .bindVertexArray(screenQuad.id())
                .drawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    private void recordBloom(CommandBuffer commands) {
        commands.bindFramebuffer(bloomDown[0])
                .viewport(0, 0, bloomDown[0].width(), bloomDown[0].height());
        bloomPass.recordExtract(
                commands,
                depthOfFieldScene.colorAttachment(),
                width,
                height,
                clamp(OctopathVisualConfig.bloomThreshold, 0.0F, 4.0F),
                clamp(OctopathVisualConfig.bloomSoftKnee, 0.0F, 1.0F));

        for (int index = 1; index < bloomDown.length; index++) {
            Framebuffer source = bloomDown[index - 1];
            Framebuffer destination = bloomDown[index];
            commands.bindFramebuffer(destination)
                    .viewport(0, 0, destination.width(), destination.height());
            bloomPass.recordDownsample(commands, source.colorAttachment(), source.width(), source.height());
        }

        for (int index = bloomUp.length - 1; index >= 0; index--) {
            Framebuffer high = bloomDown[index];
            Framebuffer low = index == bloomUp.length - 1
                    ? bloomDown[index + 1]
                    : bloomUp[index + 1];
            Framebuffer destination = bloomUp[index];
            commands.bindFramebuffer(destination)
                    .viewport(0, 0, destination.width(), destination.height());
            bloomPass.recordUpsample(
                    commands,
                    high.colorAttachment(),
                    low.colorAttachment(),
                    low.width(),
                    low.height());
        }
    }

    private void recordPresentation(
            CommandBuffer commands,
            PresentationTarget target,
            ExternalCamera camera,
            OctopathFrameState state
    ) {
        Vector2f focusUv = projectToViewport(camera, state.focusPosition());
        commands.bindFramebuffer(GL30.GL_FRAMEBUFFER, target.drawFramebufferId())
                .viewport(0, 0, target.width(), target.height())
                .enableBlend(false)
                .enableDepthTest(false)
                .enableCullFace(false)
                .enableScissor(false)
                .enableFramebufferSrgb(false)
                .depthMask(false)
                .bindShader(presentationShader)
                .bindTexture(0, depthOfFieldScene.colorAttachment())
                .bindTexture(1, bloomUp[0].colorAttachment())
                .setUniformInt(presentationShader, "uScene", 0)
                .setUniformInt(presentationShader, "uBloom", 1)
                .setUniformFloat(presentationShader, "uExposure", clamp(
                        OctopathVisualConfig.exposure, 0.1F, 4.0F))
                .setUniformFloat(presentationShader, "uBloomIntensity", clamp(
                        OctopathVisualConfig.bloomIntensity, 0.0F, 2.0F))
                .setUniformFloat(presentationShader, "uHighlightCompression", clamp(
                        OctopathVisualConfig.highlightCompression, 0.05F, 1.0F))
                .setUniformFloat(presentationShader, "uStageVignetteStrength", clamp(
                        OctopathVisualConfig.stageVignetteStrength, 0.0F, 1.0F)
                        * clamp(OctopathVisualConfig.effectStrength, 0.0F, 1.0F))
                .setUniformVec2(presentationShader, "uStageFocusUv", focusUv.x, focusUv.y)
                .bindVertexArray(screenQuad.id())
                .drawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    private void recordLocalLights(CommandBuffer commands, List<OctopathLocalLight> lights) {
        int count = Math.min(MAX_LOCAL_LIGHTS, lights.size());
        commands.setUniformInt(lightingShader, "uLocalLightCount", count);
        for (int index = 0; index < count; index++) {
            OctopathLocalLight light = lights.get(index);
            commands.setUniformVec3(lightingShader, "uLocalLightPositions[" + index + "]", light.position());
            commands.setUniformVec3(lightingShader, "uLocalLightColors[" + index + "]", light.color());
            commands.setUniformFloat(lightingShader, "uLocalLightPowers[" + index + "]", light.power());
        }
    }

    private void recordWaterMists(CommandBuffer commands, List<OctopathWaterMist> mists) {
        int count = Math.min(MAX_WATER_MISTS, mists.size());
        commands.setUniformInt(lightingShader, "uWaterMistCount", count);
        for (int index = 0; index < count; index++) {
            OctopathWaterMist mist = mists.get(index);
            commands.setUniformVec3(lightingShader, "uWaterMistPositions[" + index + "]", mist.position());
            commands.setUniformFloat(lightingShader, "uWaterMistRadii[" + index + "]", mist.radius());
            commands.setUniformFloat(lightingShader, "uWaterMistCoverages[" + index + "]", mist.coverage());
        }
    }

    private void recordEntityGroundShadows(
            CommandBuffer commands,
            List<OctopathEntityGroundShadow> shadows
    ) {
        int count = Math.min(MAX_ENTITY_GROUND_SHADOWS, shadows.size());
        commands.setUniformInt(lightingShader, "uEntityGroundShadowCount", count);
        for (int index = 0; index < count; index++) {
            OctopathEntityGroundShadow shadow = shadows.get(index);
            commands.setUniformVec3(lightingShader,
                    "uEntityGroundShadowPositions[" + index + "]", shadow.groundPosition());
            commands.setUniformFloat(lightingShader,
                    "uEntityGroundShadowRadii[" + index + "]", shadow.radius());
            commands.setUniformFloat(lightingShader,
                    "uEntityGroundShadowOpacities[" + index + "]", shadow.opacity());
        }
    }

    private void ensureTargets(int requestedWidth, int requestedHeight, int requestedBloomLevels) {
        int selectedBloomLevels = selectBloomLevels(requestedWidth, requestedHeight, requestedBloomLevels);
        if (sceneCopy != null
                && width == requestedWidth
                && height == requestedHeight
                && bloomLevels == selectedBloomLevels) {
            return;
        }

        disposeTargets();
        try {
            sceneCopy = createHdrTarget(requestedWidth, requestedHeight);
            ambientOcclusionScene = createHdrTarget(
                    Math.max(1, (requestedWidth + 1) / 2),
                    Math.max(1, (requestedHeight + 1) / 2));
            volumetricSunlightScene = createHdrTarget(
                    Math.max(1, (requestedWidth + 3) / 4),
                    Math.max(1, (requestedHeight + 3) / 4));
            litScene = createHdrTarget(requestedWidth, requestedHeight);
            depthOfFieldScene = createHdrTarget(requestedWidth, requestedHeight);
            bloomDown = new Framebuffer[selectedBloomLevels];
            int bloomWidth = Math.max(1, requestedWidth / 2);
            int bloomHeight = Math.max(1, requestedHeight / 2);
            for (int index = 0; index < bloomDown.length; index++) {
                bloomDown[index] = createHdrTarget(bloomWidth, bloomHeight);
                bloomWidth = Math.max(1, bloomWidth / 2);
                bloomHeight = Math.max(1, bloomHeight / 2);
            }
            bloomUp = new Framebuffer[selectedBloomLevels - 1];
            for (int index = 0; index < bloomUp.length; index++) {
                bloomUp[index] = createHdrTarget(bloomDown[index].width(), bloomDown[index].height());
            }
            width = requestedWidth;
            height = requestedHeight;
            bloomLevels = selectedBloomLevels;
        } catch (RuntimeException exception) {
            disposeTargets();
            throw exception;
        }
    }

    private static Framebuffer createHdrTarget(int width, int height) {
        return Framebuffer.fromDescriptor(
                FramebufferDescriptor.colorOnly(width, height, GL30.GL_RGBA16F));
    }

    private static int selectBloomLevels(int width, int height, int requestedLevels) {
        int selected = Math.max(2, Math.min(6, requestedLevels));
        int levelWidth = Math.max(1, width / 2);
        int levelHeight = Math.max(1, height / 2);
        while (selected > 2 && (levelWidth <= 1 || levelHeight <= 1)) {
            selected--;
        }
        return selected;
    }

    private static CommandBuffer beginFullscreen(CommandBuffer commands, Framebuffer destination) {
        return commands.bindFramebuffer(destination)
                .viewport(0, 0, destination.width(), destination.height())
                .enableBlend(false)
                .enableDepthTest(false)
                .enableCullFace(false)
                .enableScissor(false)
                .enableFramebufferSrgb(false)
                .depthMask(false);
    }

    private static Vector2f projectToViewport(ExternalCamera camera, Vector3f worldPosition) {
        Vector4f clip = new Vector4f(worldPosition.x, worldPosition.y, worldPosition.z, 1.0F);
        camera.viewProjection().transform(clip);
        if (Math.abs(clip.w) < 0.00001F) {
            return new Vector2f(0.5F, 0.5F);
        }
        float inverseW = 1.0F / clip.w;
        return new Vector2f(
                clamp(clip.x * inverseW * 0.5F + 0.5F, 0.0F, 1.0F),
                clamp(clip.y * inverseW * 0.5F + 0.5F, 0.0F, 1.0F));
    }

    private static Vector3f tiltShiftPlaneNormal(ExternalCamera camera) {
        Matrix4f inverseView = camera.inverseView();
        Vector3f up = new Vector3f(inverseView.m10(), inverseView.m11(), inverseView.m12()).normalize();
        Vector3f forward = new Vector3f(-inverseView.m20(), -inverseView.m21(), -inverseView.m22()).normalize();
        float lean = clamp(OctopathVisualConfig.tiltShiftDepthPlaneTilt, -1.0F, 1.0F);
        Vector3f normal = up.add(forward.mul(lean));
        if (normal.lengthSquared() < 0.00001F) {
            return new Vector3f(0.0F, 1.0F, 0.0F);
        }
        return normal.normalize();
    }

    private void disposeTargets() {
        closeFramebuffer(sceneCopy);
        closeFramebuffer(ambientOcclusionScene);
        closeFramebuffer(volumetricSunlightScene);
        closeFramebuffer(litScene);
        closeFramebuffer(depthOfFieldScene);
        closeFramebuffers(bloomDown);
        closeFramebuffers(bloomUp);
        sceneCopy = null;
        ambientOcclusionScene = null;
        volumetricSunlightScene = null;
        litScene = null;
        depthOfFieldScene = null;
        bloomDown = new Framebuffer[0];
        bloomUp = new Framebuffer[0];
        width = 0;
        height = 0;
        bloomLevels = 0;
    }

    private static void closeFramebuffers(Framebuffer[] framebuffers) {
        for (Framebuffer framebuffer : framebuffers) {
            closeFramebuffer(framebuffer);
        }
    }

    private static void closeFramebuffer(Framebuffer framebuffer) {
        if (framebuffer != null) {
            framebuffer.close();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        disposeTargets();
        bloomPass.close();
        screenQuad.close();
        lightingShader.close();
        ambientOcclusionShader.close();
        volumetricSunlightShader.close();
        depthOfFieldShader.close();
        presentationShader.close();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Octopath post processor has been closed");
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
