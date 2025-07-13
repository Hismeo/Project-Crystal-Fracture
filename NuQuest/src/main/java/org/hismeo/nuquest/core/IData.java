package org.hismeo.nuquest.core;

import org.hismeo.crystallib.api.IEmpty;

public interface IData<T extends IEmpty> extends IEmpty<T> {
    T mergeData(T newData);
}
