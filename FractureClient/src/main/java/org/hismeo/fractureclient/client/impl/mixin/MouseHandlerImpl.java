package org.hismeo.fractureclient.client.impl.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.hismeo.fractureclient.FractureClient;
import org.hismeo.fractureclient.client.render.gui.CustomDebugMessage;
import org.joml.Vector3f;

public interface MouseHandlerImpl {
    default void fracture_client$turnPlayer(MouseHandler mouseHandler, double movementTime) {
        Minecraft minecraft = mouseHandler.minecraft;
        Vec3 camPos = minecraft.gameRenderer.getMainCamera().getPosition();
        double dx = mouseHandler.xpos - minecraft.getWindow().getScreenWidth() * 0.5;
        double dz = mouseHandler.ypos - minecraft.getWindow().getScreenHeight() * 0.5;

        double len = Math.sqrt(dx*dx + dz*dz);
        if (len < 1e-6) return;

        dx /= len;
        dz /= len;
        dz = -dz;

        Vec3 mouseWorld = camPos.add(dx * 10, 0, dz * 10);
        double vx = mouseWorld.x - minecraft.player.getX();
        double vz = mouseWorld.z - minecraft.player.getZ();
        float yRot = (float) (Math.atan2(vx, vz) * 180 / Math.PI);

        minecraft.player.setYRot(yRot);
        minecraft.player.setXRot(0);


//        CustomDebugMessage.list.add("M yRot: %.3f".formatted(yRot));
//        CustomDebugMessage.list.add("P yRot: %.3f".formatted(minecraft.player.getYRot()));
//        CustomDebugMessage.list.add("dx: %.3f".formatted(dx));
//        CustlmDebugMessage.list.add("dz: %.3f".formatted(dz));
//        CustomDebugMessage.list.add("len: %.3f".formatted(len));
//        CustomDebugMessage.list.add("mouseWorld: %s".formatted(mouseWorld.toString()));
//        CustomDebugMessage.list.add("vx: %.3f".formatted(vx));
//        CustomDebugMessage.list.add("vz: %.3f".formatted(vz));
    }
}
