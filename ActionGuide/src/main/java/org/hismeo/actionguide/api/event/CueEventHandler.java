package org.hismeo.actionguide.api.event;

import org.hismeo.actionguide.api.action.ActionContext;
import org.hismeo.actionguide.api.cue.CueEvent;
import org.hismeo.actionguide.api.runtime.ActionInstanceView;

@FunctionalInterface
public interface CueEventHandler {
    void handle(ActionContext context, ActionInstanceView action, CueEvent event);
}
