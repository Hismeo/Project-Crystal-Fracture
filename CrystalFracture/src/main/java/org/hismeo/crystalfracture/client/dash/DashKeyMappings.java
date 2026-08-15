package org.hismeo.crystalfracture.client.dash;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.hismeo.crystalfracture.init.TranslateKeyInit;

public final class DashKeyMappings {
    public static final KeyMapping DASH = new KeyMapping(
            TranslateKeyInit.DASH,
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            TranslateKeyInit.CLIENT_CATEGORY);

    private DashKeyMappings() {
    }
}
