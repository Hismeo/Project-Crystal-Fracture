package org.hismeo.fractureclient.mixin;

import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CameraType.class)
public class CamaraTypeMixin {
    //=====================================ORTHO CAMERA=====================================
    @Inject(method = "isFirstPerson", at = @At("HEAD"), cancellable = true)
    public void noFirst(CallbackInfoReturnable<Boolean> cir) {cir.setReturnValue(false);}
    @Inject(method = "isMirrored", at = @At("HEAD"), cancellable = true)
    public void noMirror(CallbackInfoReturnable<Boolean> cir) {cir.setReturnValue(false);}
}
