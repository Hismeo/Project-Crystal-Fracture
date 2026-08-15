package org.hismeo.fractureclient.client.avatar;

import com.kaleblangley.haikalat.subsystems.render3d.gltf.GltfSceneInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Selects and advances the supplied player clips without consulting the vanilla player model. */
public final class PlayerAvatarAnimator {
    private static final double MOVEMENT_EPSILON_SQUARED = 1.0E-5;
    private static final float MAX_FRAME_DELTA_SECONDS = 0.25F;

    private final GltfSceneInstance character;
    private final Map<PlayerAvatarAnimation, Integer> clipIndices;
    private final int rootMotionJoint;
    private final Vector3f rootMotionBindTranslation;
    private PlayerAvatarAnimation current;
    private boolean dashLocked;

    public PlayerAvatarAnimator(GltfSceneInstance character) {
        this.character = Objects.requireNonNull(character, "character");
        this.clipIndices = resolveClips(character.animationNames());
        this.rootMotionJoint = findJoint(character, "bone");
        this.rootMotionBindTranslation = new Vector3f(character.animationSkeleton()
                .joint(rootMotionJoint).bindTransform().translation());
    }

    public void update(Player player, float deltaSeconds) {
        Objects.requireNonNull(player, "player");
        float safeDelta = Float.isFinite(deltaSeconds)
                ? Math.clamp(deltaSeconds, 0.0F, MAX_FRAME_DELTA_SECONDS)
                : 0.0F;

        if (dashLocked && dashFinished()) {
            dashLocked = false;
        }
        if (!dashLocked) {
            playIfChanged(select(player));
        }
        character.update(safeDelta);
    }

    /** Plays the authored one-shot dash; callers can bind this to an authoritative action later. */
    public void playDash() {
        playDash(0.0F);
    }

    public void playDash(float timeSeconds) {
        play(PlayerAvatarAnimation.DASH);
        float duration = character.animationClip(
                clipIndices.get(PlayerAvatarAnimation.DASH)).durationSeconds();
        if (Float.isFinite(timeSeconds) && timeSeconds > 0.0F) {
            character.seek(Math.min(timeSeconds, duration));
        }
        dashLocked = true;
    }

    /** Immediately releases an authoritative dash one-shot; locomotion resumes on the next update. */
    public void stopDash() {
        if (dashLocked || current == PlayerAvatarAnimation.DASH) {
            dashLocked = false;
            current = null;
        }
    }

    /** Cancels visual root translation because the Minecraft entity already consumes it. */
    public Vector3f rootMotionCompensation(Vector3f destination) {
        Objects.requireNonNull(destination, "destination").zero();
        if (current != PlayerAvatarAnimation.DASH) {
            return destination;
        }
        Vector3fc translation = character.pose().localTransform(rootMotionJoint).translation();
        return destination.set(translation).sub(rootMotionBindTranslation).setComponent(1, 0.0F);
    }

    public PlayerAvatarAnimation current() {
        return current;
    }

    private PlayerAvatarAnimation select(Player player) {
        if (!player.onGround()) {
            return PlayerAvatarAnimation.JUMP;
        }
        Vec3 velocity = player.getDeltaMovement();
        boolean moving = velocity.x * velocity.x + velocity.z * velocity.z
                > MOVEMENT_EPSILON_SQUARED;
        if (!moving) {
            return PlayerAvatarAnimation.STAND;
        }
        return player.isSprinting() ? PlayerAvatarAnimation.RUN : PlayerAvatarAnimation.MOVE;
    }

    private void playIfChanged(PlayerAvatarAnimation next) {
        if (current != next) {
            play(next);
        }
    }

    private void play(PlayerAvatarAnimation animation) {
        character.play(clipIndices.get(animation), animation.loopMode());
        current = animation;
    }

    private boolean dashFinished() {
        int dashIndex = clipIndices.get(PlayerAvatarAnimation.DASH);
        return character.animationTimeSeconds()
                >= character.animationClip(dashIndex).durationSeconds();
    }

    private static Map<PlayerAvatarAnimation, Integer> resolveClips(List<String> names) {
        EnumMap<PlayerAvatarAnimation, Integer> result =
                new EnumMap<>(PlayerAvatarAnimation.class);
        for (PlayerAvatarAnimation animation : PlayerAvatarAnimation.values()) {
            int index = names.indexOf(animation.clipName());
            if (index < 0) {
                throw new IllegalArgumentException(
                        "Player animation library is missing clip " + animation.clipName());
            }
            result.put(animation, index);
        }
        return Map.copyOf(result);
    }

    private static int findJoint(GltfSceneInstance character, String name) {
        for (int index = 0; index < character.animationSkeleton().jointCount(); index++) {
            if (name.equals(character.animationSkeleton().joint(index).name())) {
                return index;
            }
        }
        throw new IllegalArgumentException("Player skeleton is missing root-motion bone " + name);
    }
}
