package org.hismeo.bendsanimator.client.mixin.model;

import net.minecraft.client.model.TadpoleModel;
import net.minecraft.client.model.geom.ModelPart;
import org.hismeo.bendsanimator.client.api.IEntityModelGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TadpoleModel.class)
public class TadpoleModelMixin implements IEntityModelGetter {
    @Shadow @Final private ModelPart root;

    @Override
    public ModelPart getRoot() {
        return this.root;
    }
}
