package org.hismeo.crystallib.common.util;

public class TimeUtil {
    private static int faedTime = 10;

    private static float getProgress(int start) {
        return Math.min((System.currentTimeMillis() - start) / faedTime, 1);
    }
}
