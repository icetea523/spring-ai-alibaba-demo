package com.example.saa.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 【知识点 5 · 续】第二个工具：无参数的简单工具。
 * 故意设计成一个无参方法，用来观察模型"不需要参数时怎么传参"。
 */
@Component
public class DateTimeTools {

    @Tool(description = "获取当前的日期与时间。当用户问现在几点、今天日期，或需要基于当前时间计算时调用。")
    public String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
