package org.hismeo.crystallib.common.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public class HitResultUtil {
    public static EntityHitResult lookAtEntity(Player player){
        HitResult hitResult = getPlayerLookAtResult(player.level(), player);
        if (hitResult instanceof EntityHitResult entityHitResult) {
            return entityHitResult;
        }
        return null;
    }

    public static BlockState lookAtBlock(Player player){
        HitResult hitResult = getPlayerLookAtResult(player.level(), player);
        if (hitResult instanceof BlockHitResult blockHitResult) {
            return player.level().getBlockState(blockHitResult.getBlockPos());
        }
        return null;
    }

    public static HitResult getPlayerLookAtResult(Level level, Player player){
        return getPlayerLookAtResult(level, player, 3);
    }

    public static HitResult getPlayerLookAtResult(Level level, Player player, double range){
        return getPlayerLookAtResult(level, player, range, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY);
    }

    public static HitResult getPlayerLookAtResult(Level level, Player player, double range, ClipContext.Block blockType, ClipContext.Fluid fluidType) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 viewVector = player.getViewVector(1.0f);
        Vec3 endPosition = eyePosition.add(viewVector.scale(range));

        HitResult hitResult = ProjectileUtil.getEntityHitResult(level,
                player,
                eyePosition,
                endPosition,
                new AABB(eyePosition, endPosition)
                        .expandTowards(viewVector.scale(range)),
                entity -> !entity.isSpectator(),
                0.0f
        );
        if (hitResult == null) {
                hitResult = level.clip(new ClipContext(
                    eyePosition,
                    endPosition,
                    blockType,
                    fluidType,
                    player
            ));
        }
        return hitResult;
    }
}