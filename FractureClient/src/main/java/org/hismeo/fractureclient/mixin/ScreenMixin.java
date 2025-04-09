package org.hismeo.fractureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
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
    public float partialTick;
    @Shadow
    @Nullable
    protected Minecraft minecraft;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Inject(method = "render", at = @At(value = "HEAD"))
    public void panoramaBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        this.partialTick= partialTick;
        Screen currentScreen = (Screen) (Object) this;
        if (Arrays.asList(panoramaExcludeScreen).contains(currentScreen.getClass())) return;

        BackGroundUtil.panoramaUtil(currentScreen, partialTick, this.minecraft.level, guiGraphics, this.width, this.height);
//        if (currentScreen instanceof TitleScreen titleScreen) {
//            float f1 = ((FadeGetter) titleScreen).getFade();
//            titleScreen.logoRenderer.renderLogo(guiGraphics, this.width, f1);
//        }
    }

    @WrapOperation(method = "renderDirtBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIFFIIII)V"))
    public void cancelDirt(GuiGraphics guiGraphics, ResourceLocation atlasLocation, int x, int y, int blitOffset, float uOffset, float vOffset, int uWidth, int vHeight, int textureWidth, int textureHeight, Operation<Void> original) {
        if (false) {original.call(guiGraphics, atlasLocation, x, y, blitOffset, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);}
        else {
            Screen currentScreen = (Screen) (Object) this;
            if (Arrays.asList(panoramaExcludeScreen).contains(currentScreen.getClass())) return;

            BackGroundUtil.panoramaUtil(currentScreen, partialTick, this.minecraft.level, guiGraphics, this.width, this.height);
        }
    }

    @WrapOperation(method = "renderBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fillGradient(IIIIII)V"))
    public void cancelDirt(GuiGraphics instance, int x1, int y1, int x2, int y2, int colorFrom, int colorTo, Operation<Void> original) {
        if (false) {original.call(instance, x1, y1, x2, y2, colorFrom, colorTo);}
    }
}
