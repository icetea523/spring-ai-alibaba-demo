package com.example.saa.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ============================================================================
 * 【知识点 6】Memory（会话记忆）—— 第 1 部分：记忆存什么、存在哪
 * ============================================================================
 *
 * 先破除一个误解：模型本身是"无状态"的。
 *   你上一句说"我叫振海"，下一句问"我叫什么"，模型是不知道的——
 *   除非你把之前的对话历史放进这次请求的 messages 里。
 *   所谓"记忆"，本质就是框架替你管理并回放这段历史。
 *
 * Spring AI 记忆体系三层结构：
 *   1) ChatMemoryRepository —— 存储层：历史消息存在哪
 *        InMemoryChatMemoryRepository（本 demo）：存内存，重启丢失，开发调试用；
 *        生产可换 JdbcChatMemoryRepository（存数据库）等，只换这一层。
 *   2) MessageWindowChatMemory —— 策略层：存多少
 *        滑动窗口：只保留最近 N 条消息（本 demo 20 条），
 *        防止会话越聊越长、token 费用和上下文爆掉。
 *   3) MessageChatMemoryAdvisor —— 接入层：怎么用（见 MemoryAdvisorController）
 *        以 Advisor 形式挂在 ChatClient 调用链上，每次请求前自动把历史"回放"进 prompt。
 *
 * conversationId 的意义：不同用户/会话各自一份历史，靠这个 ID 隔离。
 */
@Configuration
public class MemoryConfig {

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }
}
