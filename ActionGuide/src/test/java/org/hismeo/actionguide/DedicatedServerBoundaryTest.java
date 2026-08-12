package org.hismeo.actionguide;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DedicatedServerBoundaryTest {
    @Test
    void productionSourcesDoNotReferenceClientRenderingOrSiblingModules() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> forbidden = List.of(
                "net.minecraft.client.",
                "org.hismeo.fractureclient",
                "org.hismeo.haikalat",
                "org.lwjgl.",
                "com.mojang.blaze3d."
        );
        try (var files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
                for (String name : forbidden) {
                    assertFalse(source.contains(name), () -> file + " references forbidden dedicated-server type namespace " + name);
                }
            }
        }
    }

    @Test
    void publicApiDoesNotExposeInternalImplementation() throws IOException {
        Path apiRoot = Path.of("src/main/java/org/hismeo/actionguide/api");
        try (var files = Files.walk(apiRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(source.contains("org.hismeo.actionguide.internal"),
                        () -> file + " exposes an internal implementation type");
            }
        }
    }
}
