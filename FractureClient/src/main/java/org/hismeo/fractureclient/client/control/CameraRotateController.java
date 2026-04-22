package org.hismeo.fractureclient.client.control;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;

public final class CameraRotateController {
    private static final float YAW_LERP_ALPHA = 0.5F;
    private static final double MIN_DRAG_LENGTH2 = 9.0;
    private static final int RENDER_RADIUS = 36;
    private static final int RING_COLOR = 0xAA33CCFF;
    private static final int CENTER_COLOR = 0xFFFFFFFF;
    private static final int LINE_COLOR = 0xAAFFFFFF;

    private static boolean rotating;
    private static boolean hasLastAngle;
    private static boolean hasSmoothYaw;
    private static double rotateCenterX;
    private static double rotateCenterY;
    private static double lastAngleDeg;
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
            updateRotateInteraction(minecraft);
        } else {
            rotating = false;
            hasLastAngle = false;
        }
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        if (!rotating) return;

        int cx = (int) Math.round(rotateCenterX);
        int cy = (int) Math.round(rotateCenterY);
        drawCircle(guiGraphics, cx, cy, RENDER_RADIUS, RING_COLOR);
        guiGraphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, CENTER_COLOR);

        int mx = (int) Math.round(getGuiMouseX(minecraft));
        int my = (int) Math.round(getGuiMouseY(minecraft));
        drawLine(guiGraphics, cx, cy, mx, my, LINE_COLOR);
    }

    private static void updateRotateInteraction(Minecraft minecraft) {
        double mouseX = getGuiMouseX(minecraft);
        double mouseY = getGuiMouseY(minecraft);
        if (!rotating) {
            rotating = true;
            hasLastAngle = false;
            rotateCenterX = mouseX;
            rotateCenterY = mouseY;
            ensureCameraYawState();
            return;
        }

        double dx = mouseX - rotateCenterX;
        double dy = mouseY - rotateCenterY;
        if (dx * dx + dy * dy < MIN_DRAG_LENGTH2) return;

        double angleDeg = Math.toDegrees(Math.atan2(dy, dx));
        if (!hasLastAngle) {
            lastAngleDeg = angleDeg;
            hasLastAngle = true;
            return;
        }

        double delta = Mth.wrapDegrees((float) (angleDeg - lastAngleDeg));
        ensureCameraYawState();
        targetYaw = Mth.wrapDegrees(targetYaw + (float) delta);
        OrthographicCameraConfig.yaw = targetYaw;
        lastAngleDeg = angleDeg;
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

    private static double getGuiMouseX(Minecraft minecraft) {
        return minecraft.mouseHandler.xpos * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
    }

    private static double getGuiMouseY(Minecraft minecraft) {
        return minecraft.mouseHandler.ypos * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
    }

    private static void drawCircle(GuiGraphics guiGraphics, int cx, int cy, int radius, int color) {
        for (int deg = 0; deg < 360; deg += 2) {
            double rad = Math.toRadians(deg);
            int x = cx + (int) Math.round(Math.cos(rad) * radius);
            int y = cy + (int) Math.round(Math.sin(rad) * radius);
            guiGraphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void drawLine(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            guiGraphics.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) break;
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
}
