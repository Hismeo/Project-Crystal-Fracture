package org.hismeo.fractureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    //=====================================OVERLOOK CAMERA=====================================
    @ModifyVariable(method = "setup", at = @At(value = "HEAD"), ordinal = 1, argsOnly = true)
    public boolean noReverse(boolean original) {return false;}
    @ModifyVariable(method = "setup", at = @At(value = "HEAD"), ordinal = 0, argsOnly = true)
    public boolean alwaysDetached(boolean original) {return true;}
    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    public void staticCamera(Camera instance, double x, double y, double z, Operation<Void> original) {
//        setPosition(-19, 65+eyeHeight, 229);
        original.call(instance,x,y,z);
    }
}
