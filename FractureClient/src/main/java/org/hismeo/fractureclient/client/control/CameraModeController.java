package org.hismeo.fractureclient.client.control;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.hismeo.fractureclient.FractureClient;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;
import org.hismeo.fractureclient.client.impl.mixin.CameraImpl;
import org.hismeo.fractureclient.client.render.gui.DebugMessage;
import org.lwjgl.glfw.GLFW;

/** Keeps every camera-related override behind the configured camera mode. */
public final class CameraModeController {
    private static OrthographicCameraConfig.CameraMode appliedMode;

    private CameraModeController() {
    }

    public static OrthographicCameraConfig.CameraMode getMode() {
        OrthographicCameraConfig.CameraMode configured = OrthographicCameraConfig.cameraMode;
        return configured == null
                ? OrthographicCameraConfig.CameraMode.FRACTURE_ORTHOGRAPHIC
                : configured;
    }

    public static boolean isOrthographic() {
        return getMode() == OrthographicCameraConfig.CameraMode.FRACTURE_ORTHOGRAPHIC;
    }

    public static void tick(Minecraft minecraft) {
        OrthographicCameraConfig.CameraMode desiredMode = getMode();
        boolean changed = desiredMode != appliedMode;
        appliedMode = desiredMode;
        enforceCameraType(minecraft);
        if (!changed) {
            return;
        }

        CameraRotateController.reset();
        CameraImpl.resetTracking();
        DebugMessage.clear();
        if (desiredMode == OrthographicCameraConfig.CameraMode.VANILLA_FIRST_PERSON
                && minecraft.player != null) {
            // Orthographic movement deliberately forces this flag; do not leak it into vanilla.
            minecraft.player.setSprinting(false);
        }
        synchronizeMouseMode(minecraft);
        FractureClient.LOGGER.info("Camera mode switched to {}", desiredMode);
    }

    /** F5 must not temporarily escape the mode selected by the client config. */
    public static void enforceCameraType(Minecraft minecraft) {
        CameraType desiredType = isOrthographic()
                ? CameraType.THIRD_PERSON_BACK
                : CameraType.FIRST_PERSON;
        if (minecraft.options.getCameraType() != desiredType) {
            minecraft.options.setCameraType(desiredType);
            minecraft.gameRenderer.checkEntityPostEffect(
                    desiredType.isFirstPerson() ? minecraft.getCameraEntity() : null
            );
            if (minecraft.levelRenderer != null) {
                minecraft.levelRenderer.needsUpdate();
            }
        }
    }

    private static void synchronizeMouseMode(Minecraft minecraft) {
        MouseHandler mouseHandler = minecraft.mouseHandler;
        if (mouseHandler.isMouseGrabbed()) {
            mouseHandler.releaseMouse();
        }

        if (minecraft.screen == null && minecraft.isWindowActive()) {
            mouseHandler.grabMouse();
            return;
        }

        int cursorMode = isOrthographic() ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL;
        GLFW.glfwSetInputMode(
                minecraft.getWindow().getWindow(),
                GLFW.GLFW_CURSOR,
                cursorMode
        );
    }
}
