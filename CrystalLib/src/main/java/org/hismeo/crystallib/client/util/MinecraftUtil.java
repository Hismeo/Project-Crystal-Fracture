package org.hismeo.crystallib.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

public class MinecraftUtil {
    public static Minecraft getMinecraft(){
        return Minecraft.getInstance();
    }

    public static GameRenderer getGameRenderer(){
        return getMinecraft().gameRenderer;
    }
}
