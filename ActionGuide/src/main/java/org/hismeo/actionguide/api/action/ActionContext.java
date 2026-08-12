package org.hismeo.actionguide.api.action;

import org.hismeo.actionguide.api.runtime.ActionRuntimeView;

import java.util.Objects;

public record ActionContext(ActionOwner owner, ActionRuntimeView runtime, long serverTick) {
    public ActionContext {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(runtime, "runtime");
    }
}
