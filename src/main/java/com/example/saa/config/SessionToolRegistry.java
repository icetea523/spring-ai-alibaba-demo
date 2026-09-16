package com.example.saa.config;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ============================================================================
 * 【知识点 11 · 续】有状态工具的"每会话一实例"隔离
 * ============================================================================
 *
 * 问题是什么？
 *   Spring 的 @Component 工具是【单例】：全应用只有一个实例，所有用户共享。
 *   工具没有实例状态（无字段 / 只读字段）时，单例 + 线程安全即可，最简单；
 *   但工具一旦需要"记住东西"（本轮已查过的结果、会话内的临时草稿、操作历史），
 *   单例里的实例字段就会变成所有会话共享的全局变量——用户 A 的数据被用户 B 看见，
 *   这叫"串号"，是并发场景下比丢计数更严重的事故（数据越权）。
 *
 * 为什么不用 synchronized？
 *   锁只解决"同时改一个值"的竞争问题，解决不了"数据本不该共享"的问题——
 *   把串号的字段锁起来，只是让用户们排队串号。隔离和互斥是两件事。
 *
 * 为什么不用 ThreadLocal？
 *   ThreadLocal 的生命周期是"一个请求线程"。而会话是跨请求的：
 *   用户第一句话和第二句话是两个 HTTP 请求（甚至两个线程），
 *   ThreadLocal 里的状态在第一个请求结束时就丢了。会话级状态必须以
 *   conversationId 为键显式存放，而不是藏在线程里。
 *
 * 正确姿势（本类实现）：每会话一实例
 *   - SessionStatsTools 故意【不注册为 Spring Bean】——它就该是普通对象，
 *     由注册表按 conversationId 创建和管理，一个会话一个实例，状态天然隔离；
 *   - 注册表用 ConcurrentHashMap.computeIfAbsent：同一会话的并发请求只会创建一个实例
 *     （computeIfAbsent 的原子性保证），不同会话各拿各的；
 *   - 实例内部字段仍用并发容器（CopyOnWriteArrayList）：同一会话内如果发生并发请求，
 *     实例内部还是要线程安全——"每会话一实例"解决隔离，不豁免线程安全。
 *
 * 怎么测？（见 ConcurrencyToolController 的 /ai/tool-session）
 *   curl "http://localhost:8080/ai/tool-session?conversationId=u1&message=记一条笔记：明天上午开评审会"
 *   curl "http://localhost:8080/ai/tool-session?conversationId=u1&message=我的笔记都有哪些？"
 *   curl "http://localhost:8080/ai/tool-session?conversationId=u2&message=我的笔记都有哪些？"
 *   ← u2 问不到 u1 的笔记：这就是会话隔离；把 conversationId 换回去又都还在。
 */
@Component
public class SessionToolRegistry {

    /**
     * 会话Id → 该会话专属的工具实例。
     * ConcurrentHashMap 而非 HashMap：多个用户同时开始会话时并发写入，
     * computeIfAbsent 保证"同键只建一次实例"是原子的。
     */
    private final ConcurrentHashMap<String, SessionStatsTools> sessions = new ConcurrentHashMap<>();

    /**
     * 取当前会话的工具实例（没有就创建）。
     * 这是"每会话一实例"模式的全部核心：computeIfAbsent 一行。
     */
    public SessionStatsTools forSession(String conversationId) {
        return sessions.computeIfAbsent(conversationId, id -> new SessionStatsTools(id));
    }

    /** 当前存活的会话数（观测用：压测后看是否泄漏） */
    public int sessionCount() {
        return sessions.size();
    }

    /**
     * 会话专属工具：注意它不是 @Component！
     * 有实例状态（notes），实例由 SessionToolRegistry 按会话创建，
     * 在 Controller 里通过 .toolCallbacks(...) 挂载——"工具对象从哪来"是完全自由的。
     */
    public static class SessionStatsTools {

        private final String sessionId;
        /** 会话内笔记：并发容器兜底"同一会话内的并发请求" */
        private final List<String> notes = new CopyOnWriteArrayList<>();

        SessionStatsTools(String sessionId) {
            this.sessionId = sessionId;
        }

        @Tool(description = "把一条笔记记录到当前会话的记事本。当用户说'记一下/记条笔记'时调用。")
        public String addNote(@ToolParam(description = "笔记内容") String note) {
            notes.add("[" + sessionId + "] " + note);
            return "已记录（当前会话共 " + notes.size() + " 条）。";
        }

        @Tool(description = "列出当前会话记事本里的全部笔记。当用户问'我记了什么/我的笔记'时调用。")
        public String listNotes() {
            if (notes.isEmpty()) {
                return "当前会话还没有笔记。";
            }
            return "会话 " + sessionId + " 的笔记（共 " + notes.size() + " 条）：" + String.join("；", notes);
        }
    }
}
