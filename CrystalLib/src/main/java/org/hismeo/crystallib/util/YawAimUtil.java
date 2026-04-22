package org.hismeo.crystallib.util;

import net.minecraft.util.Mth;

public final class YawAimUtil {
    private static final double MIN_HORIZ_RAY_LEN2 = 1e-10;
    private static final double MIN_RAY_Y_ABS = 1e-4;
    private static final double MIN_AIM_LEN2 = 1e-8;
    private static final double MAX_INTERSECT_T = 512.0;

    private YawAimUtil() {}

    public static float computeAimYaw(
            double camX, double camY, double camZ,
            double playerX, double targetY, double playerZ,
            double rayX, double rayY, double rayZ
    ) {
        double rayHorizLen2 = rayX * rayX + rayZ * rayZ;
        if (rayHorizLen2 < MIN_HORIZ_RAY_LEN2) return Float.NaN;

        double aimX = rayX;
        double aimZ = rayZ;

        if (Math.abs(rayY) >= MIN_RAY_Y_ABS) {
            double t = (targetY - camY) / rayY;
            if (t > 0.0 && t <= MAX_INTERSECT_T) {
                double hitX = camX + rayX * t;
                double hitZ = camZ + rayZ * t;
                double hitDx = hitX - playerX;
                double hitDz = hitZ - playerZ;
                if (hitDx * hitDx + hitDz * hitDz >= MIN_AIM_LEN2) {
                    aimX = hitDx;
                    aimZ = hitDz;
                }
            }
        }

        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-aimX, aimZ)));
    }
}
