package org.hismeo.crystallib.client.util.render;

import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.hismeo.crystallib.api.TrailSaver;

import static net.minecraft.util.FastColor.ARGB32.color;

public class BackGroundUtil {
    private static final PanoramaRenderer panoramaRenderer = new PanoramaRenderer(new CubeMap(new ResourceLocation("textures/gui/title/background/panorama")));

    public static void panoramaUtil(Screen screen, float partialTick, ClientLevel level, GuiGraphics guiGraphics, int width, int height){
        if (!(screen instanceof RealmsNotificationsScreen)) {
            if (level == null){
                panoramaRenderer.render(partialTick, 1.0f);
            }
            if (!(screen instanceof TitleScreen)){
                Minecraft.getInstance().gameRenderer.loadEffect(new ResourceLocation("shaders/post/blur.json"));
//                guiGraphics.fillGradient(0, 0, width, height, color(64,0,0,0), color(32,0,0,0));
            }
        }
    }

    public static PanoramaRenderer getPanoramaRenderer() {
        return panoramaRenderer;
    }
}
