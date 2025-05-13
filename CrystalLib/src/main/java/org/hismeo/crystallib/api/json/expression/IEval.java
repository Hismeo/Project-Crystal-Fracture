package org.hismeo.crystallib.api.json.expression;

import java.util.Map;

public interface IEval<T extends Number> {
    /**
     * @param ctx 传入表达式可用的变量名及其参数
     * @return 表达式运算后的number值
     */
    T eval(Map<String, Number> ctx);
}
