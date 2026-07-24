package org.hismeo.haikalathost.client.extraction;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.client.intercept.CompositeRenderTypeAccessor;
import org.hismeo.haikalathost.client.intercept.RenderStateBooleanAccessor;
import org.hismeo.haikalathost.client.intercept.RenderStateTextureInvoker;
import org.hismeo.haikalathost.client.intercept.RenderTypeCompositeStateAccessor;
import org.hismeo.haikalathost.client.material.MaterialFeature;
import org.hismeo.haikalathost.client.material.MaterialKey;
import org.hismeo.haikalathost.client.material.ShaderFamily;
import org.hismeo.haikalathost.client.submission.PassKey;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import static net.minecraft.client.renderer.RenderStateShard.POSITION_COLOR_LIGHTMAP_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.POSITION_COLOR_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.POSITION_COLOR_TEX_LIGHTMAP_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.POSITION_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.POSITION_TEX_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ARMOR_CUTOUT_NO_CULL_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ARMOR_ENTITY_GLINT_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_BEACON_BEAM_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_BREEZE_WIND_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_CLOUDS_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_CRUMBLING_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_CUTOUT_MIPPED_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_CUTOUT_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_END_GATEWAY_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_END_PORTAL_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENERGY_SWIRL_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_ALPHA_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_NO_CULL_Z_OFFSET_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_DECAL_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_GLINT_DIRECT_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_GLINT_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_NO_OUTLINE_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_SHADOW_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_SMOOTH_CUTOUT_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_EYES_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_GLINT_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_GLINT_TRANSLUCENT_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_GUI_GHOST_RECIPE_OVERLAY_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_GUI_OVERLAY_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_GUI_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_GUI_TEXT_HIGHLIGHT_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ITEM_ENTITY_TRANSLUCENT_CULL_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_LEASH_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_LIGHTNING_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_LINES_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_OUTLINE_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_SOLID_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_TEXT_BACKGROUND_SEE_THROUGH_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_TEXT_BACKGROUND_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_TEXT_INTENSITY_SEE_THROUGH_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_TEXT_INTENSITY_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_TEXT_SEE_THROUGH_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_TEXT_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_TRANSLUCENT_MOVING_BLOCK_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_TRIPWIRE_SHADER;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_WATER_MASK_SHADER;
import static org.hismeo.haikalathost.client.material.MaterialFeature.ALPHA_CUTOUT;
import static org.hismeo.haikalathost.client.material.MaterialFeature.DOUBLE_SIDED;
import static org.hismeo.haikalathost.client.material.MaterialFeature.EMISSIVE;
import static org.hismeo.haikalathost.client.material.MaterialFeature.FOG;
import static org.hismeo.haikalathost.client.material.MaterialFeature.LIGHTMAP;
import static org.hismeo.haikalathost.client.material.MaterialFeature.OVERLAY;
import static org.hismeo.haikalathost.client.material.MaterialFeature.TEXTURED;
import static org.hismeo.haikalathost.client.material.MaterialFeature.VERTEX_COLOR;

/** Explicit shader-shard identity matrix for memoized/dynamic vanilla RenderTypes. */
final class DynamicRenderTypeClassifier {
    private final Map<RenderStateShard.ShaderStateShard, ShaderRoute> routes =
            new IdentityHashMap<>();
    private final TextureResourceRegistry textures;

