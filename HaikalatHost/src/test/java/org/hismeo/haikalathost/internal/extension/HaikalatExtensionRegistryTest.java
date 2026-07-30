package org.hismeo.haikalathost.internal.extension;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.api.client.advanced.HaikalatRenderExtension;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HaikalatExtensionRegistryTest {
    @Test
    void preservesRegistrationOrderAndRejectsDuplicateIds() {
        HaikalatExtensionRegistry registry =
                new HaikalatExtensionRegistry(() -> "example");
        var event = registry.registrationEvent();
        HaikalatRenderExtension first = frame -> {
        };
        HaikalatRenderExtension second = frame -> {
        };

        event.register(id("first"), first);
        event.register(id("second"), second);
        assertThrows(
                IllegalArgumentException.class,
                () -> event.register(id("first"), frame -> {
                }));
        registry.freeze();

        assertEquals(
                java.util.List.of(id("first"), id("second")),
                registry.registrations().stream()
                        .map(HaikalatExtensionRegistry.Registration::id)
                        .toList());
        assertEquals(java.util.Set.of("example"), registry.namespaces());
    }

    @Test
    void requiresOwnerNamespaceAndFreezesAllExistingRegistrars() {
        HaikalatExtensionRegistry registry =
                new HaikalatExtensionRegistry(() -> "example");
        var event = registry.registrationEvent();

        assertThrows(
                IllegalArgumentException.class,
                () -> event.register(
                        ResourceLocation.fromNamespaceAndPath("other", "main"),
                        frame -> {
                        }));

        registry.freeze();

        assertThrows(IllegalStateException.class, registry::registrationEvent);
        assertThrows(
                IllegalStateException.class,
                () -> event.register(id("late"), frame -> {
                }));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("example", path);
    }
}
