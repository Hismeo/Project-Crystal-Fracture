package org.hismeo.crystallib.api.json.expression.evalnumber;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.hismeo.crystallib.api.json.expression.ExpressionParserDouble;
import org.hismeo.crystallib.api.json.expression.ExpressionParserLong;
import org.hismeo.crystallib.api.json.expression.IEval;

import java.util.Map;

public class EvalLong implements IEval<Long> {
    private final String expr;

    public EvalLong(long constant) {
        this.expr = Long.toString(constant);
    }

    public EvalLong(String expression) {
        this.expr = expression;
    }

    public static EvalLong fromJson(JsonElement el) {
        if (el == null || el.isJsonNull()) return new EvalLong(0L);
        if (el.isJsonPrimitive() && ((JsonPrimitive) el).isNumber()) {
            return new EvalLong(el.getAsLong());
        }
        return new EvalLong(el.getAsString());
    }

    @Override
    public Long eval(Map<String, Number> ctx) {
        return ExpressionParserLong.evalLong(expr, ctx).orElse(0L);
    }
}