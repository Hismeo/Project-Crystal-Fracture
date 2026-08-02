package org.hismeo.fractureclient.client.control;

import com.kaleblangley.haikalat.core.curve.Curve1f;
import com.kaleblangley.haikalat.core.curve.Curves;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;

public final class CameraRotateController {
    private static final float KEYBOARD_ROTATE_STEP = 5.0F;
    private static final float TARGET_EPSILON = 0.001F;
    private static final Curve1f ROTATION_CURVE = Curves.cubicBezier(
            0.16F,
            1.0F,
            0.30F,
            1.0F
    );
    private static final YawCurveAnimation YAW_ANIMATION =
            new YawCurveAnimation(ROTATION_CURVE);

    private CameraRotateController() {}

    public static float getRenderYaw() {
        if (!YAW_ANIMATION.isInitialised()) {
            return OrthographicCameraConfig.yaw;
        }
        return YAW_ANIMATION.sample(System.nanoTime());
    }

    public static void tick(Minecraft minecraft, boolean rotateKeyDown) {
        if (!CameraModeController.isOrthographic()) {
            reset();
            return;
        }
        if (minecraft.player == null) return;

        long nowNanos = System.nanoTime();
        synchronizeConfiguredYaw(nowNanos);
        if (rotateKeyDown) {
            updateRotateByKeyboard(minecraft, nowNanos);
        }
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        // no-op: keyboard rotation does not need mouse-centric helper overlay
    }

    public static void reset() {
        YAW_ANIMATION.reset();
    }

    private static void updateRotateByKeyboard(Minecraft minecraft, long nowNanos) {
        int direction = 0;
        if (minecraft.options.keyLeft.isDown()) direction += 1;
        if (minecraft.options.keyRight.isDown()) direction -= 1;
        if (direction == 0) return;

        float targetYaw = YawCurveAnimation.wrapDegrees(
                YAW_ANIMATION.targetYaw() + direction * KEYBOARD_ROTATE_STEP
        );
        OrthographicCameraConfig.yaw = targetYaw;
        YAW_ANIMATION.retarget(targetYaw, rotationTransitionSeconds(), nowNanos);
    }

    private static void synchronizeConfiguredYaw(long nowNanos) {
        float configuredYaw = YawCurveAnimation.wrapDegrees(OrthographicCameraConfig.yaw);
        if (!YAW_ANIMATION.isInitialised()) {
            YAW_ANIMATION.snap(configuredYaw, nowNanos);
            return;
        }
        if (Math.abs(YawCurveAnimation.angleDelta(
                configuredYaw,
                YAW_ANIMATION.targetYaw()
        )) > TARGET_EPSILON) {
            YAW_ANIMATION.retarget(configuredYaw, rotationTransitionSeconds(), nowNanos);
        }
    }

    private static float rotationTransitionSeconds() {
        return Math.max(
                0.05F,
                Math.min(1.0F, OrthographicCameraConfig.cameraRotationTransitionSeconds)
        );
    }
}
