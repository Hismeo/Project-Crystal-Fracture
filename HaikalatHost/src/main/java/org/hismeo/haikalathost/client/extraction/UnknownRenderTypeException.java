package org.hismeo.haikalathost.client.extraction;

import net.minecraft.client.renderer.RenderType;

public final class UnknownRenderTypeException extends IllegalStateException {
    public UnknownRenderTypeException(RenderType renderType) {
        super("Unregistered RenderType structure: class=" + renderType.getClass().getName()
                + ", format=" + renderType.format()
                + ", mode=" + renderType.mode()
                + ", sortOnUpload=" + renderType.sortOnUpload()
                + ", affectsCrumbling=" + renderType.affectsCrumbling());
    }
}
