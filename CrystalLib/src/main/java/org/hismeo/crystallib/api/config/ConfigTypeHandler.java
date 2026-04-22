package org.hismeo.crystallib.api.config;

import java.lang.reflect.Type;

public interface ConfigTypeHandler<T> {
    Object encode(T value);

    T decode(Object raw, Type targetType);
}
