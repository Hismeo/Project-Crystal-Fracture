package org.hismeo.crystalfracture.weapon.internal.registry;

import org.hismeo.crystalfracture.weapon.definition.WeaponConnectionDefinition;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Canonical hash of server-readable definitions; JSON field order never participates. */
final class WeaponRegistryContentHasher {
    private WeaponRegistryContentHasher() {
    }

    static String sha256(WeaponRegistrySnapshot snapshot) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        snapshot.partTypes().forEach((id, type) -> {
            add(digest, "type");
            add(digest, id.toString());
            type.requiredConnects().forEach(value -> add(digest, "connect:" + value));
            type.requiredMarkers().forEach(value -> add(digest, "marker:" + value));
        });
        snapshot.parts().forEach((id, part) -> {
            add(digest, "part");
            add(digest, id.toString());
            add(digest, part.type().toString());
            add(digest, part.visualModel().toString());
            part.connects().forEach((name, node) -> add(digest, "connect:" + name + "=" + node));
            part.markers().forEach((name, node) -> add(digest, "marker:" + name + "=" + node));
        });
        snapshot.schemas().forEach((id, schema) -> {
            add(digest, "schema");
            add(digest, id.toString());
            add(digest, Integer.toString(schema.schemaVersion()));
            add(digest, schema.root().value());
            schema.slots().forEach((slot, definition) ->
                    add(digest, "slot:" + slot + "=" + definition.partType()));
            schema.connections().stream()
                    .map(WeaponRegistryContentHasher::connectionKey)
                    .sorted()
                    .forEach(connection -> add(digest, "connection:" + connection));
            schema.markers().forEach((name, marker) ->
                    add(digest, "marker:" + name + "=" + marker.slot() + "/" + marker.marker()));
        });
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static String connectionKey(WeaponConnectionDefinition connection) {
        return connection.parent().slot() + "/" + connection.parent().connect()
                + "->" + connection.child().slot() + "/" + connection.child().connect();
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
