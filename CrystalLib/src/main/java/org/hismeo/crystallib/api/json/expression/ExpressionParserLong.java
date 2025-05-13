package org.hismeo.crystallib.api.json.expression;

import java.util.*;

/**
 * 表达式整数运算
 */
public class ExpressionParserLong {
    public static OptionalLong evalLong(String expr, Map<String, Number> ctx) {
        if (expr == null || expr.isBlank()) return OptionalLong.empty();
        char[] cs = expr.toCharArray();
        Deque<Long> nums = new ArrayDeque<>();
        Deque<Character> ops = new ArrayDeque<>();
        int i = 0, n = cs.length;
        while (i < n) {
            char c = cs[i];
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (Character.isDigit(c)) {
                int start = i;
                while (i < n && Character.isDigit(cs[i])) i++;
                nums.push(Long.parseLong(expr.substring(start, i)));
                continue;
            }

            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(cs[i]) || cs[i] == '_')) i++;
                String var = expr.substring(start, i);
                nums.push(ctx.getOrDefault(var, 0).longValue());
                continue;
            }

            if (c == '(') ops.push(c);
            else if (c == ')') {
                while (!ops.isEmpty() && ops.peek() != '(') computeInt(nums, ops);
                if (ops.isEmpty()) return OptionalLong.empty();
                ops.pop();
            } else if (isOp(c)) {
                while (!ops.isEmpty() && ops.peek() != '(' && prec(ops.peek()) >= prec(c)) {
                    computeInt(nums, ops);
                }
                ops.push(c);
            } else {
                return OptionalLong.empty();
            }
            i++;
        }
        while (!ops.isEmpty()) {
            if (ops.peek() == '(') return OptionalLong.empty();
            computeInt(nums, ops);
        }
        return nums.size() == 1 ? OptionalLong.of(nums.pop()) : OptionalLong.empty();
    }

    private static boolean isOp(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    private static int prec(char op) {
        return (op == '+' || op == '-') ? 1 : 2;
    }

    private static void computeInt(Deque<Long> nums, Deque<Character> ops) {
        long b = nums.pop(), a = nums.pop();
        char op = ops.pop();
        nums.push(switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            case '/' -> b == 0 ? 0 : a / b;
            default -> 0L;
        });
    }
}