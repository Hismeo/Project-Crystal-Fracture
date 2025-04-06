package org.hismeo.bendsanimator.client.mixin.model;

import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import org.hismeo.bendsanimator.client.api.IEntityModelGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {AxolotlModel.class,
        BeeModel.class,
        BoatModel.class,
        ChickenModel.class,
        EnderDragonRenderer.DragonModel.class,
        ElytraModel.class,
        FoxModel.class,
        HoglinModel.class,
        HorseModel.class,
        LlamaModel.class,
        OcelotModel.class,
        RabbitModel.class,
        RaftModel.class,
        ShulkerModel.class,
        TadpoleModel.class,
        WolfModel.class
})
public class AxolotlModelMixin implements IEntityModelGetter {
    @Unique
    private ModelPart root;
    @Override
    public ModelPart getRoot() {
        return root;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    public void setRoot(ModelPart root, CallbackInfo ci){
        this.root = root;
    }
}
