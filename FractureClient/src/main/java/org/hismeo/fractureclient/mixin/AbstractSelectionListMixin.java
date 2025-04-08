package org.hismeo.fractureclient.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractSelectionList.class)
public class AbstractSelectionListMixin {
    @Definition(id = "renderTopAndBottom", field = "Lnet/minecraft/client/gui/components/AbstractSelectionList;renderTopAndBottom:Z")
    @Expression("this.renderTopAndBottom")
    @ModifyExpressionValue(method = "render", at = @At(value = "MIXINEXTRAS:EXPRESSION"))
    public boolean cancelRenderBackGround(boolean original){
        return false;
    }
}
