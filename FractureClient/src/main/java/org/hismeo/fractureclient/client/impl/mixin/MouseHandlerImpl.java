package org.hismeo.fractureclient.client.impl.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.phys.Vec3;
import org.hismeo.crystallib.util.YawAimUtil;

public interface MouseHandlerImpl {
    default void byMouseMove(MouseHandler mouseHandler, Minecraft minecraft, double movementTime) {
        if (minecraft.player == null || minecraft.gameRenderer == null) return;

        double screenWidth = minecraft.getWindow().getScreenWidth();
        double screenHeight = minecraft.getWindow().getScreenHeight();
        if (screenWidth <= 0 || screenHeight <= 0) return;

        Vec3 camPos = minecraft.gameRenderer.getMainCamera().getPosition();
        double ndcX = mouseHandler.xpos / screenWidth * 2.0 - 1.0; // right positive
        double ndcY = 1.0 - mouseHandler.ypos / screenHeight * 2.0; // up positive

        Vec3 nearPoint = minecraft.gameRenderer.getMainCamera().getNearPlane().getPointOnPlane((float) ndcX, (float) ndcY);
        double rayLen = Math.sqrt(nearPoint.x * nearPoint.x + nearPoint.y * nearPoint.y + nearPoint.z * nearPoint.z);
        if (rayLen < 1e-12) return;
        double rayX = nearPoint.x / rayLen;
        double rayY = nearPoint.y / rayLen;
        double rayZ = nearPoint.z / rayLen;

        float yRot = YawAimUtil.computeAimYaw(
                camPos.x, camPos.y, camPos.z,
                minecraft.player.getX(), minecraft.player.getEyeY(), minecraft.player.getZ(),
                rayX, rayY, rayZ
        );
        if (Float.isNaN(yRot)) return;

        minecraft.player.setYRot(yRot);
        minecraft.player.setXRot(0);
    }

//            if (minecraft.player == null) return;
//
//    double screenWidth = minecraft.getWindow().getScreenWidth();
//    double screenHeight = minecraft.getWindow().getScreenHeight();
//    Vec3 camPos = minecraft.gameRenderer.getMainCamera().getPosition();
//    Vec3 playerPos = minecraft.player.position();
//    double playerScreenX = screenWidth * 0.5 + (playerPos.x - camPos.x);
//    double playerScreenY = screenHeight * 0.5 + (playerPos.z - camPos.z);
//    double dx = mouseHandler.xpos - playerScreenX;
//    double dz = mouseHandler.ypos - playerScreenY;
//
//        if (dx * dx + dz * dz < 1.0) return;
//    float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
//        minecraft.player.setYRot(yaw);
//        minecraft.player.setXRot(0);
}
