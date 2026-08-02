package org.hismeo.fractureclient.client.render;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;
import org.hismeo.fractureclient.client.control.BlockCullController;
import org.jetbrains.annotations.Nullable;

/** Virtual block view used only while compiling a block beside the cutaway boundary. */
public final class CutawayLightBlockAndTintGetter implements BlockAndTintGetter {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final BlockAndTintGetter delegate;
    private final int minimumBlockLight;
    private final int minimumSkyLight;

    public CutawayLightBlockAndTintGetter(BlockAndTintGetter delegate, int minimumPackedLight) {
        this.delegate = delegate;
        this.minimumBlockLight = LightTexture.block(minimumPackedLight);
        this.minimumSkyLight = LightTexture.sky(minimumPackedLight);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return isVirtuallyRemoved(pos) ? null : delegate.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return isVirtuallyRemoved(pos) ? AIR : delegate.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return isVirtuallyRemoved(pos) ? AIR.getFluidState() : delegate.getFluidState(pos);
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return delegate.getShade(direction, shade);
    }

    @Override
    public float getShade(float normalX, float normalY, float normalZ, boolean shade) {
        return delegate.getShade(normalX, normalY, normalZ, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return delegate.getLightEngine();
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return delegate.getBlockTint(pos, resolver);
    }

    @Override
    public int getBrightness(LightLayer lightLayer, BlockPos pos) {
        int original = delegate.getBrightness(lightLayer, pos);
        if (!isVirtuallyRemoved(pos)) {
            return original;
        }
        int minimum = lightLayer == LightLayer.BLOCK ? minimumBlockLight : minimumSkyLight;
        return Math.max(original, minimum);
    }

    @Override
    public int getRawBrightness(BlockPos pos, int ambientDarkening) {
        int original = delegate.getRawBrightness(pos, ambientDarkening);
        if (!isVirtuallyRemoved(pos)) {
            return original;
        }
        return Math.max(
                original,
                Math.max(minimumBlockLight, minimumSkyLight - ambientDarkening)
        );
    }

    @Override
    public int getMinBuildHeight() {
        return delegate.getMinBuildHeight();
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public ModelData getModelData(BlockPos pos) {
        return delegate.getModelData(pos);
    }

    @Nullable
    @Override
    public AuxiliaryLightManager getAuxLightManager(ChunkPos pos) {
        return delegate.getAuxLightManager(pos);
    }

    private static boolean isVirtuallyRemoved(BlockPos pos) {
        return BlockCullController.isCulledDuringCompilation(pos);
    }
}
