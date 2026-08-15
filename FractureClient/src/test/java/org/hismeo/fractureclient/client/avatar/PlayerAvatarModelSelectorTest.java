package org.hismeo.fractureclient.client.avatar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlayerAvatarModelSelectorTest {
    @Test
    void mapsVanillaWideAndSlimModelsToMatchingAvatarMeshes() {
        assertEquals(AvatarKind.WILD, PlayerAvatarModelSelector.map(false));
        assertEquals(AvatarKind.SILE, PlayerAvatarModelSelector.map(true));
    }
}
