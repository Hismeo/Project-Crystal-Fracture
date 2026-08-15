package org.hismeo.crystalfracture.init;

import org.hismeo.crystalfracture.CrystalFracture;

/**
 * TODO
 * @see org.hismeo.crystallib.api.TranslateKey
 */
public class TranslateKeyInit {

    //=====================================SCREEN=====================================
    public static String TITLE_SCREEN = screen("title_screen");

    //=====================================KEY MAPPING=====================================
    public static String CLIENT_CATEGORY = "key.categories.crystal_fracture";
    public static String ROTATE_CAMERA = keyMapping("rotate_camera");
    public static String DASH = keyMapping("dash");

    public static String keyMapping(String value) {
        return key("key", value);
    }

    public static String screen(String value) {
        return key("screen", value);
    }

    public static String key(String type, String value) {
        return "%s.%s.%s".formatted(type, CrystalFracture.MODID, value);
    }
}
