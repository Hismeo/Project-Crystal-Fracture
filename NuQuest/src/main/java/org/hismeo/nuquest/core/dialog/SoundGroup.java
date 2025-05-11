package org.hismeo.nuquest.core.dialog;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;

public class SoundGroup {
    private final SoundEvent soundEvent;
    private final float volume;
    private final float pitch;

    public SoundGroup(String soundId, float volume, float pitch) {
        this(ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId)), volume, pitch);
    }

    public SoundGroup(SoundEvent soundEvent, float volume, float pitch) {
        this.soundEvent = soundEvent;
        this.volume = volume;
        this.pitch = pitch;
    }

    public SoundEvent getSoundEvent() {
        return soundEvent;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }
}
