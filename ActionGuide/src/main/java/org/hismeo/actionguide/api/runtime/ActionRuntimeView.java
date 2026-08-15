package org.hismeo.actionguide.api.runtime;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.cue.CueTime;
import org.hismeo.actionguide.api.cue.RootMotionContract;
import org.hismeo.actionguide.api.cue.SectionId;

import java.util.Optional;

public interface ActionRuntimeView {
    Optional<ActionInstanceView> currentAction();

    /** Root-motion data retained by the current immutable action instance. */
    Optional<RootMotionContract> currentRootMotion();

    boolean isStateActive(ResourceLocation stateType);

    boolean isInSection(SectionId section);

    CueTime cursor();
}
