package org.hismeo.fractureclient.client.impl.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;
import org.hismeo.fractureclient.client.control.CameraRotateController;
import org.hismeo.fractureclient.client.render.gui.DebugMessage;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public interface MouseHandlerImpl {
    //TODO WORLD2SCREEN UTIL
    default void byMouseMove(MouseHandler mouseHandler, Minecraft minecraft, double movementTime) {
        LocalPlayer player = minecraft.player;
        if (player == null) return;

        GameRenderer renderer = minecraft.gameRenderer;
        Window window = minecraft.getWindow();
        Camera camera = renderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        int width = window.getGuiScaledWidth();
        int height = window.getGuiScaledHeight();
        double mouseX = mouseHandler.xpos * width / window.getScreenWidth();
        double mouseY = mouseHandler.ypos * height / window.getScreenHeight();

        Matrix4f ortho = CameraImpl.orthoMatrix4f(minecraft, 0.0F);
        Quaternionf invRot = camera.rotation().conjugate(new Quaternionf());
        Vec3 rel = player.getEyePosition().subtract(camPos);

        Vector3f v = rel.toVector3f();
        v.rotate(invRot);
        Vector4f clip = new Vector4f(v.x(), v.y(), v.z(), 1.0f);
        clip.mul(ortho);

        double playerScreenX = (clip.x() * 0.5 + 0.5) * width;
        double playerScreenY = (1.0 - (clip.y() * 0.5 + 0.5)) * height;
        double dx = mouseX - playerScreenX;
        double dy = mouseY - playerScreenY;

        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dy)) + OrthographicCameraConfig.yaw;
        player.setYRot(Mth.wrapDegrees(yaw));

        DebugMessage.add("yaw=%s dx=%s dy=%s", yaw, dx, dy);
        DebugMessage.add("mouse=%s,%s", mouseX, mouseY);
        DebugMessage.add("playerScreen=%s,%s", playerScreenX, playerScreenY);
    }
}
