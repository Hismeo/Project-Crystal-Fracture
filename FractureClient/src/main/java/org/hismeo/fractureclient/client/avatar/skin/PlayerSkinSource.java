package org.hismeo.fractureclient.client.avatar.skin;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record PlayerSkinSource(
        UUID playerId,
        ResourceLocation texture,
        SkinLayout layout,
        long revision,
        String textureUrl,
        boolean downloaded
) {
    public PlayerSkinSource {
        playerId = Objects.requireNonNull(playerId, "playerId");
        texture = Objects.requireNonNull(texture, "texture");
        layout = Objects.requireNonNull(layout, "layout");
    }

    public enum SkinLayout {
        CLASSIC,
        SLIM
    }
}
