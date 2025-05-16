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

    public static EvalLong fromJson(JsonElement jsonElement) {
        return fromJson(jsonElement, new EvalLong(0));
    }

    public static EvalLong fromJson(JsonElement jsonElement, EvalLong defaultValue) {
        if (jsonElement == null || jsonElement.isJsonNull()) return defaultValue;
        if (jsonElement.isJsonPrimitive() && ((JsonPrimitive) jsonElement).isNumber()) {
            return new EvalLong(jsonElement.getAsLong());
        }
        return new EvalLong(jsonElement.getAsString());
    }

    @Override
    public Long eval(Map<String, Number> ctx) {
        return ExpressionParserLong.evalLong(expr, ctx).orElse(0L);
    }
}