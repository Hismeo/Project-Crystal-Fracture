package org.hismeo.crystallib.api.json.expression;

import java.util.*;

/**
 * 表达式浮点数运算
 */
public class ExpressionParserDouble {
    public static OptionalDouble evalDouble(String expr, Map<String, Number> ctx) {
        if (expr == null || expr.isBlank()) return OptionalDouble.empty();
        char[] cs = expr.toCharArray();
        Deque<Double> nums = new ArrayDeque<>();
        Deque<Character> ops = new ArrayDeque<>();
        int i = 0, n = cs.length;
        while (i < n) {
            char c = cs[i];
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (Character.isDigit(c) || c == '.') {
                int start = i;
                while (i < n && (Character.isDigit(cs[i]) || cs[i] == '.')) i++;
                nums.push(Double.parseDouble(expr.substring(start, i)));
                continue;
            }

            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(cs[i]) || cs[i] == '_')) i++;
                String var = expr.substring(start, i);
                nums.push(ctx.getOrDefault(var, 0).doubleValue());
                continue;
            }

            if (c == '(') ops.push(c);
            else if (c == ')') {
                while (!ops.isEmpty() && ops.peek() != '(') computeDouble(nums, ops);
                if (ops.isEmpty()) return OptionalDouble.empty();
                ops.pop();
            } else if (isOp(c)) {
                while (!ops.isEmpty() && ops.peek() != '(' && prec(ops.peek()) >= prec(c)) {
                    computeDouble(nums, ops);
                }
                ops.push(c);
            } else {
                return OptionalDouble.empty();
            }
            i++;
        }
        while (!ops.isEmpty()) {
            if (ops.peek() == '(') return OptionalDouble.empty();
            computeDouble(nums, ops);
        }
        return nums.size() == 1 ? OptionalDouble.of(nums.pop()) : OptionalDouble.empty();
    }

    private static boolean isOp(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    private static int prec(char op) {
        return (op == '+' || op == '-') ? 1 : 2;
    }

    private static void computeDouble(Deque<Double> nums, Deque<Character> ops) {
        double b = nums.pop(), a = nums.pop();
        char op = ops.pop();
        nums.push(switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            case '/' -> b == 0 ? Double.NaN : a / b;
            default -> 0.0;
        });
    }
}