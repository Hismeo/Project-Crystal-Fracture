package org.hismeo.haikalathost.internal.content;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.HaikalatHost;
import org.hismeo.haikalathost.api.content.HaikalatAssetKind;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HaikalatContentRegistryTest {
    @Test
    void registrationIsDeclarativeNamespacedAndFreezes() {
        HaikalatContentRegistry registry = new HaikalatContentRegistry(() -> "example");
        var event = registry.registrationEvent();
        ResourceLocation sceneId =
                ResourceLocation.fromNamespaceAndPath("example", "haikalat/scenes/test.scene.json");

        event.namespaces().register("shared_assets");
        var registered = event.scenes().register(sceneId);

        assertEquals(HaikalatAssetKind.SCENE, registered.kind());
        assertEquals("example", registered.ownerModId());
        assertEquals(
                Set.of(HaikalatHost.MOD_ID, "example", "shared_assets"),
                registry.namespaces());
        assertThrows(
                IllegalArgumentException.class,
                () -> event.effects().register(sceneId));

        registry.freeze();

        assertEquals(
                Set.of(HaikalatHost.MOD_ID, "example", "shared_assets", "extension_only"),
                registry.createResourceCatalog(Set.of("extension_only"))
                        .mounts()
                        .keySet());
        assertThrows(
                IllegalStateException.class,
                () -> event.models().register(
                        ResourceLocation.fromNamespaceAndPath(
                                "example",
                                "haikalat/models/test.glb")));
        assertEquals(1, registry.assets().size());
    }

    @Test
    void crossNamespaceAssetsRequireAnExplicitDeclaration() {
        HaikalatContentRegistry registry = new HaikalatContentRegistry(() -> "example");
        var event = registry.registrationEvent();
        ResourceLocation shared =
                ResourceLocation.fromNamespaceAndPath(
                        "shared_assets",
                        "haikalat/scenes/shared.scene.json");

        assertThrows(IllegalArgumentException.class, () -> event.scenes().register(shared));

        event.namespaces().register("shared_assets");
        var registered = event.scenes().register(shared);
        assertEquals("example", registered.ownerModId());
    }
}
