package org.hismeo.fractureclient.client.weapon;

import com.kaleblangley.haikalat.core.assets.gltf.LoadedGltfScene;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** CPU-side GLB node transforms for one Part, relative to its local origin. */
public final class WeaponPartVisualNodes {
    private final WeaponPartId part;
    private final ResourceLocation model;
    private final Map<String, Matrix4f> transforms;
    private final Set<String> duplicateNames;

    public WeaponPartVisualNodes(
            WeaponPartId part,
            ResourceLocation model,
            Map<String, ? extends Matrix4fc> transforms,
            Set<String> duplicateNames
    ) {
        this.part = Objects.requireNonNull(part, "part");
        this.model = Objects.requireNonNull(model, "model");
        Objects.requireNonNull(transforms, "transforms");
        TreeMap<String, Matrix4f> copied = new TreeMap<>();
        transforms.forEach((name, transform) -> copied.put(
                Objects.requireNonNull(name, "node name"),
                new Matrix4f(Objects.requireNonNull(transform, "node transform"))));
        this.transforms = Collections.unmodifiableMap(copied);
        this.duplicateNames = Collections.unmodifiableSet(new TreeSet<>(
                Objects.requireNonNull(duplicateNames, "duplicateNames")));
    }

    public static WeaponPartVisualNodes from(
            WeaponPartDefinition part,
            LoadedGltfScene scene
    ) {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(scene, "scene");
        Map<String, Matrix4fc> nodes = new TreeMap<>();
        Set<String> duplicates = new TreeSet<>();
        Set<String> semanticNodes = Stream.concat(
                        part.connects().values().stream(),
                        part.markers().values().stream())
                .collect(Collectors.toUnmodifiableSet());
        for (LoadedGltfScene.Node node : scene.nodes()) {
            if (node.name().isBlank()) {
                continue;
            }
            Matrix4fc transform = semanticNodes.contains(node.name())
                    ? locatorPose(node.worldTransform())
                    : node.worldTransform();
            Matrix4fc previous = nodes.putIfAbsent(node.name(), transform);
            if (previous != null && !near(previous, transform)) {
                duplicates.add(node.name());
            }
        }
        return new WeaponPartVisualNodes(
                part.id(), part.visualModel(), nodes, duplicates);
    }

    /** Blockbench's locator display size is not part of the Connect/Marker transform contract. */
    private static Matrix4f locatorPose(Matrix4fc source) {
        Matrix4f result = new Matrix4f(source);
        float x = (float) Math.sqrt(result.m00() * result.m00()
                + result.m01() * result.m01() + result.m02() * result.m02());
        float y = (float) Math.sqrt(result.m10() * result.m10()
                + result.m11() * result.m11() + result.m12() * result.m12());
        float z = (float) Math.sqrt(result.m20() * result.m20()
                + result.m21() * result.m21() + result.m22() * result.m22());
        if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                && x > 1.0E-8F && y > 1.0E-8F && z > 1.0E-8F) {
            result.m00(result.m00() / x).m01(result.m01() / x).m02(result.m02() / x);
            result.m10(result.m10() / y).m11(result.m11() / y).m12(result.m12() / y);
            result.m20(result.m20() / z).m21(result.m21() / z).m22(result.m22() / z);
        }
        return result;
    }

    private static boolean near(Matrix4fc first, Matrix4fc second) {
        float epsilon = 1.0E-6F;
        return Math.abs(first.m00() - second.m00()) <= epsilon
                && Math.abs(first.m01() - second.m01()) <= epsilon
                && Math.abs(first.m02() - second.m02()) <= epsilon
                && Math.abs(first.m03() - second.m03()) <= epsilon
                && Math.abs(first.m10() - second.m10()) <= epsilon
                && Math.abs(first.m11() - second.m11()) <= epsilon
                && Math.abs(first.m12() - second.m12()) <= epsilon
                && Math.abs(first.m13() - second.m13()) <= epsilon
                && Math.abs(first.m20() - second.m20()) <= epsilon
                && Math.abs(first.m21() - second.m21()) <= epsilon
                && Math.abs(first.m22() - second.m22()) <= epsilon
                && Math.abs(first.m23() - second.m23()) <= epsilon
                && Math.abs(first.m30() - second.m30()) <= epsilon
                && Math.abs(first.m31() - second.m31()) <= epsilon
                && Math.abs(first.m32() - second.m32()) <= epsilon
                && Math.abs(first.m33() - second.m33()) <= epsilon;
    }

    public WeaponPartId part() {
        return part;
    }

    public ResourceLocation model() {
        return model;
    }

    public Optional<Matrix4fc> node(String name) {
        Matrix4f transform = transforms.get(name);
        return transform == null ? Optional.empty() : Optional.of(new Matrix4f(transform));
    }

    public boolean ambiguous(String name) {
        return duplicateNames.contains(name);
    }
}
