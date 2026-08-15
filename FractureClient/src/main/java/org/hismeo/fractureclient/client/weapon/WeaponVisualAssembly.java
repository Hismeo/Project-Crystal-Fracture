package org.hismeo.fractureclient.client.weapon;

import org.hismeo.crystalfracture.weapon.api.CompiledWeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable visual compilation result in Weapon Root coordinates. */
public final class WeaponVisualAssembly {
    private final CompiledWeaponAssembly logical;
    private final Map<WeaponSlotId, Matrix4f> partTransforms;
    private final Map<MarkerName, Matrix4f> markerTransforms;

    WeaponVisualAssembly(
            CompiledWeaponAssembly logical,
            Map<WeaponSlotId, Matrix4f> partTransforms,
            Map<MarkerName, Matrix4f> markerTransforms
    ) {
        this.logical = Objects.requireNonNull(logical, "logical");
        this.partTransforms = immutableMatrices(partTransforms);
        this.markerTransforms = immutableMatrices(markerTransforms);
    }

    public CompiledWeaponAssembly logical() {
        return logical;
    }

    public Matrix4fc partTransform(WeaponSlotId slot) {
        Matrix4f result = partTransforms.get(Objects.requireNonNull(slot, "slot"));
        if (result == null) {
            throw new IllegalArgumentException("Unknown weapon slot " + slot);
        }
        return new Matrix4f(result);
    }

    public Optional<Matrix4fc> markerTransform(MarkerName marker) {
        Matrix4f result = markerTransforms.get(Objects.requireNonNull(marker, "marker"));
        return result == null ? Optional.empty() : Optional.of(new Matrix4f(result));
    }

    private static <K extends Comparable<K>> Map<K, Matrix4f> immutableMatrices(
            Map<K, Matrix4f> source
    ) {
        TreeMap<K, Matrix4f> copy = new TreeMap<>();
        source.forEach((key, value) -> copy.put(key, new Matrix4f(value)));
        return Collections.unmodifiableMap(copy);
    }
}
