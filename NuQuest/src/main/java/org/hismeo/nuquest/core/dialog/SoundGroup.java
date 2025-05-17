package org.hismeo.nuquest.core.dialog;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.hismeo.crystallib.util.client.MinecraftUtil;

public class SoundGroup {
    private final SoundEvent soundEvent;
    private final float volume;
    private final float pitch;

    public SoundGroup(String soundId, float volume, float pitch) {
        this(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.tryParse(soundId)), volume, pitch);
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

    public void playSound(Level level){
        if (this.getSoundEvent() == null) return;
        if (level.isClientSide){
            LocalPlayer player = MinecraftUtil.getPlayer();
            level.playSound(player, player.blockPosition(), this.soundEvent, SoundSource.PLAYERS, this.volume, this.pitch);
        } else {
            // 服务端，待处理
        }
    }
}
