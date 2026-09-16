package com.example.saa.tools.pool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 【知识点 10 · 续】池内工具示例 3：汇率查询（分组 finance）。
 *
 * finance 组与 travel 组的业务场景不同——这是演示"运行时动态摘挂"的关键：
 *   把 finance 组开关关掉后，再问汇率问题，模型看不到汇率工具，
 *   只能回答"我没有这个能力"，你可以直观观察到"挂载了什么，模型才会什么"。
 */
@Component
public class ExchangeRateTools {

    @Tool(description = "查询两种货币之间的当前汇率并换算金额。当用户询问汇率、货币换算、外币价格时调用此工具。")
    public String getExchangeRate(
            @ToolParam(description = "基准货币，例如：CNY、USD") String from,
            @ToolParam(description = "目标货币，例如：USD、JPY") String to) {
        // 模拟汇率：demo 固定返回，避免引入第三方接口依赖
        return "当前汇率 " + from + " → " + to + "：1 " + from + " ≈ 0.1394 " + to + "（模拟数据，仅供演示）。";
    }
}
