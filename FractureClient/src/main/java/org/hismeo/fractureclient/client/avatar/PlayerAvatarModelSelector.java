package org.hismeo.fractureclient.client.avatar;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;

import java.util.Objects;

/**
 * Maps Minecraft's resolved wide/slim player model to the matching glTF mesh and UV layout.
 *
 * <p>This follows the same skin state used by vanilla PlayerRenderer, so online skin resolution can
 * switch an existing binding from its temporary default model without recreating the manager.</p>
 */
public final class PlayerAvatarModelSelector {
    public AvatarKind select(AbstractClientPlayer player) {
        Objects.requireNonNull(player, "player");
        return map(player.getSkin().model() == PlayerSkin.Model.SLIM);
    }

    static AvatarKind map(boolean slim) {
        return slim ? AvatarKind.SILE : AvatarKind.WILD;
    }
}
