package org.hismeo.crystallib.api.json.expression.evalnumber;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.hismeo.crystallib.api.json.expression.ExpressionParserDouble;
import org.hismeo.crystallib.api.json.expression.IEval;

import java.util.Map;

public class EvalDouble implements IEval<Double> {
    private final String expr;

    public EvalDouble(double constant) {
        this.expr = Double.toString(constant);
    }

    public EvalDouble(String expression) {
        this.expr = expression;
    }

    public static EvalDouble fromJson(JsonElement jsonElement) {
        return fromJson(jsonElement, new EvalDouble(0));
    }
    
    public static EvalDouble fromJson(JsonElement jsonElement, EvalDouble defaultValue) {
        if (jsonElement == null || jsonElement.isJsonNull()) return defaultValue;
        if (jsonElement.isJsonPrimitive() && ((JsonPrimitive) jsonElement).isNumber()) {
            return new EvalDouble(jsonElement.getAsDouble());
        }
        return new EvalDouble(jsonElement.getAsString());
    }

    @Override
    public Double eval(Map<String, Number> ctx) {
        return ExpressionParserDouble.evalDouble(expr, ctx).orElse(0.0);
    }
}