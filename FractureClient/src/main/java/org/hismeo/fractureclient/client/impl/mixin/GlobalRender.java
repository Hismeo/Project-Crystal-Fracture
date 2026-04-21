package org.hismeo.fractureclient.client.impl.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
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
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 3000);
        guiGraphics.blit(MOUSE, (int) mouseHandler.xpos / scale, (int) mouseHandler.ypos / scale, 0, 0, 9, 16, 9, 16);
        pose.popPose();
    }

    public static void globalRender(Minecraft minecraft, DeltaTracker deltaTracker, boolean renderLevel, GuiGraphics guiGraphics, Window window) {
        mouseRender(minecraft, guiGraphics);
    }
}