package org.hismeo.fractureclient.client.init;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.hismeo.crystalfracture.init.TranslateKeyInit;

public class KeyInit {
    private static final String IN_GAME = "key.categories.in_game";
    private static final String FRACTURE_CLIENT = "key.categories.fracture_client";
    public static final KeyMapping ROTATE_CAMERA = new KeyMapping(TranslateKeyInit.ROTATE_CAMERA, KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, IN_GAME);
    public static final KeyMapping ROOM_EDITOR = new KeyMapping(
            "key.fracture_client.room_editor",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_F6,
            FRACTURE_CLIENT
    );
    public static final KeyMapping ROOM_EDITOR_CANCEL = new KeyMapping(
            "key.fracture_client.room_editor_cancel",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_F7,
            FRACTURE_CLIENT
    );
    public static final KeyMapping ROOM_EDITOR_UNDO = new KeyMapping(
            "key.fracture_client.room_editor_undo",
            KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_Z,
            FRACTURE_CLIENT
    );
    public static final KeyMapping ROOM_EDITOR_HEIGHT = new KeyMapping(
            "key.fracture_client.room_editor_height",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_H,
            FRACTURE_CLIENT
    );
}