    DynamicRenderTypeClassifier(TextureResourceRegistry textures) {
        this.textures = java.util.Objects.requireNonNull(textures, "textures");
        world(RENDERTYPE_SOLID_SHADER, ShaderFamily.WORLD_OPAQUE, PassKey.WORLD_OPAQUE, 0);
        world(RENDERTYPE_CUTOUT_MIPPED_SHADER, ShaderFamily.WORLD_CUTOUT,
                PassKey.WORLD_CUTOUT, ALPHA_CUTOUT.bit());
        world(RENDERTYPE_CUTOUT_SHADER, ShaderFamily.WORLD_CUTOUT,
                PassKey.WORLD_CUTOUT, ALPHA_CUTOUT.bit());
        world(RENDERTYPE_TRANSLUCENT_SHADER, ShaderFamily.WORLD_TRANSLUCENT,
                PassKey.WORLD_TRANSLUCENT, 0);
        world(RENDERTYPE_TRANSLUCENT_MOVING_BLOCK_SHADER, ShaderFamily.WORLD_TRANSLUCENT,
                PassKey.WORLD_TRANSLUCENT, 0);
        world(RENDERTYPE_TRIPWIRE_SHADER, ShaderFamily.WORLD_TRANSLUCENT,
                PassKey.WORLD_TRANSLUCENT, 0);
        world(RENDERTYPE_CRUMBLING_SHADER, ShaderFamily.WORLD_TRANSLUCENT,
                PassKey.WORLD_TRANSLUCENT, 0);

        entity(RENDERTYPE_ARMOR_CUTOUT_NO_CULL_SHADER, ShaderFamily.ENTITY_CUTOUT,
                PassKey.ENTITY_CUTOUT, ALPHA_CUTOUT.bit());
        entity(RENDERTYPE_ENTITY_SOLID_SHADER, ShaderFamily.ENTITY_OPAQUE, PassKey.ENTITY_OPAQUE, 0);
        entity(RENDERTYPE_ENTITY_CUTOUT_SHADER, ShaderFamily.ENTITY_CUTOUT,
                PassKey.ENTITY_CUTOUT, ALPHA_CUTOUT.bit());
        entity(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER, ShaderFamily.ENTITY_CUTOUT,
                PassKey.ENTITY_CUTOUT, ALPHA_CUTOUT.bit());
        entity(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_Z_OFFSET_SHADER, ShaderFamily.ENTITY_CUTOUT,
                PassKey.ENTITY_CUTOUT, ALPHA_CUTOUT.bit());
        entity(RENDERTYPE_ENTITY_SMOOTH_CUTOUT_SHADER, ShaderFamily.ENTITY_CUTOUT,
                PassKey.ENTITY_CUTOUT, ALPHA_CUTOUT.bit());
        entity(RENDERTYPE_ITEM_ENTITY_TRANSLUCENT_CULL_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.ENTITY_TRANSLUCENT, 0);
        entity(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.ENTITY_TRANSLUCENT, 0);
        entity(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.ENTITY_TRANSLUCENT, 0);
        entity(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.ENTITY_TRANSLUCENT, EMISSIVE.bit());
        entity(RENDERTYPE_BEACON_BEAM_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.ENTITY_TRANSLUCENT, 0);
        entity(RENDERTYPE_ENTITY_DECAL_SHADER, ShaderFamily.ENTITY_OPAQUE, PassKey.ENTITY_OPAQUE, 0);
        entity(RENDERTYPE_ENTITY_NO_OUTLINE_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.ENTITY_TRANSLUCENT, 0);
        entity(RENDERTYPE_ENTITY_SHADOW_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.ENTITY_TRANSLUCENT, 0);
        entity(RENDERTYPE_ENTITY_ALPHA_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.ENTITY_TRANSLUCENT, 0);
        entity(RENDERTYPE_EYES_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.ENTITY_TRANSLUCENT, EMISSIVE.bit());
        entity(RENDERTYPE_ENERGY_SWIRL_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.ENTITY_TRANSLUCENT, EMISSIVE.bit());
        entity(RENDERTYPE_BREEZE_WIND_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.ENTITY_TRANSLUCENT, 0);
        entity(RENDERTYPE_LEASH_SHADER, ShaderFamily.ENTITY_OPAQUE, PassKey.ENTITY_OPAQUE, 0);
        entity(RENDERTYPE_WATER_MASK_SHADER, ShaderFamily.ENTITY_OPAQUE, PassKey.ENTITY_OPAQUE, 0);
        entity(RENDERTYPE_LIGHTNING_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.ENTITY_TRANSLUCENT, 0);

        route(RENDERTYPE_OUTLINE_SHADER, ShaderFamily.OUTLINE, PassKey.OUTLINE, 0, 0.0F);
        route(RENDERTYPE_ARMOR_ENTITY_GLINT_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.FIRST_PERSON, 0, 0.0F);
        route(RENDERTYPE_GLINT_TRANSLUCENT_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.FIRST_PERSON, 0, 0.0F);
        route(RENDERTYPE_GLINT_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.FIRST_PERSON, 0, 0.0F);
        route(RENDERTYPE_ENTITY_GLINT_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.FIRST_PERSON, 0, 0.0F);
        route(RENDERTYPE_ENTITY_GLINT_DIRECT_SHADER, ShaderFamily.ENTITY_TRANSLUCENT,
                PassKey.FIRST_PERSON, 0, 0.0F);

        text(RENDERTYPE_TEXT_SHADER);
        text(RENDERTYPE_TEXT_BACKGROUND_SHADER);
        text(RENDERTYPE_TEXT_INTENSITY_SHADER);
        text(RENDERTYPE_TEXT_SEE_THROUGH_SHADER);
        text(RENDERTYPE_TEXT_BACKGROUND_SEE_THROUGH_SHADER);
        text(RENDERTYPE_TEXT_INTENSITY_SEE_THROUGH_SHADER);

        route(RENDERTYPE_CLOUDS_SHADER, ShaderFamily.CLOUD, PassKey.CLOUD, 0, 0.0F);
        route(RENDERTYPE_END_PORTAL_SHADER, ShaderFamily.SKY, PassKey.SKY, 0, 0.0F);
        route(RENDERTYPE_END_GATEWAY_SHADER, ShaderFamily.SKY, PassKey.SKY, 0, 0.0F);
        route(RENDERTYPE_LINES_SHADER, ShaderFamily.LINE, PassKey.LINE, 0, 0.0F);
        route(RENDERTYPE_GUI_SHADER, ShaderFamily.UI, PassKey.UI, 0, 0.0F);
        route(RENDERTYPE_GUI_OVERLAY_SHADER, ShaderFamily.UI, PassKey.UI, 0, 0.0F);
        route(RENDERTYPE_GUI_TEXT_HIGHLIGHT_SHADER, ShaderFamily.UI, PassKey.UI, 0, 0.0F);
        route(RENDERTYPE_GUI_GHOST_RECIPE_OVERLAY_SHADER, ShaderFamily.UI, PassKey.UI, 0, 0.0F);

        route(POSITION_SHADER, ShaderFamily.SKY, PassKey.SKY, 0, 0.0F);
        route(POSITION_TEX_SHADER, ShaderFamily.UI, PassKey.UI, 0, 0.0F);
        route(POSITION_COLOR_SHADER, ShaderFamily.LINE, PassKey.LINE, 0, 0.0F);
        route(POSITION_COLOR_LIGHTMAP_SHADER, ShaderFamily.ENTITY_OPAQUE,
                PassKey.ENTITY_OPAQUE, 0, 0.0F);
        route(POSITION_COLOR_TEX_LIGHTMAP_SHADER, ShaderFamily.TEXT, PassKey.TEXT, 0, 0.0F);
    }

