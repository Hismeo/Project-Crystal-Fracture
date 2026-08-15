package org.hismeo.fractureclient.client.avatar.skin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Reads Minecraft skin state without exposing Haikalat types. */
public final class PlayerSkinProvider {
    private final Set<String> downloadedUrls = new HashSet<>();

    public PlayerSkinSource current(AbstractClientPlayer player) {
        Objects.requireNonNull(player, "player");
        PlayerSkin skin = player.getSkin();
        boolean downloaded = isDownloaded(skin.textureUrl());
        return new PlayerSkinSource(
                player.getUUID(),
                skin.texture(),
                skin.model() == PlayerSkin.Model.SLIM
                        ? PlayerSkinSource.SkinLayout.SLIM
                        : PlayerSkinSource.SkinLayout.CLASSIC,
                Objects.hash(skin.texture(), skin.textureUrl(), skin.model()),
                skin.textureUrl(),
                downloaded);
    }

    private boolean isDownloaded(String textureUrl) {
        if (textureUrl == null || downloadedUrls.contains(textureUrl)) {
            return true;
        }
        if (!Files.isRegularFile(PlayerSkinCache.downloadedSkinPath(
                Minecraft.getInstance(),
                textureUrl))) {
            return false;
        }
        downloadedUrls.add(textureUrl);
        return true;
    }
}
