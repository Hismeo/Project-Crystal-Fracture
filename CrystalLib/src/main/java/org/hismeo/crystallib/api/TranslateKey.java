package org.hismeo.crystallib.api;

public record TranslateKey(String type, String modid, String value) {
    @Override
    public String toString() {
        return "%s.%s.%s".formatted(type, modid, value);
    }
}
