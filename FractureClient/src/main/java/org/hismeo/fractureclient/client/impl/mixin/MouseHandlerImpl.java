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
import org.hismeo.fractureclient.client.render.gui.DebugMessage;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MouseHandlerImpl {
    private static final double EPS = 1.0E-8;

    //TODO WORLD2SCREEN UTIL
    public static void byMouseMove(MouseHandler mouseHandler, Minecraft minecraft, double movementTime) {
        LocalPlayer player = minecraft.player;
        GameRenderer renderer = minecraft.gameRenderer;
        if (player == null || renderer == null) return;

        Camera camera = renderer.getMainCamera();
        Window window = minecraft.getWindow();
        double guiW = window.getGuiScaledWidth();
        double guiH = window.getGuiScaledHeight();
        if (guiW <= 0.0 || guiH <= 0.0) return;

        double mouseX = mouseHandler.xpos * guiW / window.getScreenWidth();
        double mouseY = mouseHandler.ypos * guiH / window.getScreenHeight();
        double size = OrthographicCameraConfig.size;
        double rightLeft = Math.max(0.0001, size * guiW / guiH);
        double halfHeight = Math.max(0.0001, size);
        double ndcX = mouseX / guiW * 2.0 - 1.0;
        double ndcY = 1.0 - mouseY / guiH * 2.0;
        double localX = ndcX * rightLeft;
        double localY = ndcY * halfHeight;

        Quaternionf rot = new Quaternionf(camera.rotation());
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(rot);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f).rotate(rot);
        Vector3f forward = new Vector3f(0.0f, 0.0f, -1.0f).rotate(rot);
        Vec3 camPos = camera.getPosition();
        Vec3 rayOrigin = new Vec3(
                camPos.x + right.x * localX + up.x * localY,
                camPos.y + right.y * localX + up.y * localY,
                camPos.z + right.z * localX + up.z * localY
        );

        double targetY = player.getEyeY();
        double denom = forward.y;
        if (Math.abs(denom) < EPS) return;
        double t = (targetY - rayOrigin.y) / denom;

        if (t < 0.0) {
            forward.negate();
            denom = forward.y;
            if (Math.abs(denom) < EPS) return;
            t = (targetY - rayOrigin.y) / denom;
        }

        if (t < 0.0 || t > 1024.0) return;
        Vec3 hit = new Vec3(
                rayOrigin.x + forward.x * t,
                targetY,
                rayOrigin.z + forward.z * t
        );

        Vec3 aim = hit.subtract(player.getX(), targetY, player.getZ());
        if (aim.lengthSqr() < EPS) return;

        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)));
        player.setYRot(yaw);

        DebugMessage.add("yaw=%s aim=%s", yaw, aim.toString());
        DebugMessage.add("mouse=%s,%s", mouseX, mouseY);
        DebugMessage.add("ndc=%s,%s", ndcX, ndcY);
    }
}
