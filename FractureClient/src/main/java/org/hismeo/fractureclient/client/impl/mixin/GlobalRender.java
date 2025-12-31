package org.hismeo.fractureclient.client.impl.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystalfracture.CrystalFracture;

public class GlobalRender {
    private static final ResourceLocation MOUSE = CrystalFracture.packRL("textures/render/mouse.png");

    public static void mouseRender(Minecraft minecraft, GuiGraphics guiGraphics) {
        MouseHandler mouseHandler = minecraft.mouseHandler;
        int scale = (int) minecraft.getWindow().getGuiScale();
        guiGraphics.blit(MOUSE, (int) mouseHandler.xpos / scale, (int) mouseHandler.ypos / scale, 0, 0, 9, 16, 9, 16);
    }
}