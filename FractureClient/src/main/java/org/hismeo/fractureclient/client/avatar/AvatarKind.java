package org.hismeo.fractureclient.client.avatar;

import org.hismeo.fractureclient.client.avatar.skin.PlayerSkinSource;

/** Client render resource selected from authoritative gameplay avatar data. */
public enum AvatarKind {
    WILD(PlayerSkinSource.SkinLayout.CLASSIC),
    SILE(PlayerSkinSource.SkinLayout.SLIM);

    private final PlayerSkinSource.SkinLayout armLayout;

    AvatarKind(PlayerSkinSource.SkinLayout armLayout) {
        this.armLayout = armLayout;
    }

    /** The UV layout authored into this Avatar model; this never selects the Avatar identity. */
    public PlayerSkinSource.SkinLayout armLayout() {
        return armLayout;
    }
}
