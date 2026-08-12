package org.hismeo.actionguide.api.event;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.ResourceIds;

public final class CueStateTypes {
    public static final ResourceLocation ATTACK = type("attack");
    public static final ResourceLocation INPUT = type("input");
    public static final ResourceLocation INVULNERABLE = type("invulnerable");
    public static final ResourceLocation SUPER_ARMOR = type("super_armor");
    public static final ResourceLocation CANCEL = type("cancel");

    private CueStateTypes() {
    }

    private static ResourceLocation type(String path) {
        return ResourceIds.parse("action_guide:" + path, "built-in state type");
    }
}
