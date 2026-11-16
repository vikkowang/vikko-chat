package com.vikko.chat.orchestrator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vikko.chat.agent.Agent;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

/**
 * 工具检索器:按用户消息从「所有子 agent 工具」里挑出相关子集,替代构造期的一次性全量挂载。
 *
 * <p>检索算法是<b>中文 2-gram + 简化 BM25</b>——零外部依赖,对应 Anthropic 原生 tool search 的
 * BM25 变体。对每个工具,把 {@code name + description} 作为检索文本建索引;每次请求把用户消息
 * 分词打分,返回 topK 个相关 {@link ToolCallback},再由 planner 挂载。
 *
 * <p>兜底:query 为空或与所有工具都无词元交集时返回全部,避免漏召导致任务直接失败。
 */
@Component
public class AgentToolRetriever {

    /** 默认检索返回的工具数量。 */
    public static final int DEFAULT_TOP_K = 3;

    /** 工具名 → 回调,检索命中后用名字反查回调对象。 */
    private final Map<String, ToolCallback> byName = new HashMap<>();

    /** 工具名 → 检索文本(name + description),供 {@link #rank} 打分。 */
    private final Map<String, String> nameToText = new HashMap<>();

    public AgentToolRetriever(List<Agent> agents) {
        ToolCallback[] callbacks = ToolCallbacks.from(agents.toArray());
        for (ToolCallback callback : callbacks) {
            ToolDefinition def = callback.getToolDefinition();
            byName.put(def.name(), callback);
            nameToText.put(def.name(), def.name() + " " + def.description());
        }
    }

    /** 按用户消息检索出 topK 个相关工具回调。 */
    public ToolCallback[] retrieve(String query, int topK) {
        List<String> names = rank(query, nameToText, topK);
        ToolCallback[] result = new ToolCallback[names.size()];
        for (int i = 0; i < names.size(); i++) {
            result[i] = byName.get(names.get(i));
        }
        return result;
    }

    // ---------- 纯函数(可独立单测,不依赖 Spring) ----------

    /** 把文本切成小写字符 2-gram,过滤标点/空白。中文、英文、数字统一按相邻字符对处理。 */
    static List<String> tokenize(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        StringBuilder cleaned = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                cleaned.append(Character.toLowerCase(c));
            }
        }
        for (int i = 0; i + 1 < cleaned.length(); i++) {
            result.add(cleaned.substring(i, i + 2));
        }
        return result;
    }

    /**
     * 对 {@code nameToText} 里的每个工具按 query 做简化 BM25 打分,返回按相关度降序的 topK 个工具名。
     *
     * <p>score(doc, query) = Σ idf(term) × tf(term, doc),term 为 query 与 doc 共有的 2-gram。
     * 全零分或空 query 时兜底返回全部(保持 {@code nameToText} 的插入顺序)。
     */
    static List<String> rank(String query, Map<String, String> nameToText, int topK) {
        List<String> names = new ArrayList<>(nameToText.keySet());
        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return names;
        }

        Map<String, Map<String, Integer>> docTf = new HashMap<>();
        Map<String, Integer> docFreq = new HashMap<>();
        for (String name : names) {
            Map<String, Integer> tf = termFrequency(tokenize(nameToText.get(name)));
            docTf.put(name, tf);
            for (String term : tf.keySet()) {
                docFreq.merge(term, 1, Integer::sum);
            }
        }

        int n = names.size();
        Map<String, Double> scores = new HashMap<>();
        for (String name : names) {
            Map<String, Integer> tf = docTf.get(name);
            double score = 0;
            for (String term : queryTerms) {
                Integer freq = tf.get(term);
                if (freq != null) {
                    int df = docFreq.getOrDefault(term, 0);
                    double idf = Math.log((n + 1.0) / (df + 1.0)) + 1.0;
                    score += idf * freq;
                }
            }
            scores.put(name, score);
        }

        boolean allZero = scores.values().stream().allMatch(s -> s == 0.0);
        if (allZero) {
            return names;
        }
        return names.stream()
                .sorted((a, b) -> Double.compare(scores.get(b), scores.get(a)))
                .limit(topK)
                .toList();
    }

    private static Map<String, Integer> termFrequency(List<String> terms) {
        Map<String, Integer> tf = new HashMap<>();
        for (String term : terms) {
            tf.merge(term, 1, Integer::sum);
        }
        return tf;
    }
}
