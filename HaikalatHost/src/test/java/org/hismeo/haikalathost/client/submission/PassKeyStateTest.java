package org.hismeo.haikalathost.client.submission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassKeyStateTest {
    @Test
    void onlyWorldGeometryPassesEnableCoarseBackFaceCulling() {
        assertTrue(PassKey.WORLD_OPAQUE.cullsBackFaces());
        assertTrue(PassKey.WORLD_CUTOUT.cullsBackFaces());
        assertTrue(PassKey.WORLD_TRANSLUCENT.cullsBackFaces());

        assertFalse(PassKey.ENTITY_OPAQUE.cullsBackFaces());
        assertFalse(PassKey.SKY.cullsBackFaces());
        assertFalse(PassKey.UI.cullsBackFaces());
    }
}
