package org.hismeo.fractureclient.client.render.octopath;

import org.joml.Vector3f;

/**
 * Luminance-neutral colour tints used to steer the scene into a stable HD-2D mood. Keeping the
 * profile as tints lets the shader preserve brightness instead of solving a colour problem by
 * raising exposure.
 */
record OctopathToneProfile(
        Vector3f midTint,
        Vector3f shadowTint,
        Vector3f highlightTint,
        Vector3f fogTint,
        Vector3f skyTint,
        float vibranceMultiplier
) {
    private static final OctopathToneProfile DAY = new OctopathToneProfile(
            new Vector3f(1.03F, 1.00F, 0.94F),
            new Vector3f(0.79F, 0.88F, 1.11F),
            new Vector3f(1.13F, 0.98F, 0.78F),
            new Vector3f(0.92F, 0.97F, 1.05F),
            new Vector3f(0.96F, 1.00F, 1.06F),
            0.92F);
    private static final OctopathToneProfile FOREST = new OctopathToneProfile(
            new Vector3f(0.97F, 1.04F, 0.93F),
            new Vector3f(0.71F, 0.86F, 1.04F),
            new Vector3f(1.16F, 1.04F, 0.74F),
            new Vector3f(0.80F, 0.94F, 0.91F),
            new Vector3f(0.92F, 1.03F, 0.95F),
            0.88F);
    private static final OctopathToneProfile NIGHT = new OctopathToneProfile(
            new Vector3f(0.87F, 0.94F, 1.15F),
            new Vector3f(0.68F, 0.78F, 1.13F),
            new Vector3f(1.24F, 0.80F, 0.50F),
            new Vector3f(0.72F, 0.80F, 1.08F),
            new Vector3f(0.80F, 0.88F, 1.16F),
            1.00F);

    OctopathToneProfile {
        midTint = new Vector3f(midTint);
        shadowTint = new Vector3f(shadowTint);
        highlightTint = new Vector3f(highlightTint);
        fogTint = new Vector3f(fogTint);
        skyTint = new Vector3f(skyTint);
        vibranceMultiplier = clamp(vibranceMultiplier, 0.0F, 2.0F);
    }

    static OctopathToneProfile forPreset(OctopathTonePreset preset) {
        return switch (preset) {
            case DAY -> DAY;
            case FOREST -> FOREST;
            case NIGHT -> NIGHT;
        };
    }

    static OctopathToneProfile lerp(
            OctopathToneProfile from,
            OctopathToneProfile to,
            float progress
    ) {
        float t = clamp(progress, 0.0F, 1.0F);
        return new OctopathToneProfile(
                new Vector3f(from.midTint).lerp(to.midTint, t),
                new Vector3f(from.shadowTint).lerp(to.shadowTint, t),
                new Vector3f(from.highlightTint).lerp(to.highlightTint, t),
                new Vector3f(from.fogTint).lerp(to.fogTint, t),
                new Vector3f(from.skyTint).lerp(to.skyTint, t),
                from.vibranceMultiplier + (to.vibranceMultiplier - from.vibranceMultiplier) * t);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
