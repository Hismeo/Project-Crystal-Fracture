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
import org.hismeo.nuquest.core.IData;

import static org.hismeo.crystallib.util.JsonUtil.tryGetFloat;

public record SoundGroup(SoundEvent soundEvent, Float volume, Float pitch) implements IData<SoundGroup> {
    public SoundGroup(String soundId, Float volume, Float pitch) {
        this(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.tryParse(soundId)), volume, pitch);
    }

    public static SoundGroup fromJson(JsonElement soundElement) {
        String soundId = null;
        Float volume = null;
        Float pitch = null;
        if (soundElement != null) {
            if (soundElement.isJsonPrimitive()) {
                soundId = soundElement.getAsString();
            } else if (soundElement.isJsonObject()) {
                JsonObject soundObject = soundElement.getAsJsonObject();
                soundId = soundObject.get("sound").getAsString();
                volume = tryGetFloat(soundObject, "volume");
                pitch = tryGetFloat(soundObject, "pitch");
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


    @Override
    public SoundGroup mergeData(SoundGroup newData) {
        if (newData == null || newData.allEmpty()) return this;
        return new SoundGroup(
                choose(newData.soundEvent, soundEvent),
                choose(newData.volume, volume),
                choose(newData.pitch, pitch)
        );
    }

    @Override
    public boolean anyEmpty() {
        return anyEmpty(soundEvent, volume, pitch);
    }

    @Override
    public boolean allEmpty() {
        return allEmpty(soundEvent, volume, pitch);
    }
}
