package com.example.saa.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * 【知识点 7】RAG（Retrieval-Augmented Generation，检索增强生成）—— 建库部分
 * ============================================================================
 *
 * RAG 解决什么问题？
 *   模型不知道你的私有文档/最新资料，硬问它就会"一本正经地胡说"（幻觉）。
 *   RAG 的思路：先从知识库里"检索"出最相关的几段内容，把它们塞进 prompt
 *   让模型"开卷考试"，回答就既有依据又可控。
 *
 * 建库流水线（本类的职责，类比 ETL）：
 *   ① Extract 读取：TextReader 把文本文档读成 Document 列表（1 个文件 = 1 个 Document）
 *   ② Transform 切分：TokenTextSplitter 按 token 数把长文档切成小块（chunk）——
 *      切分很重要：块太大检索不精准、块太小丢上下文
 *   ③ Load 向量化入库：EmbeddingModel 把每个 chunk 变成高维向量，存进 VectorStore
 *      向量的意义：语义相近的文本，向量距离就近——这是"按意思搜"而非"按关键词搜"的基础
 *
 * VectorStore 选型：
 *   SimpleVectorStore：纯内存的朴素实现（精确余弦相似度），零依赖，适合学习；
 *   生产换成 PgVectorStore / MilvusVectorStore / RedisVectorStore 等，接口不变。
 *
 * 本类启动时自动把 classpath:docs/ 下两篇文档灌进向量库，
 * 之后 RagController 就能用 QuestionAnswerAdvisor 检索问答了。
 */
@Configuration
public class RagConfig {

    private static final Logger log = LoggerFactory.getLogger(RagConfig.class);

    /**
     * 向量存储 Bean。注入的 EmbeddingModel 由 DashScope starter 自动装配
     * （默认通义 text-embedding 系列，见 application.yml）。
     */
    @Bean
    public SimpleVectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * 应用启动时执行一次建库：读 → 切 → 嵌 → 存。
     * 用 try/catch 兜底：没有配 API Key 时启动不失败，只是 /ai/rag 接口不可用。
     */
    @Bean
    public CommandLineRunner loadKnowledgeBase(SimpleVectorStore vectorStore) {
        return args -> {
            try {
                // ① Extract：classpath 下两篇知识文档 → Document 列表
                List<Document> documents = new ArrayList<>();
                documents.addAll(new TextReader(new ClassPathResource("docs/spring-ai-basics.txt")).get());
                documents.addAll(new TextReader(new ClassPathResource("docs/demo-usage.txt")).get());

                // ② Transform：按 token 切块（默认配置：块 ~800 token，重叠 100 token）
                List<Document> chunks = new TokenTextSplitter().apply(documents);

                // ③ Load：每个 chunk 调 embedding 接口 → 向量入库（会真实调用 DashScope 计费接口）
                vectorStore.add(chunks);

                log.info("[RAG] 知识库初始化完成：{} 篇文档 → {} 个向量块", documents.size(), chunks.size());
            } catch (Exception e) {
                log.warn("[RAG] 知识库初始化失败（通常是未配置 DASHSCOPE_API_KEY），/ai/rag 接口将不可用，其余功能不受影响。原因：{}", e.getMessage());
            }
        };
    }
}
