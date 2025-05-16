package org.hismeo.crystallib.api.json.expression.evalnumber;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.hismeo.crystallib.api.json.expression.ExpressionParserDouble;
import org.hismeo.crystallib.api.json.expression.ExpressionParserLong;
import org.hismeo.crystallib.api.json.expression.IEval;

import java.util.Map;

public class EvalInt implements IEval<Integer> {
    private final String expr;

    public EvalInt(int constant) {
        this.expr = Integer.toString(constant);
    }

    public EvalInt(String expression) {
        this.expr = expression;
    }

    public static EvalInt fromJson(JsonElement jsonElement) {
        return fromJson(jsonElement, new EvalInt(0));
    }

    public static EvalInt fromJson(JsonElement jsonElement, EvalInt defaultValue) {
        if (jsonElement == null || jsonElement.isJsonNull()) return defaultValue;
        if (jsonElement.isJsonPrimitive() && ((JsonPrimitive) jsonElement).isNumber()) {
            return new EvalInt(jsonElement.getAsInt());
        }
        return new EvalInt(jsonElement.getAsString());
    }

    @Override
    public Integer eval(Map<String, Number> ctx) {
        return (int) ExpressionParserLong.evalLong(expr, ctx).orElse(0);
    }
}