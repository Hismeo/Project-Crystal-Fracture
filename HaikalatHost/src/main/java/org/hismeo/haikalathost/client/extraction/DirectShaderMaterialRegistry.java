package org.hismeo.haikalathost.client.extraction;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.client.material.MaterialFeature;
import org.hismeo.haikalathost.client.material.MaterialKey;
import org.hismeo.haikalathost.client.material.ShaderFamily;
import org.hismeo.haikalathost.client.submission.PassKey;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import static org.hismeo.haikalathost.client.material.MaterialFeature.LIGHTMAP;
import static org.hismeo.haikalathost.client.material.MaterialFeature.TEXTURED;
import static org.hismeo.haikalathost.client.material.MaterialFeature.VERTEX_COLOR;

/**
 * Explicit identity table for MeshData submitted directly through BufferUploader.
 *
 * <p>The table deliberately does not infer behavior from shader names. A newly introduced direct
 * shader therefore remains visible as a coverage failure instead of silently selecting a vaguely
 * compatible pipeline.</p>
 */
final class DirectShaderMaterialRegistry {
    private final Map<ShaderInstance, ShaderRoute> routes = new IdentityHashMap<>();
    private final TextureResourceRegistry textures;

    DirectShaderMaterialRegistry(TextureResourceRegistry textures) {
        this.textures = Objects.requireNonNull(textures, "textures");
    }

    DynamicRenderTypeClassifier.ClassifiedMaterial classify(
            ShaderInstance shader,
            ResourceLocation texture,
            VertexFormat format,
            PassKey passOverride
    ) {
        Objects.requireNonNull(format, "format");
        refreshCoreRoutes();
        ShaderRoute route = routes.get(shader);
        if (route == null) throw new UnknownDirectShaderException(shader);

        int features = route.baseFeatures();
        if (format.hasColor()) features |= VERTEX_COLOR.bit();
        int textureId = -1;
        if (route.textured()) {
            if (texture == null) {
                throw new IllegalStateException(
                        "Direct shader " + shader.getName()
                                + " requires a ResourceLocation-backed texture in slot 0");
            }
            textureId = textures.resolve(texture);
            features |= TEXTURED.bit();
        }
        return new DynamicRenderTypeClassifier.ClassifiedMaterial(
                passOverride == null ? route.pass() : passOverride,
                new MaterialKey(
                        overrideFamily(passOverride, route.family()),
                        features,
                        textureId < 0 ? 0L : Integer.toUnsignedLong(textureId),
                        0L,
                        textureId,
                        0,
                        0.0F,
                        0));
    }

    void clear() {
        routes.clear();
    }

    private static ShaderFamily overrideFamily(PassKey passOverride, ShaderFamily fallback) {
        if (passOverride == null) return fallback;
        return switch (passOverride) {
            case SKY -> ShaderFamily.SKY;
            case WEATHER -> ShaderFamily.WEATHER;
            default -> fallback;
        };
    }

    private void refreshCoreRoutes() {
        route(GameRenderer.getPositionShader(), ShaderFamily.SKY, PassKey.SKY, false, 0);
        route(GameRenderer.getRendertypeCloudsShader(), ShaderFamily.CLOUD, PassKey.CLOUD, false, 0);
        route(GameRenderer.getRendertypeLinesShader(), ShaderFamily.LINE, PassKey.LINE, false, 0);
        route(GameRenderer.getPositionColorShader(), ShaderFamily.LINE, PassKey.LINE, false, 0);
        route(GameRenderer.getPositionTexShader(), ShaderFamily.UI, PassKey.UI, true, 0);
        route(GameRenderer.getPositionTexColorShader(), ShaderFamily.UI, PassKey.UI, true, 0);
        route(GameRenderer.getParticleShader(), ShaderFamily.PARTICLE, PassKey.PARTICLE, true, 0);
        route(GameRenderer.getPositionColorLightmapShader(), ShaderFamily.ENTITY_OPAQUE,
                PassKey.ENTITY_OPAQUE, false, LIGHTMAP.bit());
        route(GameRenderer.getPositionColorTexLightmapShader(), ShaderFamily.TEXT,
                PassKey.TEXT, true, LIGHTMAP.bit());
    }

    private void route(
            ShaderInstance shader,
            ShaderFamily family,
            PassKey pass,
            boolean textured,
            int features
    ) {
        if (shader == null) return;
        ShaderRoute created = new ShaderRoute(family, pass, textured, features);
        ShaderRoute previous = routes.putIfAbsent(shader, created);
        if (previous != null && !previous.equals(created)) {
            throw new IllegalStateException(
                    "Direct ShaderInstance identity has multiple Host routes: " + shader.getName());
        }
    }

    private record ShaderRoute(
            ShaderFamily family,
            PassKey pass,
            boolean textured,
            int baseFeatures
    ) {
    }
}