    ClassifiedMaterial classify(RenderType renderType) {
        if (!(renderType instanceof CompositeRenderTypeAccessor composite)) {
            throw new UnknownRenderTypeException(renderType);
        }
        RenderType.CompositeState state = composite.haikalatHost$state();
        RenderTypeCompositeStateAccessor stateView = (RenderTypeCompositeStateAccessor) (Object) state;
        ShaderRoute route = routes.get(stateView.haikalatHost$shaderState());
        if (route == null) throw new UnknownRenderTypeException(renderType);

        int features = route.baseFeatures;
        Optional<ResourceLocation> texture =
                ((RenderStateTextureInvoker) (Object) stateView.haikalatHost$textureState())
                        .haikalatHost$cutoutTexture();
        int textureId = texture.map(textures::resolve).orElse(-1);
        if (texture.isPresent()) features |= TEXTURED.bit();
        if (renderType.format().hasColor()) features |= VERTEX_COLOR.bit();
        if (enabled(stateView.haikalatHost$lightmapState())) features |= LIGHTMAP.bit();
        if (enabled(stateView.haikalatHost$overlayState())) features |= OVERLAY.bit();
        if (!enabled(stateView.haikalatHost$cullState())) features |= DOUBLE_SIDED.bit();

        MaterialKey material = new MaterialKey(
                route.family,
                features,
                textureId < 0 ? 0L : Integer.toUnsignedLong(textureId),
                0L,
                textureId,
                0,
                route.alphaCutoff,
                0);
        return new ClassifiedMaterial(route.pass, material);
    }

    void clear() {
        // Shader routes are immutable. The owning registry clears the shared texture table.
    }

    private static boolean enabled(RenderStateShard.BooleanStateShard state) {
        return ((RenderStateBooleanAccessor) (Object) state).haikalatHost$enabled();
    }

    private void world(
            RenderStateShard.ShaderStateShard shader,
            ShaderFamily family,
            PassKey pass,
            int features
    ) {
        route(shader, family, pass, features | FOG.bit(), cutoff(features));
    }

    private void entity(
            RenderStateShard.ShaderStateShard shader,
            ShaderFamily family,
            PassKey pass,
            int features
    ) {
        route(shader, family, pass, features | FOG.bit(), cutoff(features));
    }

    private void text(RenderStateShard.ShaderStateShard shader) {
        route(shader, ShaderFamily.TEXT, PassKey.TEXT, 0, 0.0F);
    }

    private void route(
            RenderStateShard.ShaderStateShard shader,
            ShaderFamily family,
            PassKey pass,
            int features,
            float alphaCutoff
    ) {
        ShaderRoute previous = routes.put(
                shader, new ShaderRoute(family, pass, features, alphaCutoff));
        if (previous != null) throw new IllegalStateException("duplicate shader route");
    }

    private static float cutoff(int features) {
        return (features & ALPHA_CUTOUT.bit()) != 0 ? 0.5F : 0.0F;
    }

    record ClassifiedMaterial(PassKey pass, MaterialKey material) {
    }

    private record ShaderRoute(
            ShaderFamily family, PassKey pass, int baseFeatures, float alphaCutoff) {
    }
}
