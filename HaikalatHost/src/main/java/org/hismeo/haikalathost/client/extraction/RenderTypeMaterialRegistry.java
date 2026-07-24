package org.hismeo.haikalathost.client.extraction;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.client.material.MaterialFeature;
import org.hismeo.haikalathost.client.material.MaterialKey;
import org.hismeo.haikalathost.client.material.ShaderFamily;
import org.hismeo.haikalathost.client.submission.PassKey;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import static org.hismeo.haikalathost.client.material.MaterialFeature.ALPHA_CUTOUT;
import static org.hismeo.haikalathost.client.material.MaterialFeature.FOG;
import static org.hismeo.haikalathost.client.material.MaterialFeature.LIGHTMAP;
import static org.hismeo.haikalathost.client.material.MaterialFeature.OVERLAY;
import static org.hismeo.haikalathost.client.material.MaterialFeature.TEXTURED;
import static org.hismeo.haikalathost.client.material.MaterialFeature.VERTEX_COLOR;

/**
 * The only normal-runtime boundary that translates Minecraft RenderType identity into Host data.
 *
 * <p>Dynamic composite types must be explicitly registered by their factory/state bridge. Unknown
 * types never fall back to Minecraft GL.</p>
 */
public final class RenderTypeMaterialRegistry {
    private static final long UNRESOLVED_TEXTURE = 0L;

    private final IdentityHashMap<RenderType, MaterialBinding> bindings = new IdentityHashMap<>();
    private final IdentityHashMap<RenderType, Boolean> unknownTypes = new IdentityHashMap<>();
    private final Map<PipelineSignature, Integer> pipelines = new HashMap<>();
    private final TextureResourceRegistry textures = new TextureResourceRegistry();
    private final DynamicRenderTypeClassifier dynamicClassifier =
            new DynamicRenderTypeClassifier(textures);
    private final DirectShaderMaterialRegistry directShaders =
            new DirectShaderMaterialRegistry(textures);

    public RenderTypeMaterialRegistry() {
    }

    public MaterialBinding register(RenderType renderType, PassKey pass, MaterialKey material) {
        Objects.requireNonNull(renderType, "renderType");
        MaterialBinding created = binding(pass, material);
        MaterialBinding previous = bindings.putIfAbsent(renderType, created);
        if (previous != null && !previous.equals(created)) {
            throw new IllegalStateException("RenderType identity already has a different Host material binding");
        }
        unknownTypes.remove(renderType);
        return previous == null ? created : previous;
    }

    public MaterialBinding resolve(RenderType renderType) {
        Objects.requireNonNull(renderType, "renderType");
        MaterialBinding binding = bindings.get(renderType);
        if (binding != null) return binding;
        try {
            DynamicRenderTypeClassifier.ClassifiedMaterial classified =
                    dynamicClassifier.classify(renderType);
            return register(renderType, classified.pass(), classified.material());
        } catch (UnknownRenderTypeException failure) {
            unknownTypes.put(renderType, Boolean.TRUE);
            throw failure;
        }
    }

    public int registeredCount() {
        return bindings.size();
    }

    public MaterialBinding resolveDirect(
            ShaderInstance shader, ResourceLocation texture, VertexFormat format) {
        DynamicRenderTypeClassifier.ClassifiedMaterial classified =
                directShaders.classify(
                        shader, texture, format, DirectDrawStateTracker.passOverride());
        return binding(classified.pass(), classified.material());
    }

    public ResourceLocation textureLocation(int identifier) {
        return textures.get(identifier);
    }

    public int unknownCount() {
        return unknownTypes.size();
    }

    public void clearDynamic() {
        bindings.clear();
        unknownTypes.clear();
        pipelines.clear();
        dynamicClassifier.clear();
        directShaders.clear();
        textures.clear();
    }

    private MaterialBinding binding(PassKey pass, MaterialKey material) {
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(material, "material");
        PipelineSignature signature =
                new PipelineSignature(pass, material.shaderFamily(), material.features());
        int pipelineId = pipelines.computeIfAbsent(signature, ignored -> pipelines.size());
        return new MaterialBinding(pass, material, pipelineId);
    }

