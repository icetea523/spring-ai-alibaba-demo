package com.example.saa.tools.pool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 【知识点 10 · 续】池内工具示例 2：酒店查询（分组 travel）。
 *
 * 与 FlightTools 同属 travel 分组——分组依据是"业务场景"，不是技术实现：
 *   一个"差旅助手"Agent 请求到来时，挂载 travel 整组即可覆盖机票+酒店两类诉求，
 *   不用逐个工具挑选；这就是分组比单工具粒度更实用的原因。
 */
@Component
public class HotelTools {

    @Tool(description = "查询指定城市的酒店，返回酒店名、档次与每晚价格。当用户询问住宿、酒店、住哪里时调用此工具。")
    public String searchHotels(@ToolParam(description = "城市名称，例如：上海") String city) {
        return city + " 酒店推荐：浦东香格里拉（五星，¥1280/晚）；全季人民广场店（舒适型，¥450/晚）；亚朵静安寺店（高档型，¥680/晚）。";
    }
}
