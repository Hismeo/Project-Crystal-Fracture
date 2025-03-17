package org.hismeo.crystallib.util;

import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;

public class DevelopUtil {
    public static boolean isDev() {
        return !FMLLoader.isProduction();
    }
}
