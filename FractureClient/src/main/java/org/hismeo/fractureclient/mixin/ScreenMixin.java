package org.hismeo.fractureclient.mixin;

import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.hismeo.crystallib.client.util.render.BackGroundUtil;
import org.hismeo.fractureclient.impl.FadeGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.Arrays;


@Mixin(Screen.class)
public class ScreenMixin {
    private static final Class<?>[] panoramaExcludeScreen = {RealmsNotificationsScreen.class};
    @Shadow
    @Nullable
    protected Minecraft minecraft;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Inject(method = "render", at = @At(value = "HEAD"))
    public void panoramaBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Screen currentScreen = (Screen) (Object) this;
        if (Arrays.asList(panoramaExcludeScreen).contains(currentScreen.getClass())) return;

        BackGroundUtil.panoramaUtil(currentScreen, partialTick, this.minecraft.level, guiGraphics, this.width, this.height);
        if (currentScreen instanceof TitleScreen titleScreen) {
            float f1 = ((FadeGetter) titleScreen).getFade();
            titleScreen.logoRenderer.renderLogo(guiGraphics, this.width, f1);
        }
    }

    @Inject(method = "renderDirtBackground", at = @At(value = "HEAD"), cancellable = true)
    public void cancelDirt(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (this.minecraft.level == null) ci.cancel();
    }
}
