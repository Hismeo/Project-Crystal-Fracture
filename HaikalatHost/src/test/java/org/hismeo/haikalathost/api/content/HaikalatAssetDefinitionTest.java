package org.hismeo.haikalathost.api.content;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HaikalatAssetDefinitionTest {
    private static final ResourceLocation ASSET =
            ResourceLocation.fromNamespaceAndPath("example", "scenes/test.scene.json");

    @Test
    void compatibilityConstructorUsesAssetNamespaceAsOwner() {
        HaikalatAssetDefinition definition =
                new HaikalatAssetDefinition(ASSET, HaikalatAssetKind.SCENE);

        assertEquals("example", definition.ownerModId());
    }

    @Test
    void explicitOwnerMustNotBeBlank() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new HaikalatAssetDefinition(ASSET, HaikalatAssetKind.SCENE, " "));
    }
}
