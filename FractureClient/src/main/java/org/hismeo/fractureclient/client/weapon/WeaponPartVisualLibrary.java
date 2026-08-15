package org.hismeo.fractureclient.client.weapon;

import com.kaleblangley.haikalat.backend.shader.ShaderProgram;
import com.kaleblangley.haikalat.backend.texture.Sampler;
import com.kaleblangley.haikalat.backend.texture.Texture2D;
import com.kaleblangley.haikalat.backend.texture.TextureColorSpace;
import com.kaleblangley.haikalat.core.assets.AssetByteResolver;
import com.kaleblangley.haikalat.core.assets.AssetRef;
import com.kaleblangley.haikalat.core.assets.MaterialModel;
import com.kaleblangley.haikalat.core.assets.gltf.GltfAssetLoader;
import com.kaleblangley.haikalat.core.material.Material;
import com.kaleblangley.haikalat.core.material.UniformValue;
import com.kaleblangley.haikalat.subsystems.render3d.gltf.GltfRuntimeLibrary;
import com.kaleblangley.haikalat.subsystems.render3d.gltf.GltfSceneAsset;
import com.kaleblangley.haikalat.subsystems.render3d.gltf.GltfSceneInstance;
import com.kaleblangley.haikalat.subsystems.resources.AssetId;
import com.kaleblangley.haikalat.subsystems.resources.ResourceCatalog;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.io.IOException;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One GPU resource library per Haikalat resource generation.
 * Assembly catalogs borrow these assets instead of re-uploading the same GLBs on every selection.
 */
final class WeaponPartVisualLibrary implements AutoCloseable {
    private static final String WEAPON_VERTEX_SHADER =
            "/assets/fracture_client/shaders/avatar/player_avatar.vsh";
    private static final String WEAPON_FRAGMENT_SHADER =
            "/assets/fracture_client/shaders/avatar/player_avatar.fsh";
    private static final String BASE_COLOR_SAMPLER = "uBaseColorMap";

    private final ResourceCatalog resources;
    private final long resourceGeneration;
    private final GltfRuntimeLibrary runtimeLibrary;
    private final ShaderProgram weaponShader;
    private final Map<WeaponPartId, PartAsset> assets = new LinkedHashMap<>();
    private final Map<Material, Material> embeddedMaterials = new IdentityHashMap<>();
    private Texture2D sharedAtlas;
    private Sampler sharedAtlasSampler;
    private byte[] sharedAtlasBytes;
    private boolean closed;

