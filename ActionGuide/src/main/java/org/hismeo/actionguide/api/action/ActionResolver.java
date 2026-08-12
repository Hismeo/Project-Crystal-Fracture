package org.hismeo.actionguide.api.action;

import java.util.Optional;

@FunctionalInterface
public interface ActionResolver {
    Optional<ActionId> resolve(ActionContext context, ActionIntentRequest request);
}
