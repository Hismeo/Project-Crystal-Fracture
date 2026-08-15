package org.hismeo.fractureclient.client.avatar;

import com.kaleblangley.haikalat.subsystems.animation.AnimationPlayer;

/** Named animation contract exported by the shared player animation library. */
public enum PlayerAvatarAnimation {
    STAND("stand", AnimationPlayer.LoopMode.LOOP),
    MOVE("move", AnimationPlayer.LoopMode.LOOP),
    RUN("run", AnimationPlayer.LoopMode.LOOP),
    JUMP("jump", AnimationPlayer.LoopMode.ONCE),
    DASH("dash", AnimationPlayer.LoopMode.ONCE);

    private final String clipName;
    private final AnimationPlayer.LoopMode loopMode;

    PlayerAvatarAnimation(String clipName, AnimationPlayer.LoopMode loopMode) {
        this.clipName = clipName;
        this.loopMode = loopMode;
    }

    public String clipName() {
        return clipName;
    }

    public AnimationPlayer.LoopMode loopMode() {
        return loopMode;
    }
}
