package org.hismeo.crystallib.util;

import net.minecraftforge.fml.loading.FMLEnvironment;

public class DevelopUtil {
    public static boolean isDev() {
        return !FMLEnvironment.production;
    }

    public static void devRun(Runnable runnable) {
        if (isDev()) runnable.run();
    }
}
