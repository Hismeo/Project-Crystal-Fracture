package org.hismeo.fractureclient.client.render.octopath;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.BiomeTags;
import org.hismeo.fractureclient.client.config.OctopathVisualConfig;

/**
 * Chooses an intentionally small palette set and makes the automatic choice temporally stable.
 * A player straddling a biome border should never make the entire frame flicker green/blue each
 * tick, so automatic choices need a short confirmation before they begin their cross-fade.
 */
final class OctopathToneSelector {
    private static final int AUTO_DEBOUNCE_TICKS = 12;

    private ClientLevel lastLevel;
    private OctopathTonePreset activePreset = OctopathTonePreset.DAY;
    private OctopathTonePreset pendingPreset;
    private long pendingSince = Long.MIN_VALUE;
    private OctopathToneProfile transitionFrom = OctopathToneProfile.forPreset(OctopathTonePreset.DAY);
    private long transitionStarted = Long.MIN_VALUE;
    private int transitionDuration = 1;

    OctopathToneProfile select(
            ClientLevel level,
            LocalPlayer player,
            float daylight,
            float twilight,
            OctopathVisualConfig.TonePresetMode configuredMode,
            int configuredTransitionTicks
    ) {
        long gameTime = level.getGameTime();
        int transitionTicks = clamp(configuredTransitionTicks, 1, 200);
        OctopathTonePreset desired = desiredPreset(
                level,
                player,
                daylight,
                twilight,
                configuredMode);

        if (level != lastLevel) {
            lastLevel = level;
            activePreset = desired;
            pendingPreset = null;
            pendingSince = Long.MIN_VALUE;
            transitionFrom = OctopathToneProfile.forPreset(desired);
            transitionStarted = Long.MIN_VALUE;
            transitionDuration = transitionTicks;
            return transitionFrom;
        }

        if (desired == activePreset) {
            pendingPreset = null;
            pendingSince = Long.MIN_VALUE;
        } else if (configuredMode != OctopathVisualConfig.TonePresetMode.AUTO) {
            promote(desired, gameTime, transitionTicks);
        } else if (desired != pendingPreset) {
            pendingPreset = desired;
            pendingSince = gameTime;
        } else if (gameTime - pendingSince >= AUTO_DEBOUNCE_TICKS) {
            promote(desired, gameTime, transitionTicks);
        }

        return currentProfile(gameTime);
    }

    void reset() {
        lastLevel = null;
        activePreset = OctopathTonePreset.DAY;
        pendingPreset = null;
        pendingSince = Long.MIN_VALUE;
        transitionFrom = OctopathToneProfile.forPreset(OctopathTonePreset.DAY);
        transitionStarted = Long.MIN_VALUE;
        transitionDuration = 1;
    }

    private void promote(OctopathTonePreset desired, long gameTime, int transitionTicks) {
        transitionFrom = currentProfile(gameTime);
        activePreset = desired;
        pendingPreset = null;
        pendingSince = Long.MIN_VALUE;
        transitionStarted = gameTime;
        transitionDuration = transitionTicks;
    }

    private OctopathToneProfile currentProfile(long gameTime) {
        OctopathToneProfile target = OctopathToneProfile.forPreset(activePreset);
        if (transitionStarted == Long.MIN_VALUE) {
            return target;
        }

        float progress = (gameTime - transitionStarted) / (float) Math.max(1, transitionDuration);
        if (progress >= 1.0F) {
            transitionStarted = Long.MIN_VALUE;
            transitionFrom = target;
            return target;
        }
        float smoothProgress = smoothstep(progress);
        return OctopathToneProfile.lerp(transitionFrom, target, smoothProgress);
    }

    private static OctopathTonePreset desiredPreset(
            ClientLevel level,
            LocalPlayer player,
            float daylight,
            float twilight,
            OctopathVisualConfig.TonePresetMode configuredMode
    ) {
        if (configuredMode != null && configuredMode != OctopathVisualConfig.TonePresetMode.AUTO) {
            return switch (configuredMode) {
                case DAY -> OctopathTonePreset.DAY;
                case FOREST -> OctopathTonePreset.FOREST;
                case NIGHT -> OctopathTonePreset.NIGHT;
                case AUTO -> throw new IllegalStateException("AUTO mode was handled above");
            };
        }
        if (!level.dimensionType().hasSkyLight() || (daylight < 0.08F && twilight < 0.35F)) {
            return OctopathTonePreset.NIGHT;
        }

        var biome = level.getBiome(player.blockPosition());
        if (daylight >= 0.18F && (biome.is(BiomeTags.IS_FOREST)
                || biome.is(BiomeTags.IS_JUNGLE)
                || biome.is(BiomeTags.IS_TAIGA))) {
            return OctopathTonePreset.FOREST;
        }
        return OctopathTonePreset.DAY;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float smoothstep(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        return t * t * (3.0F - 2.0F * t);
    }
}
