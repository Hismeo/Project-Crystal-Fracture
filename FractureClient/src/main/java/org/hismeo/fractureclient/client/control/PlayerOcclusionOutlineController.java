package org.hismeo.fractureclient.client.control;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.hismeo.fractureclient.client.config.OctopathVisualConfig;

/**
 * Bridges the conservative block-culling fallback to Minecraft's real entity-outline pass.
 *
 * <p>The outline pass renders the animated player model into a separate, depth-independent
 * target, so it retains the exact skin, armour and pose silhouette even when terrain completely
 * hides the player from the scene colour and depth buffers.</p>
 */
public final class PlayerOcclusionOutlineController {
    private PlayerOcclusionOutlineController() {
    }

    public static boolean shouldOutline(Entity entity) {
        if (!CameraModeController.isOrthographic()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!OctopathVisualConfig.playerOcclusionIndicator
                || minecraft.level == null
                || minecraft.player == null
                || entity != minecraft.player) {
            return false;
        }

        float threshold = Math.max(0.0F, Math.min(
                1.0F,
                OctopathVisualConfig.playerOcclusionOutlineThreshold
        ));
        return BlockCullController.getFallbackStrength() >= threshold;
    }

    public static int getOutlineColor() {
        return OctopathVisualConfig.playerOcclusionOutlineColor & 0x00FFFFFF;
    }
}
