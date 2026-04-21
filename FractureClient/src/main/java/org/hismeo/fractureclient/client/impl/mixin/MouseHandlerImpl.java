package org.hismeo.fractureclient.client.impl.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.hismeo.fractureclient.client.render.gui.CustomDebugMessage;

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
        Vec3 rayDir = nearPoint.normalize();

        double targetY = minecraft.player.getEyeY();
        if (Math.abs(rayDir.y) < 1e-6) return;

        double t = (targetY - camPos.y) / rayDir.y;
        if (t <= 0.0) return;

        Vec3 hit = camPos.add(rayDir.scale(t));
        double dx = hit.x - minecraft.player.getX();
        double dz = hit.z - minecraft.player.getZ();
        if (dx * dx + dz * dz < 1e-8) return;

        float yRot = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)));
        minecraft.player.setYRot(yRot);
        minecraft.player.setXRot(0);

//        CustomDebugMessage.list.add("yRot %.2f hit(%.2f, %.2f)".formatted(yRot, hit.x, hit.z));
    }
}
