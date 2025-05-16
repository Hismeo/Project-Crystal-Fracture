package org.hismeo.crystallib.api.json.expression.evalnumber;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.hismeo.crystallib.api.json.expression.ExpressionParserDouble;
import org.hismeo.crystallib.api.json.expression.IEval;

import java.util.Map;

public class EvalFloat implements IEval<Float> {
    private final String expr;

    public EvalFloat(float constant) {
        this.expr = Float.toString(constant);
    }

    public EvalFloat(String expression) {
        this.expr = expression;
    }

    public static EvalFloat fromJson(JsonElement jsonElement) {
        return fromJson(jsonElement, new EvalFloat(0));
    }
    
    public static EvalFloat fromJson(JsonElement jsonElement, EvalFloat defaultValue) {
        if (jsonElement == null || jsonElement.isJsonNull()) return defaultValue;
        if (jsonElement.isJsonPrimitive() && ((JsonPrimitive) jsonElement).isNumber()) {
            return new EvalFloat(jsonElement.getAsFloat());
        }
        return new EvalFloat(jsonElement.getAsString());
    }

    @Override
    public Float eval(Map<String, Number> ctx) {
        return (float) ExpressionParserDouble.evalDouble(expr, ctx).orElse(0.0);
    }
}