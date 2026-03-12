package org.hismeo.fractureclient.client.impl.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public interface LocalPlayerImpl {
    default void moveKeyMove(LocalPlayer localPlayer, Minecraft minecraft, float amount, @NotNull Vec3 relative) {
        Vec3 vec3 = Entity.getInputVector(relative, amount, minecraft.gameRenderer.getMainCamera().getYRot());
        localPlayer.setDeltaMovement(localPlayer.getDeltaMovement().add(vec3));
    }

    default boolean isPlayerMove(Input input) {
        return input.forwardImpulse != 0 || input.leftImpulse != 0;
    }
}
