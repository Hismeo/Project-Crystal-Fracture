package org.hismeo.fractureclient.client.weapon;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Narrow compatibility fix for zero-size Blockbench locators exported with null scale values. */
final class BlockbenchGlbCompatibility {
    private static final byte[] INVALID_SCALE =
            "[null,null,null]".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] IDENTITY_SCALE =
            "[1.00,1.00,1.00]".getBytes(StandardCharsets.US_ASCII);
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int JSON_CHUNK = 0x4E4F534A;
    private static final byte[][] TEXTURE_KEYS = {
            "\"images\":".getBytes(StandardCharsets.US_ASCII),
            "\"textures\":".getBytes(StandardCharsets.US_ASCII),
            "\"samplers\":".getBytes(StandardCharsets.US_ASCII),
            "\"baseColorTexture\":".getBytes(StandardCharsets.US_ASCII),
            "\"metallicRoughnessTexture\":".getBytes(StandardCharsets.US_ASCII),
            "\"normalTexture\":".getBytes(StandardCharsets.US_ASCII),
            "\"occlusionTexture\":".getBytes(StandardCharsets.US_ASCII),
            "\"emissiveTexture\":".getBytes(StandardCharsets.US_ASCII)
    };

    private BlockbenchGlbCompatibility() {
    }

    static SanitizedGlb sanitize(byte[] source) {
        if (source.length < 20
                || littleEndianInt(source, 0) != GLB_MAGIC
                || littleEndianInt(source, 16) != JSON_CHUNK) {
            return new SanitizedGlb(source, 0);
        }
        int jsonLength = littleEndianInt(source, 12);
        int end = Math.min(source.length, 20 + Math.max(0, jsonLength));
        byte[] result = source;
        int replacements = 0;
        for (int index = 20; index <= end - INVALID_SCALE.length; index++) {
            if (!matches(source, index, INVALID_SCALE)) {
                continue;
            }
            if (result == source) {
                result = Arrays.copyOf(source, source.length);
            }
            System.arraycopy(IDENTITY_SCALE, 0, result, index, IDENTITY_SCALE.length);
            replacements++;
            index += INVALID_SCALE.length - 1;
        }
        return new SanitizedGlb(result, replacements);
    }

    /**
     * Leaves embedded image bytes in the BIN chunk but hides every texture JSON key from the glTF
     * loader. The weapon renderer binds the single shared atlas itself, so Part assets only need
     * their meshes and material state.
     */
    static byte[] stripEmbeddedTextures(byte[] source) {
        if (source.length < 20
                || littleEndianInt(source, 0) != GLB_MAGIC
                || littleEndianInt(source, 16) != JSON_CHUNK) {
            return source;
        }
        int jsonLength = littleEndianInt(source, 12);
        int end = Math.min(source.length, 20 + Math.max(0, jsonLength));
        byte[] result = source;
        for (byte[] key : TEXTURE_KEYS) {
            for (int index = 20; index <= end - key.length; index++) {
                if (!matches(result, index, key)) {
                    continue;
                }
                if (result == source) {
                    result = Arrays.copyOf(source, source.length);
                }
                // Renaming the first character keeps the GLB byte layout valid while making the
                // property unknown to the glTF loader.
                result[index + 1] = 'x';
                index += key.length - 1;
            }
        }
        return result;
    }

    private static boolean matches(byte[] source, int offset, byte[] expected) {
        for (int index = 0; index < expected.length; index++) {
            if (source[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static int littleEndianInt(byte[] source, int offset) {
        return (source[offset] & 0xFF)
                | ((source[offset + 1] & 0xFF) << 8)
                | ((source[offset + 2] & 0xFF) << 16)
                | ((source[offset + 3] & 0xFF) << 24);
    }

    record SanitizedGlb(byte[] bytes, int replacements) {
    }
}
