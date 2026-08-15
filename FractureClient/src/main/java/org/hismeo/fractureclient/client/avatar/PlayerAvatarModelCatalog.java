package org.hismeo.fractureclient.client.avatar;

import com.kaleblangley.haikalat.core.assets.AssetByteResolver;
import com.kaleblangley.haikalat.core.assets.AssetRef;
import com.kaleblangley.haikalat.core.assets.MaterialModel;
import com.kaleblangley.haikalat.core.assets.gltf.GltfAssetLoader;
import com.kaleblangley.haikalat.core.assets.gltf.LoadedGltfScene;
import com.kaleblangley.haikalat.core.material.Material;
import com.kaleblangley.haikalat.core.material.UniformValue;
import com.kaleblangley.haikalat.backend.shader.ShaderProgram;
import com.kaleblangley.haikalat.subsystems.render3d.gltf.GltfRuntimeLibrary;
import com.kaleblangley.haikalat.subsystems.render3d.gltf.GltfSceneAsset;
import com.kaleblangley.haikalat.subsystems.render3d.gltf.GltfSceneInstance;
import com.kaleblangley.haikalat.subsystems.resources.AssetId;
import com.kaleblangley.haikalat.subsystems.resources.ResourceCatalog;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Shared, validated Wild/Sile model assets for one accepted resource generation. */
public final class PlayerAvatarModelCatalog implements AutoCloseable {
    /** Logical equipment path implemented by the current exported Avatar rig node. */
    public static final String WEAPON_SOCKET_PATH = "hand_r/weapon_socket";
    private static final String WEAPON_SOCKET_NODE = "right_hand";
    public static final String WILD_PATH = "haikalat/models/player/player_wild.gltf";
    public static final String SILE_PATH = "haikalat/models/player/player_sile.gltf";
    public static final String WILD_LIBRARY_PATH =
            "haikalat/models/player/player_wild.animation-library.json";
    public static final String SILE_LIBRARY_PATH =
            "haikalat/models/player/player_sile.animation-library.json";
    private static final String AVATAR_VERTEX_SHADER =
            "/assets/fracture_client/shaders/avatar/player_avatar.vsh";
    private static final String AVATAR_FRAGMENT_SHADER =
            "/assets/fracture_client/shaders/avatar/player_avatar.fsh";

    private static final Set<String> REQUIRED_ANIMATIONS = Set.of(
            "stand", "move", "run", "jump", "dash");

    private static final Set<String> REQUIRED_BONES = Set.of(
            "bone",
            "torso",
            "chest",
            "head",
            "right_arm",
            "right_hand",
            "left_arm",
            "left_hand",
            "right_leg",
            "right_shin",
            "left_leg",
            "left_shin");

    private final GltfRuntimeLibrary runtimeLibrary;
    private final ShaderProgram avatarShader;
    private final Map<AvatarKind, GltfSceneAsset> assets;
    private final Map<Material, Material> embeddedMaterials = new IdentityHashMap<>();
    private final long resourceGeneration;
    private final int weaponSocketNodeIndex;
    private boolean closed;

    private PlayerAvatarModelCatalog(
            GltfRuntimeLibrary runtimeLibrary,
            ShaderProgram avatarShader,
            Map<AvatarKind, GltfSceneAsset> assets,
            long resourceGeneration,
            int weaponSocketNodeIndex
    ) {
        this.runtimeLibrary = runtimeLibrary;
        this.avatarShader = avatarShader;
        this.assets = assets;
        this.resourceGeneration = resourceGeneration;
        this.weaponSocketNodeIndex = weaponSocketNodeIndex;
    }

