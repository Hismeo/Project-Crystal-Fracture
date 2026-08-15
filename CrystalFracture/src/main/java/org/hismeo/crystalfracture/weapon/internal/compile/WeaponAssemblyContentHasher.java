package org.hismeo.crystalfracture.weapon.internal.compile;

import org.hismeo.crystalfracture.weapon.api.CompiledWeaponConnection;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.ResolvedMarker;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

final class WeaponAssemblyContentHasher {
    private WeaponAssemblyContentHasher() {
    }

    static String hash(
            WeaponSchemaId schemaId,
            WeaponSlotId root,
            Map<WeaponSlotId, WeaponPartDefinition> parts,
            List<CompiledWeaponConnection> connections,
            Map<MarkerName, ResolvedMarker> markers
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            field(digest, "crystal_fracture:compiled_weapon_assembly/v1");
            field(digest, schemaId.toString());
            field(digest, root.value());

            parts.forEach((slot, part) -> {
                field(digest, "part");
                field(digest, slot.value());
                field(digest, part.id().toString());
                field(digest, part.type().toString());
                field(digest, part.visualModel().toString());
                part.connects().forEach((name, node) -> {
                    field(digest, "connect");
                    field(digest, name.value());
                    field(digest, node);
                });
                part.markers().forEach((name, node) -> {
                    field(digest, "part_marker");
                    field(digest, name.value());
                    field(digest, node);
                });
                field(digest, "end_part");
            });

            for (CompiledWeaponConnection connection : connections) {
                field(digest, "connection");
                field(digest, connection.parent().slot().value());
                field(digest, connection.parent().connect().value());
                field(digest, connection.parentNode());
                field(digest, connection.child().slot().value());
                field(digest, connection.child().connect().value());
                field(digest, connection.childNode());
            }
            markers.forEach((name, marker) -> {
                field(digest, "export_marker");
                field(digest, name.value());
                field(digest, marker.slot().value());
                field(digest, marker.part().toString());
                field(digest, marker.partMarker().value());
                field(digest, marker.nodeName());
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void field(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
