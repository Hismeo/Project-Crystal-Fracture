package org.hismeo.crystallib.util;


import net.neoforged.fml.loading.FMLEnvironment;

public final class DevelopUtil {
    public static boolean isDev() {
        return !FMLEnvironment.production;
    }

    public static void devRun(Runnable runnable) {
        if (isDev()) runnable.run();
    }
}
