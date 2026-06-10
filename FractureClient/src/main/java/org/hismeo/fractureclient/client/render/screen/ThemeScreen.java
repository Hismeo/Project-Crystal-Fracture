package org.hismeo.fractureclient.client.render.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class ThemeScreen extends Screen {
    public ThemeScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0 ,width, height, 0xFF1E1E1E);
//        guiGraphics.fill(15, 15 ,width - 15, height - 15, );
        MultiBufferSource.BufferSource bufferSource = guiGraphics.bufferSource();
        PoseStack pose = guiGraphics.pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        PoseStack.Pose last = pose.last();
        consumer.addVertex(last, width / 4f, 0, 0).setColor(0xFFA0A0A0);
        consumer.addVertex(last, width / 4f - 30, 0, 0).setColor(0xFFA0A0A0);
        consumer.addVertex(last, width / 4f *2 - 30, height, 0).setColor(0xFFA0A0A0);
        consumer.addVertex(last, width / 4f *2, height, 0).setColor(0xFFA0A0A0);
        bufferSource.endBatch();
    }
}
