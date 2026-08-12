package org.hismeo.actionguide.api.event;

import org.hismeo.actionguide.api.action.ActionContext;
import org.hismeo.actionguide.api.cue.CueState;
import org.hismeo.actionguide.api.runtime.ActionInstanceView;

public interface CueStateHandler {
    void onEnter(ActionContext context, ActionInstanceView action, CueState state);

    void onExit(ActionContext context, ActionInstanceView action, CueState state);
}
