package org.hismeo.fractureclient.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Camera.class)
public class CameraMixin {
    //=====================================ORTHO CAMERA=====================================
    @ModifyVariable(method = "setup", at = @At(value = "HEAD"), ordinal = 1, argsOnly = true)
    public boolean noReverse(boolean original) {return false;}
    @ModifyVariable(method = "setup", at = @At(value = "HEAD"), ordinal = 0, argsOnly = true)
    public boolean alwaysDetached(boolean original) {return true;}
}
