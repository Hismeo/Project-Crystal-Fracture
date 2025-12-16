package org.hismeo.fractureclient.client.init;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.hismeo.crystalfracture.init.TranslateKeyInit;

public class KeyInit {
    public static final KeyMapping ROTATE_CAMERA = new KeyMapping(TranslateKeyInit.ROTATE_CAMERA, KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, "");
}
