package org.hismeo.crystalfracture.init;

import org.hismeo.crystalfracture.CrystalFracture;

public class TranslateKeyInit {


    public static String key(String type, String value) {
        return "%s.%s.%s".formatted(type, CrystalFracture.MODID, value);
    }
}
