package org.hismeo.crystallib.client.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystallib.client.util.render.shader.BlurUtil;

import static org.hismeo.crystallib.client.util.MinecraftUtil.*;


public class BackGroundUtil {
    private static final PanoramaRenderer panoramaRenderer = new PanoramaRenderer(new CubeMap(new ResourceLocation("textures/gui/title/background/panorama")));

    public static void panoramaUtil(Screen screen, float partialTick, ClientLevel level, GuiGraphics guiGraphics, int width, int height){
        if (level == null){
            panoramaRenderer.render(partialTick, 1.0f);
        }
        if (!(screen instanceof TitleScreen)){
//            renderMenuBackground(guiGraphics, screen);
        }
    }

    public static void renderBlurredBackground(float pPartialTick) {
        BlurUtil.loadBlurEffect();
//        getGameRenderer().loadEffect(EffectType.FADE_IN_BLUR);
        BlurUtil.processBlurEffect(pPartialTick);
        getMainRenderTarget().bindWrite(false);
    }

    protected static void renderMenuBackground(GuiGraphics guiGraphics, Screen screen) {
        RenderSystem.enableBlend();
        guiGraphics.blit(new ResourceLocation("textures/gui/menu_background.png"), 0, 0, 0, 0, 0, screen.width, screen.height, 32, 32);
        RenderSystem.disableBlend();
    }

    public static PanoramaRenderer getPanoramaRenderer() {
        return panoramaRenderer;
    }
}
