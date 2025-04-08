package org.hismeo.fractureclient.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import org.hismeo.fractureclient.impl.FadeGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen implements FadeGetter {
    private float fade;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"), locals = LocalCapture.CAPTURE_FAILSOFT)
    public void setFade(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci, float f, float f1){
        fade = f1;
    }

    @Override
    public float getFade() {
        return fade;
    }
}
