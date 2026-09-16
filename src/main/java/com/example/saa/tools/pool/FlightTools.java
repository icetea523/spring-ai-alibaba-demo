package com.example.saa.tools.pool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * 【知识点 10】动态工具池（Tool Pool）—— 池内工具示例 1：机票查询（分组 travel）
 * ============================================================================
 *
 * 为什么要有"工具池"这个概念？
 *   知识点 5 里我们把工具直接写死在代码里（.tools(weatherTools, dateTimeTools)），
 *   demo 阶段没问题；但真实 Agent 项目的工具会越积越多——机票、酒店、火车票、
 *   签证、汇率、天气、日程……工具列表有两个隐性成本：
 *
 *   1) Token 成本：每次请求都会把"全部工具的说明书"（名称+描述+参数 schema）发给模型，
 *      50 个工具轻松吃掉上万 token，且每次请求都要付一遍；
 *   2) 选择准确率：官方博客（Spring AI Tool Search 工具，2025-12）指出，
 *      当模型面对 30+ 个相似工具时，选对工具的准确率会明显下降。
 *
 *   所以工程上要做"工具治理"：统一注册成池子 → 打分组标签 → 按需挂载子集。
 *   本包里的三个类（FlightTools / HotelTools / ExchangeRateTools）就是池子里的"货"，
 *   真正的池化管理逻辑在 config/ToolPoolConfig，按需挂载的入口在 controller/ToolPoolController。
 *
 * 本类定位：一个普通的 @Tool 业务工具，和知识点 5 的写法完全一致——
 *   工具本身不需要为"进池子"做任何改造，池化是"管理方式"的升级，不是工具写法的升级。
 *   注意：本包工具没有在 McpServerConfig 里注册，所以不会暴露成 MCP 工具——
 *   是否对外暴露、暴露给谁，本身就是工具治理的一部分。
 */
@Component
public class FlightTools {

    /**
     * 模拟的机票查询工具。真实场景里这里是 GDS / OTA 接口调用。
     */
    @Tool(description = "查询两个城市之间的航班列表，返回航班号、起飞时间与票价。当用户询问机票、航班、怎么飞过去时调用此工具。")
    public String searchFlights(
            @ToolParam(description = "出发城市，例如：广州") String from,
            @ToolParam(description = "目的城市，例如：上海") String to) {
        // 模拟数据：教学 demo 不接真实航司接口，重点是演示"池子按分组挂载工具"的编排
        return from + " → " + to + " 共 3 个航班：CZ3501 08:00 ¥980；HU7002 11:30 ¥1050；MU5308 15:40 ¥890。";
    }
}
