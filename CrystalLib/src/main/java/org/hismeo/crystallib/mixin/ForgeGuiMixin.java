package org.hismeo.crystallib.mixin;

import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ForgeGui.class, remap = false)
public class ForgeGuiMixin {
    @Redirect(method = "renderHealth", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    public float maxHealth(float a, float b){
        return a;
    }
}
