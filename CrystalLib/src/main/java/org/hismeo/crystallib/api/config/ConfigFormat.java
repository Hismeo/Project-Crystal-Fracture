package org.hismeo.crystallib.api.config;

public enum ConfigFormat {
    TOML("toml"),
    JSON("json"),
    PROPERTIES("properties");

    private final String extension;

    ConfigFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }
}
