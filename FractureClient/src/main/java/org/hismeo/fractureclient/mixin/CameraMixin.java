package org.hismeo.fractureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import org.hismeo.fractureclient.client.control.CameraModeController;
import org.hismeo.fractureclient.client.impl.mixin.CameraImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    //=====================================OVERLOOK CAMERA=====================================
    @ModifyVariable(method = "setup", at = @At(value = "HEAD"), ordinal = 1, argsOnly = true)
    public boolean noReverse(boolean original) {
        return CameraModeController.isOrthographic() ? false : original;
    }

    @ModifyVariable(method = "setup", at = @At(value = "HEAD"), ordinal = 0, argsOnly = true)
    public boolean alwaysDetached(boolean original) {
        return CameraModeController.isOrthographic() ? true : original;
    }

    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;move(FFF)V"))
    public void dontMove(Camera instance, float zoom, float dy, float dx, Operation<Void> original) {
        if (!CameraModeController.isOrthographic()) {
            original.call(instance, zoom, dy, dx);
        }
    }

    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    public void staticCamera(Camera instance, double x, double y, double z, Operation<Void> original) {
        if (CameraModeController.isOrthographic()) {
            CameraImpl.deadZone(instance, x, y, z);
        } else {
            original.call(instance, x, y, z);
        }
    }
}
