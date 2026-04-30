package org.hismeo.fractureclient.client.impl.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.hismeo.fractureclient.client.init.KeyInit;
import org.jetbrains.annotations.NotNull;

public interface LocalPlayerImpl {
    default void moveKeyMove(LocalPlayer localPlayer, Minecraft minecraft, float amount, @NotNull Vec3 relative) {
        Vec3 moveInput = relative;
        if (KeyInit.ROTATE_CAMERA.isDown()) {
            // While rotating camera, A/D should rotate only and not strafe the player.
            moveInput = new Vec3(0.0, relative.y, relative.z);
        }
        Vec3 vec3 = Entity.getInputVector(moveInput, amount, minecraft.gameRenderer.getMainCamera().getYRot());
        localPlayer.setDeltaMovement(localPlayer.getDeltaMovement().add(vec3));
    }

    default boolean isPlayerMove(Input input) {
        if (KeyInit.ROTATE_CAMERA.isDown()) {
            return input.forwardImpulse != 0;
        }
        return input.forwardImpulse != 0 || input.leftImpulse != 0;
    }
}
