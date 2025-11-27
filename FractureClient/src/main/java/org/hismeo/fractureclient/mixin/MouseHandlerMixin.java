package org.hismeo.fractureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.hismeo.fractureclient.client.impl.mixin.MouseHandlerImpl;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin implements MouseHandlerImpl {
    @Shadow private boolean mouseGrabbed;

    @Shadow @Final private Minecraft minecraft;

    @Shadow private boolean ignoreFirstMove;

    @Shadow private double xpos;

    @Shadow private double ypos;

    @WrapOperation(method = "grabMouse", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(JIDD)V"))
    public void showMouse(long window, int cursorValue, double xPos, double yPos, Operation<Void> original) {
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }

    @WrapMethod(method = "turnPlayer")
    public void proxyTurnPlayer(double movementTime, Operation<Void> original) {
        this.fracture_client$turnPlayer((MouseHandler) (Object) this, movementTime);
    }
}
