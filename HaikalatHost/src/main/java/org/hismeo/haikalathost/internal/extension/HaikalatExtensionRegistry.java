package org.hismeo.haikalathost.internal.extension;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModLoadingContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatRenderExtension;
import org.hismeo.haikalathost.api.client.advanced.event.RegisterHaikalatExtensionsEvent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Collects advanced extensions in deterministic mod-event order and freezes before rendering.
 */
public final class HaikalatExtensionRegistry {
    private static final HaikalatExtensionRegistry INSTANCE = new HaikalatExtensionRegistry();

    private final Map<ResourceLocation, Registration> registrations = new LinkedHashMap<>();
    private final Supplier<String> ownerModId;
    private boolean frozen;

    public HaikalatExtensionRegistry() {
        this(() -> ModLoadingContext.get().getActiveContainer().getModId());
    }

    HaikalatExtensionRegistry(Supplier<String> ownerModId) {
        this.ownerModId = Objects.requireNonNull(ownerModId, "ownerModId");
    }

    public static HaikalatExtensionRegistry instance() {
        return INSTANCE;
    }

    public synchronized RegisterHaikalatExtensionsEvent registrationEvent() {
        requireMutable();
        return new RegisterHaikalatExtensionsEvent(this::register);
    }

    public synchronized void freeze() {
        frozen = true;
    }

    public synchronized boolean frozen() {
        return frozen;
    }

    public synchronized List<Registration> registrations() {
        if (!frozen) {
            throw new IllegalStateException("Haikalat extension registration is not frozen");
        }
        return List.copyOf(registrations.values());
    }

    public synchronized Set<String> namespaces() {
        if (!frozen) {
            throw new IllegalStateException("Haikalat extension registration is not frozen");
        }
        Set<String> result = new LinkedHashSet<>();
        registrations.keySet().stream()
                .map(ResourceLocation::getNamespace)
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private synchronized void register(
            ResourceLocation id,
            HaikalatRenderExtension extension
    ) {
        requireMutable();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(extension, "extension");
        String owner = Objects.requireNonNull(
                ownerModId.get(),
                "ownerModId supplier returned null");
        if (owner.isBlank()) {
            throw new IllegalStateException("Active mod id must not be blank");
        }
        if (!owner.equals(id.getNamespace())) {
            throw new IllegalArgumentException(
                    "Mod " + owner + " cannot register Haikalat extension " + id
                            + "; extension ids must use the registering mod's namespace");
        }

        Registration candidate = new Registration(id, owner, extension);
        Registration existing = registrations.putIfAbsent(id, candidate);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Haikalat extension " + id + " is already registered by mod "
                            + existing.ownerModId() + "; duplicate declaration by mod " + owner);
        }
    }

    private void requireMutable() {
        if (frozen) {
            throw new IllegalStateException("Haikalat extension registration is already frozen");
        }
    }

    public record Registration(
            ResourceLocation id,
            String ownerModId,
            HaikalatRenderExtension extension
    ) {
        public Registration {
            id = Objects.requireNonNull(id, "id");
            ownerModId = Objects.requireNonNull(ownerModId, "ownerModId");
            extension = Objects.requireNonNull(extension, "extension");
        }
    }
}
