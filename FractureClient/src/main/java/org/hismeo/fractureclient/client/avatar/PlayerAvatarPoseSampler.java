package org.hismeo.fractureclient.client.avatar;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Objects;

/** Samples the small transform-only pose required by milestone one. */
public final class PlayerAvatarPoseSampler {
    public static final float DEFAULT_SCALE = 1.0F;
    /** The supplied Blockbench exports extend one pixel below their root plane. */
    public static final float DEFAULT_FOOT_OFFSET = 1.0F / 64.0F;

    public PlayerAvatarPose sample(Player player, float partialTick) {
        Objects.requireNonNull(player, "player");
        float alpha = Mth.clamp(partialTick, 0.0F, 1.0F);
        Vec3 position = player.getPosition(alpha);
        return new PlayerAvatarPose(
                new Vector3f((float) position.x, (float) position.y, (float) position.z),
                Mth.rotLerp(alpha, player.yBodyRotO, player.yBodyRot),
                DEFAULT_SCALE,
                DEFAULT_FOOT_OFFSET);
    }

    public record PlayerAvatarPose(
            Vector3f position,
            float bodyYaw,
            float scale,
            float footOffset
    ) {
        public PlayerAvatarPose {
            position = new Vector3f(Objects.requireNonNull(position, "position"));
            if (!Float.isFinite(bodyYaw)
                    || !Float.isFinite(scale)
                    || scale <= 0.0F
                    || !Float.isFinite(footOffset)) {
                throw new IllegalArgumentException("avatar pose must be finite and scale positive");
            }
        }

        /** Minecraft and these Blockbench exports are both Y-up; only yaw sign/origin need mapping. */
        public Matrix4f modelMatrix(Matrix4f destination) {
            Objects.requireNonNull(destination, "destination");
            return destination.identity()
                    .translate(position.x, position.y + footOffset, position.z)
                    .rotateY((float) Math.toRadians(180.0F - bodyYaw))
                    .scale(scale);
        }
    }
}
