package org.hismeo.actionguide.api.runtime;

import org.hismeo.actionguide.api.action.ActionId;
import org.hismeo.actionguide.api.cue.SectionId;

import java.util.Objects;

public sealed interface ActionCommand permits ActionCommand.RequestStart, ActionCommand.RequestInterrupt,
        ActionCommand.RequestTransition, ActionCommand.RequestStop {
    record RequestStart(ActionId action) implements ActionCommand {
        public RequestStart {
            Objects.requireNonNull(action, "action");
        }
    }

    record RequestInterrupt(ActionStopReason reason) implements ActionCommand {
        public RequestInterrupt {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record RequestTransition(SectionId section) implements ActionCommand {
        public RequestTransition {
            Objects.requireNonNull(section, "section");
        }
    }

    record RequestStop(ActionStopReason reason) implements ActionCommand {
        public RequestStop {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
