package org.hismeo.fractureclient.client.weapon;

import org.hismeo.haikalathost.api.client.advanced.HaikalatFrameContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatRenderExtension;

/** Draws the debug preview after world-space post-processing has finished. */
public final class WeaponPreviewRenderExtension implements HaikalatRenderExtension {
    @Override
    public void render(HaikalatFrameContext frame) {
        PlayerWeaponExtension.renderPreviewPass(frame);
    }
}
