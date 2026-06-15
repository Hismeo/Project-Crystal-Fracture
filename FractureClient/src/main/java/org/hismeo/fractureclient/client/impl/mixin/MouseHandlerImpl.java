package org.hismeo.fractureclient.client.impl.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;
import org.hismeo.fractureclient.client.render.gui.DebugMessage;

public interface MouseHandlerImpl {
    //    default void byMouseMove(MouseHandler mouseHandler, Minecraft minecraft, double movementTime) {
//        LocalPlayer player = minecraft.player;
//        if (player == null || minecraft.gameRenderer == null) return;
//
//        double screenWidth = minecraft.getWindow().getScreenWidth();
//        double screenHeight = minecraft.getWindow().getScreenHeight();
//        if (screenWidth <= 0 || screenHeight <= 0) return;
//
//        Camera mainCamera = minecraft.gameRenderer.getMainCamera();
//        Vec3 camPos = mainCamera.getPosition();
//        double ndcX = mouseHandler.xpos / screenWidth * 2.0 - 1.0; // right positive
//        double ndcY = 1.0 - mouseHandler.ypos / screenHeight * 2.0; // up positive
//
//        Vec3 nearPoint = mainCamera.getNearPlane().getPointOnPlane((float) ndcX, (float) ndcY);
//        Vec3 planeWorld = camPos.add(nearPoint.x, nearPoint.y, nearPoint.z);
//        Vec3 aim = planeWorld.subtract(player.getX(), player.getEyeY(), player.getZ());
//
//        double rayLen = aim.length();
//        if (rayLen < 1e-12) return;
//        float yRot = Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-aim.x, aim.z)));
//
//        if (Float.isNaN(yRot)) return;
//        player.setYRot(yRot);
//        player.setXRot(0);
//    }
    default void byMouseMove(MouseHandler mouseHandler, Minecraft minecraft, double movementTime) {
        LocalPlayer player = minecraft.player;
        if (player == null) return;

        GameRenderer gameRenderer = minecraft.gameRenderer;
        Window window = minecraft.getWindow();
        Vec3 camPos = gameRenderer.getMainCamera().getPosition();

        int width = window.getGuiScaledWidth();
        int height = window.getGuiScaledHeight();

        double mouseX = mouseHandler.xpos * (double) window.getGuiScaledWidth() / (double) window.getScreenWidth();
        double mouseY = mouseHandler.ypos * (double) window.getGuiScaledHeight() / (double) window.getScreenHeight();

        float size = OrthographicCameraConfig.size;
        float rightLeft = Math.max(0.0F, size * width / height);
        float minSize = Math.max(0.0F, size);

        double scaleX = width / (2.0 * rightLeft);
        double scaleY = height / (2.0 * minSize);

        double playerScreenX = width / 2f - (player.getX() - camPos.x) * scaleX;
        double pitchRad = Math.toRadians(OrthographicCameraConfig.pitch);
        double playerScreenY = height / 2f - (player.getZ() - camPos.z) * scaleY * pitchRad;

        double dx = mouseX - playerScreenX;
        double dy = mouseY - playerScreenY;
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dx, -dy)));
        player.setYRot(yaw);
        DebugMessage.add("yaw%s, dx%s, dy%s", yaw, dx, dy);
        DebugMessage.add("mx%s, my%s", mouseX, mouseY);
        DebugMessage.add("px%s, py%s", playerScreenX, playerScreenY);
        DebugMessage.add("cx%s, cy%s", camPos.x, camPos.z);
    }
}
