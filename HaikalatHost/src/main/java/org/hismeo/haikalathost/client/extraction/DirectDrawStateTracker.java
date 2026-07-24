package org.hismeo.haikalathost.client.extraction;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.client.submission.PassKey;

/**
 * CPU-side mirror of the small part of RenderSystem state needed to classify direct MeshData
 * submissions. It never reads a Minecraft GL texture identifier.
 */
public final class DirectDrawStateTracker {
    private static final int TEXTURE_SLOTS = 12;
    private static final DirectPassOverrideStack PASS_OVERRIDES = new DirectPassOverrideStack(8);
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[TEXTURE_SLOTS];

    private DirectDrawStateTracker() {
    }

    public static void setTexture(int slot, ResourceLocation texture) {
        if (validSlot(slot)) TEXTURES[slot] = texture;
    }

    public static void setUnresolvedTexture(int slot) {
        if (validSlot(slot)) TEXTURES[slot] = null;
    }

    public static ResourceLocation texture(int slot) {
        return validSlot(slot) ? TEXTURES[slot] : null;
    }

    public static void pushPassOverride(PassKey pass) { PASS_OVERRIDES.push(pass); }

    public static void popPassOverride(PassKey expected) { PASS_OVERRIDES.pop(expected); }

    public static PassKey passOverride() { return PASS_OVERRIDES.current(); }

    public static void clear() {
        java.util.Arrays.fill(TEXTURES, null);
        clearPassOverrides();
    }

    public static void clearPassOverrides() {
        PASS_OVERRIDES.clear();
    }

    private static boolean validSlot(int slot) {
        return slot >= 0 && slot < TEXTURE_SLOTS;
    }
}
