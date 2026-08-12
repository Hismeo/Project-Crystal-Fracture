package org.hismeo.actionguide.internal.definition;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.ResourceIds;
import org.hismeo.actionguide.api.event.CueEventTypes;
import org.hismeo.actionguide.api.event.CueStateTypes;

import java.util.Map;
import java.util.Set;

public final class CueTypes {
    public static final ResourceLocation SOUND = CueEventTypes.SOUND;
    public static final ResourceLocation VFX = CueEventTypes.VFX;
    public static final ResourceLocation PROJECTILE = CueEventTypes.PROJECTILE;
    public static final ResourceLocation CAMERA_SHAKE = CueEventTypes.CAMERA_SHAKE;
    public static final ResourceLocation CUSTOM_EVENT = CueEventTypes.CUSTOM;
    public static final ResourceLocation ATTACK = CueStateTypes.ATTACK;
    public static final ResourceLocation INPUT = CueStateTypes.INPUT;
    public static final ResourceLocation INVULNERABLE = CueStateTypes.INVULNERABLE;
    public static final ResourceLocation SUPER_ARMOR = CueStateTypes.SUPER_ARMOR;
    public static final ResourceLocation CANCEL = CueStateTypes.CANCEL;

    private static final Map<String, ResourceLocation> BUILT_INS = Map.ofEntries(
            Map.entry("sound", SOUND), Map.entry("vfx", VFX), Map.entry("projectile", PROJECTILE),
            Map.entry("camera_shake", CAMERA_SHAKE), Map.entry("custom", CUSTOM_EVENT),
            Map.entry("attack", ATTACK), Map.entry("input", INPUT), Map.entry("invulnerable", INVULNERABLE),
            Map.entry("super_armor", SUPER_ARMOR), Map.entry("cancel", CANCEL)
    );
    private static final Set<String> EVENT_NAMES = Set.of("sound", "vfx", "projectile", "camera_shake", "custom");
    private static final Set<String> STATE_NAMES = Set.of("attack", "input", "invulnerable", "super_armor", "cancel");

    private CueTypes() {
    }

    public static ResourceLocation event(String value) {
        return parse(value, EVENT_NAMES, "event");
    }

    public static ResourceLocation state(String value) {
        return parse(value, STATE_NAMES, "state");
    }

    private static ResourceLocation parse(String value, Set<String> builtIns, String label) {
        if (builtIns.contains(value)) {
            return BUILT_INS.get(value);
        }
        if (!value.contains(":")) {
            throw new IllegalArgumentException("custom " + label + " type must be namespaced: " + value);
        }
        return ResourceIds.parse(value, label + " type");
    }

}
