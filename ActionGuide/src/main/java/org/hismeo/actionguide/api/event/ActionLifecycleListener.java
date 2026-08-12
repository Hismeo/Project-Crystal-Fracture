package org.hismeo.actionguide.api.event;

import org.hismeo.actionguide.api.action.ActionContext;
import org.hismeo.actionguide.api.runtime.ActionInstanceView;
import org.hismeo.actionguide.api.runtime.ActionStopReason;

public interface ActionLifecycleListener {
    default void onStarted(ActionContext context, ActionInstanceView action) {
    }

    default void onTransitioned(ActionContext context, ActionInstanceView action) {
    }

    default void onStopped(ActionContext context, ActionInstanceView action, ActionStopReason reason) {
    }
}
