package org.hismeo.fractureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TabNavigationBar.class)
public class TabNavigationBarMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/Iterator;hasNext()Z"))
    public void enableBlendTab(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci){
        RenderSystem.enableBlend();
    }

    @Inject(method = "render", at = @At(value = "TAIL"))
    public void disableBlendTab(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci){
        RenderSystem.disableBlend();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    public void cancelFill(GuiGraphics instance, int minX, int minY, int maxX, int maxY, int color, Operation<Void> original){
        if (false) original.call(instance, minX, minY, maxX, maxY, color);
    }
}