    public static PlayerAvatarModelCatalog load(
            ResourceCatalog resources,
            long resourceGeneration
    ) {
        Objects.requireNonNull(resources, "resources");
        GltfRuntimeLibrary runtime = GltfRuntimeLibrary.create();
        ShaderProgram avatarShader = null;
        EnumMap<AvatarKind, GltfSceneAsset> loadedAssets = new EnumMap<>(AvatarKind.class);
        try {
            avatarShader = ShaderProgram.fromResource(
                    PlayerAvatarModelCatalog.class,
                    AVATAR_VERTEX_SHADER,
                    AVATAR_FRAGMENT_SHADER);
            AssetByteResolver resolver = new CatalogResolver(resources, "fracture_client");
            GltfAssetLoader loader = new GltfAssetLoader(resolver);
            LoadedGltfScene wild = loadOne(loader, WILD_LIBRARY_PATH);
            LoadedGltfScene sile = loadOne(loader, SILE_LIBRARY_PATH);
            validateSharedRig(wild, WILD_LIBRARY_PATH, sile, SILE_LIBRARY_PATH);
            loadedAssets.put(AvatarKind.WILD, GltfSceneAsset.upload(wild, runtime));
            loadedAssets.put(AvatarKind.SILE, GltfSceneAsset.upload(sile, runtime));
            return new PlayerAvatarModelCatalog(
                    runtime,
                    avatarShader,
                    loadedAssets,
                    resourceGeneration,
                    nodeIndex(wild, WEAPON_SOCKET_NODE));
        } catch (RuntimeException failure) {
            loadedAssets.values().forEach(GltfSceneAsset::close);
            if (avatarShader != null) {
                avatarShader.close();
            }
            runtime.close();
            throw failure;
        }
    }

    public GltfSceneInstance instantiate(AvatarKind kind, Matrix4f rootTransform) {
        ensureOpen();
        GltfSceneAsset asset = assets.get(Objects.requireNonNull(kind, "kind"));
        if (asset == null) {
            throw new IllegalStateException("Avatar model is unavailable: " + kind);
        }
        // MASK shadow depth is intentionally disabled by Haikalat 0.20.1.
        return asset.instantiateAnimated(rootTransform, false);
    }

    /**
     * Reuses the glTF material's raster and alpha contract with a Minecraft-embedded shader.
     *
     * <p>Haikalat's full metallic-roughness path requires HDR tone mapping and a PBR
     * environment. That path also draws an environment background, so it is not suitable for
     * compositing an Avatar over Minecraft's existing world target. The embedded material keeps
     * skinning, player-skin sampling and direct lighting without claiming the full PBR contract.</p>
     */
    public Material embeddedMaterial(Material source) {
        ensureOpen();
        Objects.requireNonNull(source, "source");
        return embeddedMaterials.computeIfAbsent(source, this::createEmbeddedMaterial);
    }

    public long resourceGeneration() {
        return resourceGeneration;
    }

    public int weaponSocketNodeIndex() {
        ensureOpen();
        return weaponSocketNodeIndex;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        embeddedMaterials.values().forEach(Material::close);
        embeddedMaterials.clear();
        assets.values().forEach(GltfSceneAsset::close);
        assets.clear();
        runtimeLibrary.close();
        avatarShader.close();
    }

    private Material createEmbeddedMaterial(Material source) {
        return Material.builder(avatarShader)
                .model(MaterialModel.LEGACY)
                .blendMode(source.blendMode())
                .depthTest(source.depthTest())
                .cullMode(source.cullMode())
                .setVec4("uBaseColorFactor", vec4Uniform(
                        source,
                        "uBaseColorFactor",
                        new org.joml.Vector4f(1.0F)))
                .setInt("uHasVertexColor", intUniform(source, "uHasVertexColor", 0))
                .setInt("uDoubleSided", intUniform(source, "uDoubleSided", 0))
                .setFloat("uAlphaCutoff", floatUniform(source, "uAlphaCutoff", 0.0F))
                .build();
    }

    private static int intUniform(Material material, String name, int fallback) {
        UniformValue value = uniform(material, name);
        return value instanceof UniformValue.IntVal integer ? integer.value() : fallback;
    }

    private static float floatUniform(Material material, String name, float fallback) {
        UniformValue value = uniform(material, name);
        return value instanceof UniformValue.FloatVal number ? number.value() : fallback;
    }

