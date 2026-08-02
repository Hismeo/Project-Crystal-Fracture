package org.hismeo.fractureclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.hismeo.fractureclient.client.control.CameraModeController;
import org.hismeo.fractureclient.client.impl.mixin.MouseHandlerImpl;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow @Final public Minecraft minecraft;

    @WrapOperation(method = "grabMouse", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(JIDD)V"))
    public void showMouse(long window, int cursorValue, double xPos, double yPos, Operation<Void> original) {
        if (CameraModeController.isOrthographic()) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        } else {
            original.call(window, cursorValue, xPos, yPos);
        }
    }

    @ModifyExpressionValue(method = "releaseMouse", at = @At(value = "CONSTANT", args = "intValue=212993"))
    public int showMouse(int original) {
        return CameraModeController.isOrthographic() ? GLFW.GLFW_CURSOR_HIDDEN : original;
    }

    @WrapMethod(method = "turnPlayer")
    public void proxyTurnPlayer(double movementTime, Operation<Void> original) {
        if (CameraModeController.isOrthographic()) {
            MouseHandlerImpl.byMouseMove((MouseHandler) (Object) this, minecraft, movementTime);
        } else {
            original.call(movementTime);
        }
    }
}
