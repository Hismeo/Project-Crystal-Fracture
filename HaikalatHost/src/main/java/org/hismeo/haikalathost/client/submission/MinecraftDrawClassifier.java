package org.hismeo.haikalathost.client.submission;

import net.minecraft.client.renderer.RenderType;

final class MinecraftDrawClassifier {
    private MinecraftDrawClassifier() {
    }

    static Classification classify(RenderType renderType) {
        if (renderType == RenderType.solid()) {
            return new Classification(MinecraftPass.WORLD_OPAQUE, OrderDomain.OPAQUE);
        }
        if (renderType == RenderType.cutoutMipped() || renderType == RenderType.cutout()) {
            return new Classification(MinecraftPass.WORLD_CUTOUT, OrderDomain.CUTOUT);
        }
        if (renderType == RenderType.translucent()
                || renderType == RenderType.translucentMovingBlock()
                || renderType == RenderType.tripwire()) {
            return new Classification(MinecraftPass.WORLD_TRANSLUCENT, OrderDomain.TRANSLUCENT);
        }
        if (renderType == RenderType.armorEntityGlint()
                || renderType == RenderType.glintTranslucent()
                || renderType == RenderType.glint()
                || renderType == RenderType.entityGlint()
                || renderType == RenderType.entityGlintDirect()) {
            return new Classification(MinecraftPass.STABLE_GLINT, OrderDomain.GLINT);
        }
        if (renderType == RenderType.gui()
                || renderType == RenderType.guiOverlay()
                || renderType == RenderType.guiTextHighlight()
                || renderType == RenderType.guiGhostRecipeOverlay()
                || renderType == RenderType.textBackground()
                || renderType == RenderType.textBackgroundSeeThrough()) {
            return new Classification(MinecraftPass.STABLE_UI, OrderDomain.UI);
        }

        // Dynamic entity/text types can include first-person or third-party state. Unknown is a stable barrier.
        return new Classification(MinecraftPass.STABLE_COMPAT, OrderDomain.UNKNOWN_STABLE);
    }

    record Classification(MinecraftPass pass, OrderDomain domain) {
    }
}
