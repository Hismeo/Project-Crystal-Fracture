package org.hismeo.crystalfracture.weapon.api;

import org.hismeo.crystalfracture.weapon.definition.WeaponConnectEndpoint;

import java.util.Objects;

public record CompiledWeaponConnection(
        WeaponConnectEndpoint parent,
        String parentNode,
        WeaponConnectEndpoint child,
        String childNode
) {
    public CompiledWeaponConnection {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(parentNode, "parentNode");
        Objects.requireNonNull(child, "child");
        Objects.requireNonNull(childNode, "childNode");
        if (parentNode.isBlank() || childNode.isBlank()) {
            throw new IllegalArgumentException("compiled connect nodes must not be blank");
        }
    }
}
