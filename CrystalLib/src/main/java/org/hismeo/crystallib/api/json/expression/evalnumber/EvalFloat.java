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

    public static EvalFloat fromJson(JsonElement el) {
        if (el == null || el.isJsonNull()) return new EvalFloat(0f);
        if (el.isJsonPrimitive() && ((JsonPrimitive) el).isNumber()) {
            return new EvalFloat(el.getAsFloat());
        }
        return new EvalFloat(el.getAsString());
    }

    @Override
    public Float eval(Map<String, Number> ctx) {
        return (float) ExpressionParserDouble.evalDouble(expr, ctx).orElse(0.0);
    }
}