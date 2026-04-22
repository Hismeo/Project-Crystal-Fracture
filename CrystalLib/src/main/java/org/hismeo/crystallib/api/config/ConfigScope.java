package org.hismeo.crystallib.api.config;

public enum ConfigScope {
    COMMON("common"),
    CLIENT("client"),
    SERVER("server");

    private final String suffix;

    ConfigScope(String suffix) {
        this.suffix = suffix;
    }

    public String suffix() {
        return suffix;
    }
}
