package org.hismeo.crystalfracture.weapon.internal.registry;

import org.hismeo.crystalfracture.weapon.api.CompiledWeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaRegistry;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartTypeDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;
import org.hismeo.crystalfracture.weapon.internal.compile.WeaponAssemblyCompiler;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Atomically publishes only fully validated snapshots. */
public final class WeaponRegistryManager implements WeaponSchemaRegistry {
    private static final WeaponRegistryManager INSTANCE = new WeaponRegistryManager();
    private final AtomicReference<State> state = new AtomicReference<>(
            new State(0L, WeaponRegistrySnapshot.empty(),
                    WeaponRegistryContentHasher.sha256(WeaponRegistrySnapshot.empty())));
    private final WeaponAssemblyCompiler compiler = new WeaponAssemblyCompiler();

    public static WeaponRegistryManager instance() {
        return INSTANCE;
    }

    @Override
    public long generation() {
        return state.get().generation();
    }

    @Override
    public String contentHash() {
        return state.get().contentHash();
    }

    @Override
    public Map<WeaponPartTypeId, WeaponPartTypeDefinition> partTypes() {
        return state.get().snapshot().partTypes();
    }

    @Override
    public Map<WeaponPartId, WeaponPartDefinition> parts() {
        return state.get().snapshot().parts();
    }

    @Override
    public Map<WeaponSchemaId, WeaponSchemaDefinition> schemas() {
        return state.get().snapshot().schemas();
    }

    @Override
    public Optional<WeaponPartTypeDefinition> partType(WeaponPartTypeId id) {
        return state.get().snapshot().partType(Objects.requireNonNull(id, "id"));
    }

    @Override
    public Optional<WeaponPartDefinition> part(WeaponPartId id) {
        return state.get().snapshot().part(Objects.requireNonNull(id, "id"));
    }

    @Override
    public Optional<WeaponSchemaDefinition> schema(WeaponSchemaId id) {
        return state.get().snapshot().schema(Objects.requireNonNull(id, "id"));
    }

    @Override
    public Optional<CompiledWeaponAssembly> compile(WeaponAssembly assembly) {
        State current = state.get();
        return compiler.compile(Objects.requireNonNull(assembly, "assembly"), current.snapshot())
                .assembly();
    }

    public long publish(WeaponRegistrySnapshot replacement) {
        Objects.requireNonNull(replacement, "replacement");
        return state.updateAndGet(previous -> new State(
                        previous.generation() + 1L,
                        replacement,
                        WeaponRegistryContentHasher.sha256(replacement)))
                .generation();
    }

    private record State(long generation, WeaponRegistrySnapshot snapshot, String contentHash) {
    }
}
