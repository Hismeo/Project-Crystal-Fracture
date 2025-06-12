package org.hismeo.nuquest.core.dialog.context.config.components.button;

import org.hismeo.crystallib.api.json.expression.evalnumber.EvalInt;

public abstract class AbstractButtonConfig {
    EvalInt x;
    EvalInt y;
    EvalInt width;
    EvalInt height;

    public AbstractButtonConfig(EvalInt x, EvalInt y, EvalInt width, EvalInt height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
}
