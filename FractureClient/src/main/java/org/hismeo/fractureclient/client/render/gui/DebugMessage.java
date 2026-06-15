package org.hismeo.fractureclient.client.render.gui;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

public final class DebugMessage {
    private static final int LINE_HEIGHT = 10;
    private static final int DEFAULT_COLOR = 0xFFFFFFFF;
    private static final ObjectArrayList<String> LIST = new ObjectArrayList<>();

    private DebugMessage() {}

    public static void add(String message) {
        if (message == null || message.isBlank()) return;
        LIST.add(message);
    }

    public static void add(String pattern, Object... args) {
        add(String.format(Locale.ROOT, pattern, args));
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        for (int i = 0; i < LIST.size(); i++) {
            guiGraphics.drawString(minecraft.font, LIST.get(i), 0, i * LINE_HEIGHT, DEFAULT_COLOR);
        }
        LIST.clear();
    }

    public static void clear() {
        LIST.clear();
    }

    public static int size() {
        return LIST.size();
    }
}
