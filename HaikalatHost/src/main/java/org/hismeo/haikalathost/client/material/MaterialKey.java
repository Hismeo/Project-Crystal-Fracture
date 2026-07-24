package org.hismeo.haikalathost.client.material;

import java.util.Objects;

/**
 * Stable, Minecraft-independent material identity.
 *
 * <p>Texture handles are opaque Host resource identities. In non-bindless mode texturePage is used
 * as the batching page and the handles are resolved by the texture table.</p>
 */
public record MaterialKey(
        ShaderFamily shaderFamily,
        int features,
        long baseColorHandle,
        long normalHandle,
        int texturePage,
        int samplerId,
        float alphaCutoff,
        int tintMode
) {
    public MaterialKey {
        Objects.requireNonNull(shaderFamily, "shaderFamily");
        if ((features & ~MaterialFeature.ALL_MASK) != 0) {
            throw new IllegalArgumentException("unknown material feature bits: " + features);
        }
        if (texturePage < -1) throw new IllegalArgumentException("texturePage must be -1 or greater");
        if (samplerId < 0) throw new IllegalArgumentException("samplerId must not be negative");
        if (!Float.isFinite(alphaCutoff) || alphaCutoff < 0.0F || alphaCutoff > 1.0F) {
            throw new IllegalArgumentException("alphaCutoff must be finite and in [0, 1]");
        }
        boolean cutout = MaterialFeature.contains(features, MaterialFeature.ALPHA_CUTOUT);
        if (!cutout && alphaCutoff != 0.0F) {
            throw new IllegalArgumentException("alphaCutoff requires ALPHA_CUTOUT");
        }
    }

    public GpuMaterial gpuMaterial() {
        return new GpuMaterial(
                baseColorHandle, normalHandle, samplerId, features, alphaCutoff, tintMode);
    }
}
