package org.hismeo.fractureclient.mixin;

import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.widget.ScrollPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ScrollPanel.class, remap = false)
public class ScrollPanelMixin {
    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    public void cancelBackground(GuiGraphics guiGraphics, Tesselator tess, float partialTick, CallbackInfo ci){
        ci.cancel();
    }
}
