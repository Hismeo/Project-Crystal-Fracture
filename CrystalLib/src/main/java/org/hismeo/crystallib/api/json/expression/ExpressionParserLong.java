package org.hismeo.crystallib.api.json.expression;

import java.util.*;

/**
 * 表达式整数运算
 * 支持变量命名：@var、$var.name、a_b、foo.bar
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

            if (isVariableStartChar(c)) {
                int start = i;
                while (i < n && isVariableChar(cs[i])) i++;
                String var = expr.substring(start, i);
                long val = ctx.getOrDefault(var, 0).longValue();
                nums.push(val);
                continue;
            }

            if (c == '(') ops.push(c);
            else if (c == ')') {
                while (!ops.isEmpty() && ops.peek() != '(') compute(nums, ops);
                if (ops.isEmpty()) return OptionalLong.empty();
                ops.pop();
            } else if (isOp(c)) {
                while (!ops.isEmpty() && ops.peek() != '(' && prec(ops.peek()) >= prec(c)) {
                    compute(nums, ops);
                }
                ops.push(c);
            } else {
                return OptionalLong.empty(); // 非法字符
            }

            i++;
        }

        while (!ops.isEmpty()) {
            if (ops.peek() == '(') return OptionalLong.empty();
            compute(nums, ops);
        }

        return nums.size() == 1 ? OptionalLong.of(nums.pop()) : OptionalLong.empty();
    }

    private static boolean isVariableStartChar(char c) {
        return Character.isLetter(c) || c == '_' || c == '@' || c == '$';
    }

    private static boolean isVariableChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '@' || c == '$';
    }

    private static boolean isOp(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    private static int prec(char op) {
        return (op == '+' || op == '-') ? 1 : 2;
    }

    private static void compute(Deque<Long> nums, Deque<Character> ops) {
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