    WeaponPartVisualLibrary(ResourceCatalog resources, long resourceGeneration) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.resourceGeneration = resourceGeneration;
        runtimeLibrary = GltfRuntimeLibrary.create();
        try {
            weaponShader = ShaderProgram.fromResource(
                    WeaponPartVisualLibrary.class,
                    WEAPON_VERTEX_SHADER,
                    WEAPON_FRAGMENT_SHADER);
        } catch (RuntimeException failure) {
            runtimeLibrary.close();
            throw failure;
        }
    }

    long resourceGeneration() {
        return resourceGeneration;
    }

    WeaponPartVisualNodes nodes(WeaponPartDefinition part) {
        return asset(part).nodes();
    }

    GltfSceneInstance instantiate(WeaponPartDefinition part, Matrix4fc rootTransform) {
        ensureOpen();
        return asset(part).scene().instantiateAnimated(rootTransform, false);
    }

    Material embeddedMaterial(Material source) {
        ensureOpen();
        return embeddedMaterials.computeIfAbsent(
                Objects.requireNonNull(source, "source"),
                this::createEmbeddedMaterial);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        embeddedMaterials.values().forEach(Material::close);
        embeddedMaterials.clear();
        assets.values().forEach(asset -> asset.scene().close());
        assets.clear();
        runtimeLibrary.close();
        if (sharedAtlasSampler != null) {
            sharedAtlasSampler.close();
            sharedAtlasSampler = null;
        }
        if (sharedAtlas != null) {
            sharedAtlas.close();
            sharedAtlas = null;
        }
        sharedAtlasBytes = null;
        weaponShader.close();
    }

    private PartAsset asset(WeaponPartDefinition part) {
        ensureOpen();
        PartAsset existing = assets.get(part.id());
        if (existing != null) {
            return existing;
        }
        org.hismeo.fractureclient.FractureClient.LOGGER.info(
                "Uploading shared weapon Part visual {} from {}",
                part.id(),
                part.visualModel());
        var loaded = loadScene(resources, part.visualModel(), false);
        prepareSharedAtlas(part, loaded);
        WeaponPartVisualNodes nodes = WeaponPartVisualNodes.from(part, loaded);
        var geometryOnly = loadScene(resources, part.visualModel(), true);
        GltfSceneAsset scene = uploadScene(part, geometryOnly);
        PartAsset created = new PartAsset(nodes, scene);
        assets.put(part.id(), created);
        org.hismeo.fractureclient.FractureClient.LOGGER.info(
                "Uploaded shared weapon Part visual {}",
                part.id());
        return created;
    }

    private void prepareSharedAtlas(
            WeaponPartDefinition part,
            com.kaleblangley.haikalat.core.assets.gltf.LoadedGltfScene loaded
    ) {
        if (loaded.images().isEmpty()) {
            throw new IllegalArgumentException(
                    "Weapon Part has no embedded atlas image: " + part.id());
        }
        byte[] encoded = loaded.images().getFirst().encoded();
        if (sharedAtlasBytes != null) {
            if (!Arrays.equals(sharedAtlasBytes, encoded)) {
                throw new IllegalArgumentException(
                        "Weapon Part uses a different atlas image: " + part.id());
            }
            return;
        }

        Sampler createdSampler = null;
        Texture2D createdTexture = null;
        try {
            var sampler = loaded.samplers().isEmpty() ? null : loaded.samplers().getFirst();
            createdSampler = sampler == null
                    ? Sampler.nearestRepeat()
                    : Sampler.create(new Sampler.Descriptor(
                    sampler.minFilter(), sampler.magFilter(),
                    sampler.wrapS(), sampler.wrapT()));
            createdTexture = Texture2D.fromEncoded(encoded, false, TextureColorSpace.SRGB);
            org.lwjgl.opengl.GL11.glFinish();
            sharedAtlasSampler = createdSampler;
            sharedAtlas = createdTexture;
            sharedAtlasBytes = encoded.clone();
        } catch (RuntimeException failure) {
            if (createdTexture != null) {
                createdTexture.close();
            }
            if (createdSampler != null) {
                createdSampler.close();
            }
            throw failure;
        }
    }

    /**
     * Runtime Part changes happen while Minecraft is presenting frames. Keep each native resource
     * creation isolated and completed before advancing to the next one; NVIDIA 576.88 otherwise
     * faults its asynchronous Present worker when a whole GLB is uploaded in one synchronous burst.
     */
    private GltfSceneAsset uploadScene(
            WeaponPartDefinition part,
            com.kaleblangley.haikalat.core.assets.gltf.LoadedGltfScene loaded
    ) {
        org.lwjgl.opengl.GL11.glFinish();
        try (GltfSceneAsset.UploadSession upload =
                     GltfSceneAsset.beginUpload(loaded, runtimeLibrary)) {
            while (!upload.isComplete()) {
                GltfSceneAsset.UploadPhase phase = upload.phase();
                org.hismeo.fractureclient.FractureClient.LOGGER.info(
                        "Uploading weapon Part visual {} GPU phase {}",
                        part.id(),
                        phase);
                upload.advance(1);
                org.lwjgl.opengl.GL11.glFinish();
            }
            return upload.finish();
        }
    }

    private Material createEmbeddedMaterial(Material source) {
        Material.Builder builder = Material.builder(weaponShader)
                .model(MaterialModel.LEGACY)
                .blendMode(source.blendMode())
                .depthTest(source.depthTest())
                .cullMode(source.cullMode())
                .setVec4("uBaseColorFactor", vec4Uniform(
                        source, "uBaseColorFactor", new Vector4f(1.0F)))
                .setInt("uHasVertexColor", intUniform(source, "uHasVertexColor", 0))
                .setInt("uDoubleSided", intUniform(source, "uDoubleSided", 0))
                .setFloat("uAlphaCutoff", floatUniform(source, "uAlphaCutoff", 0.0F));
        if (sharedAtlas == null || sharedAtlasSampler == null) {
            throw new IllegalStateException("Shared weapon atlas is unavailable");
        }
        builder.texture(0, BASE_COLOR_SAMPLER, sharedAtlas, sharedAtlasSampler);
        return builder.build();
    }

    private static com.kaleblangley.haikalat.core.assets.gltf.LoadedGltfScene loadScene(
            ResourceCatalog resources,
            ResourceLocation model,
            boolean geometryOnly
    ) {
        AssetByteResolver resolver = new CatalogResolver(
                resources, model.getNamespace(), geometryOnly);
        return new GltfAssetLoader(resolver).load(AssetRef.of(model.getPath()));
    }

    private static int intUniform(Material material, String name, int fallback) {
        UniformValue value = uniform(material, name);
        return value instanceof UniformValue.IntVal integer ? integer.value() : fallback;
    }

    private static float floatUniform(Material material, String name, float fallback) {
        UniformValue value = uniform(material, name);
        return value instanceof UniformValue.FloatVal number ? number.value() : fallback;
    }

    private static Vector4f vec4Uniform(Material material, String name, Vector4f fallback) {
        UniformValue value = uniform(material, name);
        return value instanceof UniformValue.Vec4Val vector
                ? new Vector4f(vector.value())
                : fallback;
    }

    private static UniformValue uniform(Material material, String name) {
        return material.defaultUniforms().entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("weapon Part visual library is closed");
        }
    }

    private record PartAsset(WeaponPartVisualNodes nodes, GltfSceneAsset scene) {
    }

    private static final class CatalogResolver implements AssetByteResolver {
        private final ResourceCatalog catalog;
        private final String namespace;
        private final boolean geometryOnly;

        private CatalogResolver(
                ResourceCatalog catalog,
                String namespace,
                boolean geometryOnly
        ) {
            this.catalog = catalog;
            this.namespace = namespace;
            this.geometryOnly = geometryOnly;
        }

        @Override
        public byte[] readBytes(AssetRef asset, long maxBytes) {
            try {
                byte[] bytes = catalog.readBytes(
                        AssetId.of(namespace, stripLeadingSlash(asset.path())), maxBytes);
                BlockbenchGlbCompatibility.SanitizedGlb sanitized =
                        BlockbenchGlbCompatibility.sanitize(bytes);
                if (sanitized.replacements() > 0) {
                    org.hismeo.fractureclient.FractureClient.LOGGER.warn(
                            "Normalized {} invalid null-scale locator(s) in Blockbench GLB {}:{}",
                            sanitized.replacements(), namespace, asset.path());
                }
                return geometryOnly
                        ? BlockbenchGlbCompatibility.stripEmbeddedTextures(sanitized.bytes())
                        : sanitized.bytes();
            } catch (IOException failure) {
                throw new IllegalArgumentException(
                        "Could not read weapon asset " + namespace + ":" + asset.path(), failure);
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
