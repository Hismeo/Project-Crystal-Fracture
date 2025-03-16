package org.hismeo.bendsanimator.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import org.hismeo.bendsanimator.client.BendsAnimator;

public class BendsUtil {
    private static final Minecraft mc = Minecraft.getInstance();
    public static void getModel(Entity entity){
//        mc.getEntityRenderDispatcher().getRenderer(entity).entityRenderDispatcher.entityModels.roots.get().mesh.getRoot().getChild();
        EntityRenderDispatcher entityRenderDispatcher = mc.getEntityRenderDispatcher().getRenderer(entity).entityRenderDispatcher;
        entityRenderDispatcher.entityModels.roots.forEach((modelLayerLocation, layerDefinition) -> {
            BendsAnimator.LOGGER.info(modelLayerLocation.getModel().toString());
        });
        ModelLayers.getKnownLocations();
    }
}
