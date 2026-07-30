package org.hismeo.haikalathost.api.content;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class HaikalatAssetStatusTest {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("example", "haikalat/scenes/test.scene.json");

    @Test
    void lastKnownGoodRequiresAPreparedGeneration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new HaikalatAssetStatus(
                        ID,
                        HaikalatAssetKind.SCENE,
                        "example",
                        HaikalatAssetState.FAILED,
                        2L,
                        -1L,
                        true,
                        "scene_prepare_failed",
                        "failed"));
    }
}
