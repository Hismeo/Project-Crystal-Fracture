package org.hismeo.nuquest.core.dialog.context.config.group;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.hismeo.crystallib.util.client.MinecraftUtil;

import static org.hismeo.crystallib.util.JsonUtil.tryGetFloat;

public record SoundGroup(SoundEvent soundEvent, float volume, float pitch) {
    public SoundGroup(String soundId, float volume, float pitch) {
        this(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.tryParse(soundId)), volume, pitch);
    }

    public static SoundGroup fromJson(JsonElement soundElement) {
        String soundId = null;
        float volume = 1.0f;
        float pitch = 1.0f;
        if (soundElement != null) {
            if (soundElement.isJsonPrimitive()) {
                soundId = soundElement.getAsString();
            } else if (soundElement.isJsonObject()) {
                JsonObject soundObject = soundElement.getAsJsonObject();
                soundId = soundObject.get("sound").getAsString();
                volume = tryGetFloat(soundObject, "volume", volume);
                pitch = tryGetFloat(soundObject, "pitch", pitch);
            }
            return new SoundGroup(soundId, volume, pitch);
        }
        return null;
    }

    public void playSound(Level level) {
        if (this.soundEvent == null) return;
        if (level.isClientSide) {
            LocalPlayer player = MinecraftUtil.getPlayer();
            level.playSound(player, player.blockPosition(), this.soundEvent, SoundSource.PLAYERS, this.volume, this.pitch);
        } else {
            // 服务端，待处理
        }
    }
}
