package org.hismeo.bendsanimator.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import org.hismeo.bendsanimator.client.api.IEntityModelGetter;
import org.jetbrains.annotations.Nullable;

public class ModelUtil {
    private static final Minecraft mc = Minecraft.getInstance();

    public static @Nullable ModelPart getEntityModelPart(Entity entity) {
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        EntityRenderer<? extends Entity> renderer = dispatcher.getRenderer(entity);
        if (renderer instanceof LivingEntityRenderer<?, ?> livingRenderer) {
            return ((IEntityModelGetter) livingRenderer.getModel()).getRoot();
        }
        return null;
    }
}
