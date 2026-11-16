package com.vikko.chat.orchestrator;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具检索器的核心(中文 2-gram + BM25)纯函数测试,不依赖 Spring。
 *
 * <p>检索对象是 planner 挂载的 5 个子 agent 工具(name + description),断言「语义 query → 命中正确 agent」。
 */
class AgentToolRetrieverTest {

    /** 模拟 {@code ToolCallbacks.from(agents)} 得到的 name → (name + description) 文本。 */
    private static final Map<String, String> AGENTS = Map.of(
            "calculate", "calculate 做数学计算(支持复合算式,如 '12 乘 8 再加 5')。返回结果和简要计算过程。",
            "queryWeatherOrTime", "queryWeatherOrTime 查询某个城市的天气,或某个时区的当前时间,并结合结果给出简短生活建议。",
            "manageUserStatus", "manageUserStatus 查询或修改用户账户状态(正常/停用/封禁)。修改前会先查询确认当前状态,修改后报告变更结果。",
            "operateDocuments", "operateDocuments 操作钉钉文档/知识库:搜索、读取、创建、编辑、整理、移动、删除、权限管理等。适合任何涉及钉钉文档的任务。",
            "research", "research 检索本地知识库回答专业问题,并附引用来源。适合需要查资料、需要准确出处的场景。");

    @Test
    void tokenizeChineseIntoBigrams() {
        assertEquals(List.of("北京", "京天", "天气"), AgentToolRetriever.tokenize("北京天气"));
    }

    @Test
    void tokenizeFiltersPunctuationAndCase() {
        assertEquals(List.of("be", "ei", "ij", "ji", "in", "ng"), AgentToolRetriever.tokenize("Bei-Jing!"));
    }

    @Test
    void rankFindsWeatherAgentForWeatherQuery() {
        assertEquals("queryWeatherOrTime",
                AgentToolRetriever.rank("北京的天气怎么样", AGENTS, 3).get(0));
    }

    @Test
    void rankFindsCalculatorForMathQuery() {
        assertEquals("calculate",
                AgentToolRetriever.rank("算一下 12 乘 8 再加 5", AGENTS, 3).get(0));
    }

    @Test
    void rankFindsUserAdminForStatusQuery() {
        assertEquals("manageUserStatus",
                AgentToolRetriever.rank("帮我查用户张三的状态", AGENTS, 3).get(0));
    }

    @Test
    void rankFindsDingTalkForDocumentQuery() {
        assertEquals("operateDocuments",
                AgentToolRetriever.rank("整理一下钉钉文档", AGENTS, 3).get(0));
    }

    @Test
    void rankFindsResearchForLookupQuery() {
        assertEquals("research",
                AgentToolRetriever.rank("查资料要准确出处", AGENTS, 3).get(0));
    }

    @Test
    void rankRespectsTopK() {
        List<String> result = AgentToolRetriever.rank("北京的天气怎么样", AGENTS, 2);
        assertEquals(2, result.size());
        assertEquals("queryWeatherOrTime", result.get(0));
    }

    @Test
    void rankReturnsAllWhenNoMatch() {
        List<String> result = AgentToolRetriever.rank("zzzz qqqq", AGENTS, 3);
        assertEquals(AGENTS.size(), result.size());
        assertTrue(result.containsAll(AGENTS.keySet()));
    }

    @Test
    void rankReturnsAllWhenQueryEmpty() {
        List<String> result = AgentToolRetriever.rank("", AGENTS, 3);
        assertEquals(AGENTS.size(), result.size());
    }
}
