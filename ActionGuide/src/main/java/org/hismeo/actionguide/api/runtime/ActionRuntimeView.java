package org.hismeo.actionguide.api.runtime;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.cue.CueTime;
import org.hismeo.actionguide.api.cue.SectionId;

import java.util.Optional;

public interface ActionRuntimeView {
    Optional<ActionInstanceView> currentAction();

    boolean isStateActive(ResourceLocation stateType);

    boolean isInSection(SectionId section);

    CueTime cursor();
}
