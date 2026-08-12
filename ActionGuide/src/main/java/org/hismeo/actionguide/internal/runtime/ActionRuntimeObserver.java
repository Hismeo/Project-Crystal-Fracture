package org.hismeo.actionguide.internal.runtime;

import org.hismeo.actionguide.api.action.ActionContext;
import org.hismeo.actionguide.api.cue.CueEvent;
import org.hismeo.actionguide.api.runtime.ActionInstanceView;

@FunctionalInterface
public interface ActionRuntimeObserver {
    void onEvent(ActionContext context, ActionInstanceView action, CueEvent event, long loopIteration);
}
