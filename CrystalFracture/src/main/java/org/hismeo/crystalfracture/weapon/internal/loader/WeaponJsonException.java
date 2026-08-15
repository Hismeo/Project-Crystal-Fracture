package org.hismeo.crystalfracture.weapon.internal.loader;

final class WeaponJsonException extends RuntimeException {
    private final String path;
    private final String errorCode;

    WeaponJsonException(String path, String errorCode, String message) {
        super(message);
        this.path = path;
        this.errorCode = errorCode;
    }

    String path() {
        return path;
    }

    String errorCode() {
        return errorCode;
    }
}
