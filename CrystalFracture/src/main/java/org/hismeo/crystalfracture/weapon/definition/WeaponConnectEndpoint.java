package org.hismeo.crystalfracture.weapon.definition;

import org.hismeo.crystalfracture.weapon.api.ConnectName;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;

import java.util.Objects;

public record WeaponConnectEndpoint(WeaponSlotId slot, ConnectName connect) {
    public WeaponConnectEndpoint {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(connect, "connect");
    }
}
