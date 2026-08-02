package org.hismeo.fractureclient.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.hismeo.fractureclient.client.control.CameraModeController;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {
    protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public boolean isSprinting() {
        if (CameraModeController.isOrthographic()) {
            this.setSharedFlag(3, true);
            return true;
        }
        return super.isSprinting();
    }
}
