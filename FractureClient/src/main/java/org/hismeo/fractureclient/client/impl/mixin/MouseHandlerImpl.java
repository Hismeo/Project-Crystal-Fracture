package org.hismeo.fractureclient.client.impl.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.phys.Vec3;

public interface MouseHandlerImpl {
    default void byMouseMove(MouseHandler mouseHandler, Minecraft minecraft, double movementTime) {
        Vec3 camPos = minecraft.gameRenderer.getMainCamera().getPosition();
        double dx = mouseHandler.xpos - minecraft.getWindow().getScreenWidth() * 0.5;
        double dz = mouseHandler.ypos - minecraft.getWindow().getScreenHeight() * 0.5;

        double len = Math.sqrt(dx*dx + dz*dz);
        if (len < 1e-6) return;

        dx /= len;
        dz /= len;
        dz = -dz;

        float camYaw = minecraft.gameRenderer.getMainCamera().getYRot();
        double rad = Math.toRadians(-camYaw);

        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        double worldDx = dx * cos - dz * sin;
        double worldDz = dx * sin + dz * cos;

        Vec3 mouseWorld = camPos.add(worldDx * 10, 0, worldDz * 10);
        double vx = mouseWorld.x - minecraft.player.getX();
        double vz = mouseWorld.z - minecraft.player.getZ();
        float yRot = (float) (Math.atan2(vx, vz) * 180 / Math.PI);

        minecraft.player.setYRot(yRot);
        minecraft.player.setXRot(0);
    }
}
