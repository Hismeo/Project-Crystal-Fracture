package org.hismeo.fractureclient.mixin;

import net.minecraft.client.CameraType;
import org.hismeo.fractureclient.client.control.CameraModeController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CameraType.class)
public class CameraTypeMixin {
    @Inject(method = "isFirstPerson", at = @At("HEAD"), cancellable = true)
    public void hideFirstPersonContent(CallbackInfoReturnable<Boolean> cir) {
        if (CameraModeController.isOrthographic()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isMirrored", at = @At("HEAD"), cancellable = true)
    public void disableMirroredCamera(CallbackInfoReturnable<Boolean> cir) {
        if (CameraModeController.isOrthographic()) {
            cir.setReturnValue(false);
        }
    }
}
