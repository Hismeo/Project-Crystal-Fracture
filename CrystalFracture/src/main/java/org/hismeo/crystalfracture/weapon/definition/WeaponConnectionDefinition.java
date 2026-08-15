package org.hismeo.crystalfracture.weapon.definition;

import java.util.Objects;

public record WeaponConnectionDefinition(
        WeaponConnectEndpoint parent,
        WeaponConnectEndpoint child
) {
    public WeaponConnectionDefinition {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(child, "child");
    }
}