    private void registerBuiltins() {
        register(RenderType.solid(), PassKey.WORLD_OPAQUE,
                material(ShaderFamily.WORLD_OPAQUE, features(TEXTURED, VERTEX_COLOR, LIGHTMAP, FOG), 0.0F));
        register(RenderType.cutoutMipped(), PassKey.WORLD_CUTOUT,
                material(ShaderFamily.WORLD_CUTOUT,
                        features(TEXTURED, VERTEX_COLOR, LIGHTMAP, FOG, ALPHA_CUTOUT), 0.5F));
        register(RenderType.cutout(), PassKey.WORLD_CUTOUT,
                material(ShaderFamily.WORLD_CUTOUT,
                        features(TEXTURED, VERTEX_COLOR, LIGHTMAP, FOG, ALPHA_CUTOUT), 0.5F));
        register(RenderType.translucent(), PassKey.WORLD_TRANSLUCENT,
                material(ShaderFamily.WORLD_TRANSLUCENT, features(TEXTURED, VERTEX_COLOR, LIGHTMAP, FOG), 0.0F));
        register(RenderType.translucentMovingBlock(), PassKey.WORLD_TRANSLUCENT,
                material(ShaderFamily.WORLD_TRANSLUCENT, features(TEXTURED, VERTEX_COLOR, LIGHTMAP, FOG), 0.0F));
        register(RenderType.tripwire(), PassKey.WORLD_TRANSLUCENT,
                material(ShaderFamily.WORLD_TRANSLUCENT, features(TEXTURED, VERTEX_COLOR, LIGHTMAP, FOG), 0.0F));

        register(RenderType.leash(), PassKey.ENTITY_OPAQUE,
                material(ShaderFamily.ENTITY_OPAQUE, features(VERTEX_COLOR, LIGHTMAP), 0.0F));
        register(RenderType.waterMask(), PassKey.ENTITY_OPAQUE,
                material(ShaderFamily.ENTITY_OPAQUE, 0, 0.0F));
        register(RenderType.lightning(), PassKey.ENTITY_TRANSLUCENT,
                material(ShaderFamily.ENTITY_TRANSLUCENT, features(VERTEX_COLOR), 0.0F));
        register(RenderType.dragonRays(), PassKey.ENTITY_TRANSLUCENT,
                material(ShaderFamily.ENTITY_TRANSLUCENT, features(VERTEX_COLOR), 0.0F));
        register(RenderType.dragonRaysDepth(), PassKey.ENTITY_TRANSLUCENT,
                material(ShaderFamily.ENTITY_TRANSLUCENT, features(VERTEX_COLOR), 0.0F));

        register(RenderType.lines(), PassKey.LINE,
                material(ShaderFamily.LINE, features(VERTEX_COLOR), 0.0F));
        register(RenderType.lineStrip(), PassKey.LINE,
                material(ShaderFamily.LINE, features(VERTEX_COLOR), 0.0F));
        register(RenderType.debugFilledBox(), PassKey.LINE,
                material(ShaderFamily.LINE, features(VERTEX_COLOR), 0.0F));
        register(RenderType.debugQuads(), PassKey.LINE,
                material(ShaderFamily.LINE, features(VERTEX_COLOR), 0.0F));
        register(RenderType.debugStructureQuads(), PassKey.LINE,
                material(ShaderFamily.LINE, features(VERTEX_COLOR), 0.0F));
        register(RenderType.debugSectionQuads(), PassKey.LINE,
                material(ShaderFamily.LINE, features(VERTEX_COLOR), 0.0F));

        register(RenderType.clouds(), PassKey.CLOUD,
                material(ShaderFamily.CLOUD, features(TEXTURED, VERTEX_COLOR), 0.0F));
        register(RenderType.cloudsDepthOnly(), PassKey.CLOUD,
                material(ShaderFamily.CLOUD, features(TEXTURED, VERTEX_COLOR), 0.0F));
        register(RenderType.endPortal(), PassKey.SKY,
                material(ShaderFamily.SKY, features(TEXTURED), 0.0F));
        register(RenderType.endGateway(), PassKey.SKY,
                material(ShaderFamily.SKY, features(TEXTURED), 0.0F));

        register(RenderType.armorEntityGlint(), PassKey.FIRST_PERSON,
                material(ShaderFamily.ENTITY_TRANSLUCENT, features(TEXTURED), 0.0F));
        register(RenderType.glintTranslucent(), PassKey.FIRST_PERSON,
                material(ShaderFamily.ENTITY_TRANSLUCENT, features(TEXTURED), 0.0F));
        register(RenderType.glint(), PassKey.FIRST_PERSON,
                material(ShaderFamily.ENTITY_TRANSLUCENT, features(TEXTURED), 0.0F));
        register(RenderType.entityGlint(), PassKey.FIRST_PERSON,
                material(ShaderFamily.ENTITY_TRANSLUCENT, features(TEXTURED), 0.0F));
        register(RenderType.entityGlintDirect(), PassKey.FIRST_PERSON,
                material(ShaderFamily.ENTITY_TRANSLUCENT, features(TEXTURED), 0.0F));

        register(RenderType.textBackground(), PassKey.TEXT,
                material(ShaderFamily.TEXT, features(VERTEX_COLOR, LIGHTMAP), 0.0F));
        register(RenderType.textBackgroundSeeThrough(), PassKey.TEXT,
                material(ShaderFamily.TEXT, features(VERTEX_COLOR, LIGHTMAP), 0.0F));

        register(RenderType.gui(), PassKey.UI,
                material(ShaderFamily.UI, features(VERTEX_COLOR), 0.0F));
        register(RenderType.guiOverlay(), PassKey.UI,
                material(ShaderFamily.UI, features(VERTEX_COLOR), 0.0F));
        register(RenderType.guiTextHighlight(), PassKey.UI,
                material(ShaderFamily.UI, features(VERTEX_COLOR), 0.0F));
        register(RenderType.guiGhostRecipeOverlay(), PassKey.UI,
                material(ShaderFamily.UI, features(VERTEX_COLOR), 0.0F));
    }

    private static MaterialKey material(ShaderFamily family, int features, float alphaCutoff) {
        return new MaterialKey(
                family, features, UNRESOLVED_TEXTURE, 0L, 0, 0, alphaCutoff, 0);
    }

    private static int features(MaterialFeature... features) {
        return MaterialFeature.mask(features);
    }

    private record PipelineSignature(PassKey pass, ShaderFamily family, int features) {
    }
}
