package org.ticketing_system.biz.aiservice.agent.master;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.agent.AgentLlmService;
import org.ticketing_system.biz.aiservice.agent.AgentPlan;
import org.ticketing_system.biz.aiservice.agent.AgentTask;
import org.ticketing_system.biz.aiservice.agent.AgentType;
import org.ticketing_system.biz.aiservice.agent.AgentTraceEmitter;
import org.ticketing_system.biz.aiservice.client.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 主代理（Master Agent），负责对用户请求进行规划并路由至相应的子 Agent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterAgent {

    private static final int DEFAULT_MAX_TASKS = 5;
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");
    private static final Pattern ORDER_DATE_PATTERN = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}");
    private static final Pattern ORDER_COUNT_PATTERN = Pattern.compile("\\d+(?=\\s*(条|张|个))");
    private static final Pattern TRAIN_NUMBER_PATTERN = Pattern.compile("[GDCZTK]\\d{1,5}", Pattern.CASE_INSENSITIVE);
    private static final String DEPARTURE_PARAM = "departure";
    private static final String ARRIVAL_PARAM = "arrival";
    private static final String DATE_PARAM = "date";

    private static final String MASTER_SYSTEM_PROMPT = """
            你是铁路票务 AI 的 Master Agent，只做路由规划，不回答用户内容。
            今天日期：%s。
            只输出 JSON，不要 Markdown，不要解释，不得输出 JSON 之外的任何字符。
            不得新增 schema 外字段；字段缺失时使用空字符串；布尔值必须是 true/false，不要字符串。
            tasks 必须是数组，parameters 必须是对象。

            type 只能是：
            - TICKET_INFO：查车次/余票/票价。参数只允许 departure, arrival, date, trainNumber。必须有 departure+arrival+date。
            - ORDER_QUERY：查当前登录用户本人的订单。参数只允许 date, count。date 是下单日期 yyyy-MM-dd，count 是返回条数。
            - GENERAL_CHAT：闲聊或非实时咨询。GENERAL_CHAT 也必须作为 task 输出，但不要回答用户内容。parameters 输出空对象。

            输出 schema：
            {"clarification":"","needAggregation":false,"tasks":[{"type":"TICKET_INFO","intent":"","parameters":{"departure":"","arrival":"","date":"yyyy-MM-dd","trainNumber":""}}]}

            规则：
            1. tasks 数量大于 1 时，needAggregation=true；否则 needAggregation=false。
            2. 多意图场景中，参数完整的任务保留在 tasks；缺参的 TICKET_INFO 任务不要放入 tasks。
            3. 只要存在 TICKET_INFO 缺少 departure/arrival/date，clarification 写一句追问缺失信息；如果没有任何完整任务，则 tasks=[]。
            4. ORDER_QUERY 不要追问用户 ID；用户没给 date/count 也路由 ORDER_QUERY，由子 Agent 默认查最近一次买票日期当天的所有票。
            5. 不要为 ORDER_QUERY 输出订单号、车次、出发站、到达站、乘车人等其他参数。
            6. 相对日期基于今天日期解析为 yyyy-MM-dd，例如“明天”“后天”“下周一”“周五”。
            7. 用户说“周五”时，默认指未来最近的周五；日期无法唯一确定时，clarification 追问。
            8. “五一”等节日日期如果可由未来最近日期唯一确定，则转换为 yyyy-MM-dd；否则 clarification 追问。
            9. 最多输出 5 个 task。
            """;

    private final AgentLlmService agentLlmService;
    private final AiProperties aiProperties;

    /**
     * 根据用户请求生成执行计划
     *
     * @param context     聊天请求上下文
     * @param baseRequest 原始 LLM 请求
     * @return 包含任务列表或澄清请求的代理计划
     */
    public Mono<AgentPlan> plan(AiChatRequestContext context, LlmRequest baseRequest, AgentTraceEmitter traceEmitter) {
        LlmRequest request = LlmRequest.builder()
                .systemPrompt(String.format(MASTER_SYSTEM_PROMPT, LocalDate.now()))
                .userMessage(baseRequest.getUserMessage())
                .messages(baseRequest.getMessages())
                .userId(baseRequest.getUserId())
                .sessionId(baseRequest.getSessionId())
                .maxTokens(getMasterMaxTokens())
                .build();
        return agentLlmService.complete(request, traceEmitter, "MASTER", null, "Master Agent", false)
                .map(response -> parsePlan(response.getContent(), context, baseRequest))
                .onErrorResume(ex -> {
                    log.warn("Master Agent 规划失败，使用回退路由: {}", ex.getMessage(), ex);
                    return Mono.just(buildFallbackPlan(context, baseRequest));
                });
    }

    /**
     * 解析 LLM 返回的 JSON 字符串为 AgentPlan 对象
     */
    private AgentPlan parsePlan(String content, AiChatRequestContext context, LlmRequest baseRequest) {
        try {
            JSONObject jsonObject = JSON.parseObject(extractJson(content));
            AgentPlan plan = new AgentPlan();
            plan.setClarification(jsonObject.getString("clarification"));
            JSONArray tasks = jsonObject.getJSONArray("tasks");
            List<AgentTask> parsedTasks = new ArrayList<>(parseTasks(tasks, context, baseRequest));
            boolean hasIncompleteTicketInfoTask = parsedTasks.removeIf(this::isIncompleteTicketInfoTask);
            if (hasIncompleteTicketInfoTask && !plan.hasClarification()) {
                plan.setClarification("请补充出发地、目的地和出行日期。");
            }
            plan.setTasks(parsedTasks);
            if (plan.getTasks().size() > getMaxTasks()) {
                plan.setTasks(plan.getTasks().subList(0, getMaxTasks()));
            }
            plan.setNeedAggregation(plan.getTasks().size() > 1);
            if (!plan.hasClarification() && plan.getTasks().isEmpty()) {
                return buildFallbackPlan(context, baseRequest);
            }
            return plan;
        } catch (Exception ex) {
            log.warn("Master Agent 计划解析失败，使用回退路由: content={}", content, ex);
            return buildFallbackPlan(context, baseRequest);
        }
    }

    /**
     * 判断票务查询任务是否缺少必填参数
     */
    private boolean isIncompleteTicketInfoTask(AgentTask task) {
        if (task.getType() != AgentType.TICKET_INFO) {
            return false;
        }
        return hasBlankParameter(task, DEPARTURE_PARAM) || hasBlankParameter(task, ARRIVAL_PARAM) || hasBlankParameter(task, DATE_PARAM);
    }

    /**
     * 判断任务参数是否为空
     */
    private boolean hasBlankParameter(AgentTask task, String key) {
        return task.getStringParam(key) == null;
    }

    /**
     * 解析 JSON 数组为 AgentTask 列表
     */
    private List<AgentTask> parseTasks(JSONArray tasks, AiChatRequestContext context, LlmRequest baseRequest) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<AgentTask> result = new ArrayList<>();
        for (Object item : tasks) {
            if (!(item instanceof JSONObject taskJson)) {
                continue;
            }
            AgentType type = parseType(taskJson.getString("type"));
            if (type == null) {
                continue;
            }
            JSONObject parameters = taskJson.getJSONObject("parameters");
            result.add(AgentTask.builder()
                    .type(type)
                    .intent(taskJson.getString("intent"))
                    .parameters(parameters == null ? Map.of() : parameters)
                    .originalMessage(baseRequest.getUserMessage())
                    .context(context)
                    .llmRequest(baseRequest)
                    .build());
        }
        return result;
    }

    /**
     * 当 LLM 响应异常或无法解析时，构建基于关键词匹配的回退执行计划
     */
    private AgentPlan buildFallbackPlan(AiChatRequestContext context, LlmRequest baseRequest) {
        String message = baseRequest.getUserMessage() == null ? "" : baseRequest.getUserMessage();
        AgentType type = resolveFallbackType(message);
        Map<String, Object> parameters = buildFallbackParameters(message, type);
        AgentTask task = AgentTask.builder()
                .type(type)
                .intent("fallback-route")
                .parameters(parameters)
                .originalMessage(baseRequest.getUserMessage())
                .context(context)
                .llmRequest(baseRequest)
                .build();
        return AgentPlan.builder()
                .tasks(List.of(task))
                .needAggregation(false)
                .build();
    }

    /**
     * 基于简单关键词解析回退 Agent 类型
     */
    private AgentType resolveFallbackType(String message) {
        if (message.contains("订单")) {
            return AgentType.ORDER_QUERY;
        }
        if (message.contains("车") || message.contains("票") || message.contains("余票") || message.contains("价格") || message.contains("票价") || message.contains("多少钱")) {
            return AgentType.TICKET_INFO;
        }
        return AgentType.GENERAL_CHAT;
    }

    /**
     * 基于正则匹配构建回退参数
     */
    private Map<String, Object> buildFallbackParameters(String message, AgentType type) {
        if (type == AgentType.ORDER_QUERY) {
            return Map.of("date", firstMatch(ORDER_DATE_PATTERN, message), "count", firstMatch(ORDER_COUNT_PATTERN, message));
        }
        if (type == AgentType.TICKET_INFO) {
            return Map.of("trainNumber", firstMatch(TRAIN_NUMBER_PATTERN, message), "date", "");
        }
        return Map.of();
    }

    /**
     * 正则匹配第一个命中项
     */
    private String firstMatch(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group() : "";
    }

    /**
     * 解析 Agent 类型字符串
     */
    private AgentType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AgentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 从 LLM 响应中提取第一个 JSON 对象块
     */
    private String extractJson(String content) {
        if (content == null || content.isBlank()) {
            return "{}";
        }
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(content);
        return matcher.find() ? matcher.group() : content;
    }

    /**
     * 获取最大允许任务数
     */
    private int getMaxTasks() {
        AiProperties.Agent agent = aiProperties.getAgent();
        return agent == null ? DEFAULT_MAX_TASKS : agent.getMaxTasks();
    }

    /**
     * 获取主代理 LLM 最大输出 Token
     */
    private int getMasterMaxTokens() {
        AiProperties.Agent agent = aiProperties.getAgent();
        return agent == null ? 512 : agent.getMasterMaxTokens();
    }
}
