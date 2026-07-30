package org.hismeo.haikalathost.internal.content;

import com.kaleblangley.haikalat.subsystems.resources.ResourceCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModLoadingContext;
import org.hismeo.haikalathost.HaikalatHost;
import org.hismeo.haikalathost.api.content.HaikalatAssetDefinition;
import org.hismeo.haikalathost.api.content.HaikalatAssetKind;
import org.hismeo.haikalathost.api.content.HaikalatAssetRegistrar;
import org.hismeo.haikalathost.api.event.RegisterHaikalatContentEvent;
import org.hismeo.haikalathost.internal.resource.MinecraftResourceSource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Collects immutable declarations before Minecraft creates the client resource manager.
 */
public final class HaikalatContentRegistry {
    private static final HaikalatContentRegistry INSTANCE = new HaikalatContentRegistry();

    private final Map<ResourceLocation, HaikalatAssetDefinition> assets = new LinkedHashMap<>();
    private final Set<String> dependencyNamespaces = new LinkedHashSet<>();
    private final Supplier<String> ownerModId;
    private boolean frozen;

    public HaikalatContentRegistry() {
        this(() -> ModLoadingContext.get().getActiveContainer().getModId());
    }

    HaikalatContentRegistry(Supplier<String> ownerModId) {
        this.ownerModId = Objects.requireNonNull(ownerModId, "ownerModId");
    }

    public static HaikalatContentRegistry instance() {
        return INSTANCE;
    }

    public synchronized RegisterHaikalatContentEvent registrationEvent() {
        requireMutable();
        return new RegisterHaikalatContentEvent(this::registrar, this::registerNamespace);
    }

    public synchronized void freeze() {
        frozen = true;
    }

    public synchronized boolean frozen() {
        return frozen;
    }

    public synchronized List<HaikalatAssetDefinition> assets() {
        return List.copyOf(assets.values());
    }

    public synchronized Set<String> namespaces() {
        Set<String> namespaces = new LinkedHashSet<>();
        namespaces.add(HaikalatHost.MOD_ID);
        assets.keySet().stream()
                .map(ResourceLocation::getNamespace)
                .forEach(namespaces::add);
        namespaces.addAll(dependencyNamespaces);
        return Set.copyOf(namespaces);
    }

    public synchronized ResourceCatalog createResourceCatalog() {
        return createResourceCatalog(Set.of());
    }

    /**
     * Builds the catalog after adding namespaces owned by advanced render extensions.
     *
     * <p>An extension id is itself a declaration that its mod namespace may be read through the
     * shared Minecraft resource bridge. Requiring an otherwise unused content declaration just to
     * mount that namespace would make the advanced API needlessly surprising.</p>
     */
    public synchronized ResourceCatalog createResourceCatalog(
            Set<String> additionalNamespaces
    ) {
        if (!frozen) {
            throw new IllegalStateException("Haikalat content registration is not frozen");
        }
        Objects.requireNonNull(additionalNamespaces, "additionalNamespaces");
        Set<String> mountedNamespaces = new LinkedHashSet<>(namespaces());
        for (String namespace : additionalNamespaces) {
            String normalized = com.kaleblangley.haikalat.subsystems.resources.AssetId
                    .of(Objects.requireNonNull(namespace, "namespace"), "probe")
                    .namespace();
            mountedNamespaces.add(normalized);
        }
        ResourceCatalog.Builder builder = ResourceCatalog.builder();
        for (String namespace : mountedNamespaces) {
            builder.mount(
                    namespace,
                    new MinecraftResourceSource(
                            namespace,
                            () -> Minecraft.getInstance().getResourceManager()));
        }
        return builder.build();
    }

    private HaikalatAssetRegistrar registrar(HaikalatAssetKind kind) {
        return id -> register(id, kind);
    }

    private synchronized void registerNamespace(String namespace) {
        requireMutable();
        String normalized = com.kaleblangley.haikalat.subsystems.resources.AssetId
                .of(Objects.requireNonNull(namespace, "namespace"), "probe")
                .namespace();
        dependencyNamespaces.add(normalized);
    }

    private synchronized HaikalatAssetDefinition register(
            ResourceLocation id,
            HaikalatAssetKind kind
    ) {
        requireMutable();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        String owner = Objects.requireNonNull(
                ownerModId.get(),
                "ownerModId supplier returned null");
        if (owner.isBlank()) {
            throw new IllegalStateException("Active mod id must not be blank");
        }
        if (!"minecraft".equals(owner)
                && !owner.equals(id.getNamespace())
                && !dependencyNamespaces.contains(id.getNamespace())) {
            throw new IllegalArgumentException(
                    "Mod " + owner + " cannot register Haikalat asset " + id
                            + " without first declaring namespace "
                            + id.getNamespace());
        }
        HaikalatAssetDefinition definition = new HaikalatAssetDefinition(
                id,
                kind,
                owner);
        HaikalatAssetDefinition existing = assets.putIfAbsent(id, definition);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Haikalat asset " + id + " is already registered as "
                            + existing.kind() + " by mod " + existing.ownerModId()
                            + "; duplicate declaration by mod " + owner);
        }
        return definition;
    }

    private void requireMutable() {
        if (frozen) {
            throw new IllegalStateException("Haikalat content registration is already frozen");
        }
    }
}
