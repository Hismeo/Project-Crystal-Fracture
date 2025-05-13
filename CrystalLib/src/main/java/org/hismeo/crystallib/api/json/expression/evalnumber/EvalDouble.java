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

    public static EvalDouble fromJson(JsonElement el) {
        if (el == null || el.isJsonNull()) return new EvalDouble(0.0);
        if (el.isJsonPrimitive() && ((JsonPrimitive) el).isNumber()) {
            return new EvalDouble(el.getAsDouble());
        }
        return new EvalDouble(el.getAsString());
    }

    @Override
    public Double eval(Map<String, Number> ctx) {
        return ExpressionParserDouble.evalDouble(expr, ctx).orElse(0.0);
    }
}