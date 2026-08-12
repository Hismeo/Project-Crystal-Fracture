package org.hismeo.actionguide.api.event;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.ResourceIds;

public final class CueEventTypes {
    public static final ResourceLocation SOUND = type("sound");
    public static final ResourceLocation VFX = type("vfx");
    public static final ResourceLocation PROJECTILE = type("projectile");
    public static final ResourceLocation CAMERA_SHAKE = type("camera_shake");
    public static final ResourceLocation CUSTOM = type("custom");

    private CueEventTypes() {
    }

    private static ResourceLocation type(String path) {
        return ResourceIds.parse("action_guide:" + path, "built-in event type");
    }
}
