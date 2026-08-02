package org.hismeo.fractureclient.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;

/** Raises the final lightmap without discarding block tint, directional shade, or AO colour. */
public final class CutawayLightVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final int minimumBlockUv;
    private final int minimumSkyUv;

    public CutawayLightVertexConsumer(VertexConsumer delegate, int minimumPackedLight) {
        this.delegate = delegate;
        this.minimumBlockUv = LightTexture.block(minimumPackedLight) << 4;
        this.minimumSkyUv = LightTexture.sky(minimumPackedLight) << 4;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(Math.max(u, minimumBlockUv), Math.max(v, minimumSkyUv));
        return this;
    }

    @Override
    public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
        delegate.setNormal(normalX, normalY, normalZ);
        return this;
    }
}
