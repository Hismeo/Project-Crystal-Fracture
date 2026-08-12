package org.hismeo.actionguide.api.action;

import org.hismeo.actionguide.api.runtime.ActionInstanceId;

public interface ActionAdmission {
    ActionDecision evaluate(ActionContext context, ActionDefinition action);

    void commit(ActionContext context, ActionDefinition action, ActionInstanceId instanceId);

    static ActionAdmission allowAll() {
        return new ActionAdmission() {
            @Override
            public ActionDecision evaluate(ActionContext context, ActionDefinition action) {
                return ActionDecision.allow();
            }

            @Override
            public void commit(ActionContext context, ActionDefinition action, ActionInstanceId instanceId) {
            }
        };
    }
}
