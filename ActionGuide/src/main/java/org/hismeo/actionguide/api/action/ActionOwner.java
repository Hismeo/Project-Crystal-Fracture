package org.hismeo.actionguide.api.action;

import java.util.Objects;
import java.util.UUID;

public record ActionOwner(UUID id) {
    public ActionOwner {
        Objects.requireNonNull(id, "owner id");
    }
}
