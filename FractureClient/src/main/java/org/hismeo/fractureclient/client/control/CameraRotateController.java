package org.hismeo.fractureclient.client.control;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;

//TODO UGLY CODE
public final class CameraRotateController {
    private static final float YAW_LERP_ALPHA = 0.5F;
    private static final float KEYBOARD_ROTATE_STEP = 5.0F;

    private static boolean hasSmoothYaw;
    private static float smoothYaw;
    private static float targetYaw;

    private CameraRotateController() {}

    public static float getRenderYaw() {
        return hasSmoothYaw ? smoothYaw : OrthographicCameraConfig.yaw;
    }

    public static void tick(Minecraft minecraft, boolean rotateKeyDown) {
        if (minecraft.player == null) return;

        tickYawAnimation();
        if (rotateKeyDown) {
            updateRotateByKeyboard(minecraft);
        }
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        // no-op: keyboard rotation does not need mouse-centric helper overlay
    }

    private static void updateRotateByKeyboard(Minecraft minecraft) {
        int direction = 0;
        if (minecraft.options.keyLeft.isDown()) direction += 1;
        if (minecraft.options.keyRight.isDown()) direction -= 1;
        if (direction == 0) return;

        ensureCameraYawState();
        targetYaw = Mth.wrapDegrees(targetYaw + direction * KEYBOARD_ROTATE_STEP);
        OrthographicCameraConfig.yaw = targetYaw;
    }

    private static void ensureCameraYawState() {
        if (hasSmoothYaw) return;
        targetYaw = OrthographicCameraConfig.yaw;
        smoothYaw = OrthographicCameraConfig.yaw;
        hasSmoothYaw = true;
    }

    private static void tickYawAnimation() {
        ensureCameraYawState();
        float delta = Mth.wrapDegrees(targetYaw - smoothYaw);
        if (Math.abs(delta) < 0.01F) {
            smoothYaw = targetYaw;
            return;
        }
        smoothYaw = Mth.wrapDegrees(smoothYaw + delta * YAW_LERP_ALPHA);
    }
}