    private static org.joml.Vector4f vec4Uniform(
            Material material,
            String name,
            org.joml.Vector4f fallback
    ) {
        UniformValue value = uniform(material, name);
        return value instanceof UniformValue.Vec4Val vector
                ? new org.joml.Vector4f(vector.value())
                : fallback;
    }

    private static UniformValue uniform(Material material, String name) {
        return material.defaultUniforms().entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static LoadedGltfScene loadOne(GltfAssetLoader loader, String path) {
        LoadedGltfScene scene = loader.loadAnimationLibrary(AssetRef.of(path));
        validateSkeleton(scene, path);
        validateAnimations(scene, path);
        return scene;
    }

    private static void validateSkeleton(LoadedGltfScene scene, String path) {
        Set<String> found = new HashSet<>();
        scene.nodes().stream().map(LoadedGltfScene.Node::name).forEach(found::add);
        Set<String> missing = new HashSet<>(REQUIRED_BONES);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(path + " is missing required bones " + missing);
        }
    }

    private static void validateAnimations(LoadedGltfScene scene, String path) {
        Set<String> found = new HashSet<>();
        scene.animations().stream().map(LoadedGltfScene.AnimationDef::name).forEach(found::add);
        Set<String> missing = new HashSet<>(REQUIRED_ANIMATIONS);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(path + " is missing required animations " + missing);
        }
    }

    private static int nodeIndex(LoadedGltfScene scene, String name) {
        return scene.nodes().stream()
                .filter(node -> node.name().equals(name))
                .mapToInt(LoadedGltfScene.Node::index)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Avatar rig is missing " + WEAPON_SOCKET_PATH + " (node " + name + ")"));
    }

    /** Wild and Sile may differ in mesh/material/UV data, but never in their rig contract. */
    static void validateSharedRig(
            LoadedGltfScene canonical,
            String canonicalPath,
            LoadedGltfScene candidate,
            String candidatePath
    ) {
        requireEqual(canonical.nodes().size(), candidate.nodes().size(), candidatePath,
                "node count differs from " + canonicalPath);
        requireEqual(canonical.nodeRigs().size(), candidate.nodeRigs().size(), candidatePath,
                "node-rig count differs from " + canonicalPath);
        for (int index = 0; index < canonical.nodes().size(); index++) {
            LoadedGltfScene.Node expectedNode = canonical.nodes().get(index);
            LoadedGltfScene.Node actualNode = candidate.nodes().get(index);
            String location = candidatePath + " nodes[" + index + "]";
            if (!expectedNode.name().equals(actualNode.name())) {
                throw incompatible(location, "bone name is " + actualNode.name()
                        + ", expected " + expectedNode.name());
            }

            LoadedGltfScene.NodeRigDef expected = canonical.nodeRigs().get(index);
            LoadedGltfScene.NodeRigDef actual = candidate.nodeRigs().get(index);
            requireEqual(expected.parentIndex(), actual.parentIndex(), location,
                    "parent differs");
            requireEqual(expected.skinIndex(), actual.skinIndex(), location,
                    "skin binding differs");
            if (expected.matrixAuthored() != actual.matrixAuthored()
                    || !near(expected.translation(), actual.translation())
                    || !sameRotation(expected.rotation(), actual.rotation())
                    || !near(expected.scale(), actual.scale())) {
                throw incompatible(location, "bind pose differs");
            }
        }

        requireEqual(canonical.skins().size(), candidate.skins().size(), candidatePath,
                "skin count differs from " + canonicalPath);
        for (int skinIndex = 0; skinIndex < canonical.skins().size(); skinIndex++) {
            LoadedGltfScene.SkinDef expected = canonical.skins().get(skinIndex);
            LoadedGltfScene.SkinDef actual = candidate.skins().get(skinIndex);
            String location = candidatePath + " skins[" + skinIndex + "]";
            requireEqual(expected.skeletonRootNode(), actual.skeletonRootNode(), location,
                    "skeleton root differs");
            if (!expected.joints().equals(actual.joints())) {
                throw incompatible(location, "joint order differs");
            }
            List<org.joml.Matrix4fc> expectedMatrices = expected.inverseBindMatrices();
            List<org.joml.Matrix4fc> actualMatrices = actual.inverseBindMatrices();
            requireEqual(expectedMatrices.size(), actualMatrices.size(), location,
                    "inverse-bind matrix count differs");
            for (int matrixIndex = 0; matrixIndex < expectedMatrices.size(); matrixIndex++) {
                if (!near(expectedMatrices.get(matrixIndex), actualMatrices.get(matrixIndex))) {
                    throw incompatible(location + ".inverseBindMatrices[" + matrixIndex + "]",
                            "inverse-bind matrix differs");
                }
            }
        }
    }

    private static boolean near(org.joml.Vector3fc first, org.joml.Vector3fc second) {
        return Math.abs(first.x() - second.x()) <= 1.0E-6F
                && Math.abs(first.y() - second.y()) <= 1.0E-6F
                && Math.abs(first.z() - second.z()) <= 1.0E-6F;
    }

    private static boolean sameRotation(
            org.joml.Quaternionfc first,
            org.joml.Quaternionfc second
    ) {
        float dot = first.x() * second.x() + first.y() * second.y()
                + first.z() * second.z() + first.w() * second.w();
        return Math.abs(Math.abs(dot) - 1.0F) <= 1.0E-6F;
    }

    private static boolean near(org.joml.Matrix4fc first, org.joml.Matrix4fc second) {
        return Math.abs(first.m00() - second.m00()) <= 1.0E-6F
                && Math.abs(first.m01() - second.m01()) <= 1.0E-6F
                && Math.abs(first.m02() - second.m02()) <= 1.0E-6F
                && Math.abs(first.m03() - second.m03()) <= 1.0E-6F
                && Math.abs(first.m10() - second.m10()) <= 1.0E-6F
                && Math.abs(first.m11() - second.m11()) <= 1.0E-6F
                && Math.abs(first.m12() - second.m12()) <= 1.0E-6F
                && Math.abs(first.m13() - second.m13()) <= 1.0E-6F
                && Math.abs(first.m20() - second.m20()) <= 1.0E-6F
                && Math.abs(first.m21() - second.m21()) <= 1.0E-6F
                && Math.abs(first.m22() - second.m22()) <= 1.0E-6F
                && Math.abs(first.m23() - second.m23()) <= 1.0E-6F
                && Math.abs(first.m30() - second.m30()) <= 1.0E-6F
                && Math.abs(first.m31() - second.m31()) <= 1.0E-6F
                && Math.abs(first.m32() - second.m32()) <= 1.0E-6F
                && Math.abs(first.m33() - second.m33()) <= 1.0E-6F;
    }

    private static void requireEqual(int expected, int actual, String location, String message) {
        if (expected != actual) {
            throw incompatible(location, message + ": " + actual + ", expected " + expected);
        }
    }

    private static IllegalArgumentException incompatible(String location, String message) {
        return new IllegalArgumentException(location + " is not compatible with the shared player rig: "
                + message);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("avatar model catalog is closed");
        }
    }

    private static final class CatalogResolver implements AssetByteResolver {
        private final ResourceCatalog catalog;
        private final String namespace;

        private CatalogResolver(ResourceCatalog catalog, String namespace) {
            this.catalog = catalog;
            this.namespace = namespace;
        }

        @Override
        public byte[] readBytes(AssetRef asset, long maxBytes) {
            try {
                return catalog.readBytes(AssetId.of(namespace, stripLeadingSlash(asset.path())), maxBytes);
            } catch (IOException failure) {
                throw new IllegalArgumentException("Could not read avatar asset " + asset.path(), failure);
            }
        }

        @Override
        public boolean exists(AssetRef asset) {
            return catalog.exists(AssetId.of(namespace, stripLeadingSlash(asset.path())));
        }

        @Override
        public AssetRef resolveRelative(AssetRef base, String relative) {
            AssetId baseId = AssetId.of(namespace, stripLeadingSlash(base.path()));
            return AssetRef.of(baseId.resolve(relative).path());
        }

        private static String stripLeadingSlash(String path) {
            return path.startsWith("/") ? path.substring(1) : path;
        }
    }
}
