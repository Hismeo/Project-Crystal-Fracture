package org.hismeo.fractureclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.hismeo.fractureclient.client.impl.mixin.MouseHandlerImpl;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin implements MouseHandlerImpl {
    @Shadow @Final public Minecraft minecraft;

    @WrapOperation(method = "grabMouse", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(JIDD)V"))
    public void showMouse(long window, int cursorValue, double xPos, double yPos, Operation<Void> original) {
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    @ModifyExpressionValue(method = "releaseMouse", at = @At(value = "CONSTANT", args = "intValue=212993"))
    public int showMouse(int original) {
        return GLFW.GLFW_CURSOR_HIDDEN;
    }

    @WrapMethod(method = "turnPlayer")
    public void proxyTurnPlayer(double movementTime, Operation<Void> original) {
        this.byMouseMove((MouseHandler) (Object) this, minecraft, movementTime);
    }
}
