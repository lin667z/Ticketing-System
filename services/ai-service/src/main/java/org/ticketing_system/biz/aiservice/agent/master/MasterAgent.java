package org.ticketing_system.biz.aiservice.agent.master;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.agent.core.AgentLlmService;
import org.ticketing_system.biz.aiservice.agent.model.AgentPlan;
import org.ticketing_system.biz.aiservice.agent.model.AgentTask;
import org.ticketing_system.biz.aiservice.agent.core.AgentType;
import org.ticketing_system.biz.aiservice.agent.core.AgentTraceEmitter;
import org.ticketing_system.biz.aiservice.llm.dto.LlmRequest;
import org.ticketing_system.biz.aiservice.common.context.AiChatRequestContext;
import org.ticketing_system.biz.aiservice.common.util.JsonExtractor;
import org.ticketing_system.biz.aiservice.config.AiProperties;
import org.ticketing_system.biz.aiservice.memory.working.WorkingMemoryState;
import org.ticketing_system.biz.aiservice.session.context.SessionSlotState;
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

    private static final Pattern ORDER_DATE_PATTERN = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}");
    private static final Pattern ORDER_COUNT_PATTERN = Pattern.compile("\\d+(?=\\s*(条|张|个))");
    private static final Pattern TRAIN_NUMBER_PATTERN = Pattern.compile("[GDCZTK]\\d{1,5}", Pattern.CASE_INSENSITIVE);
    private static final String DEPARTURE_PARAM = "departure";
    private static final String ARRIVAL_PARAM = "arrival";
    private static final String DATE_PARAM = "date";
    private static final String TRAIN_NUMBER_PARAM = "trainNumber";
    private static final String COUNT_PARAM = "count";
    private static final String PASSENGER_NAME_PARAM = "passengerName";

    private static final String MASTER_SYSTEM_PROMPT = """
            你是铁路票务 AI 的 Master Agent（铁宝），只做路由规划，不回答用户内容。
            今天日期：%s。
            只输出 JSON，不要 Markdown，不要解释，不得输出 JSON 之外的任何字符。
            不得新增 schema 外字段；字段缺失时使用空字符串；布尔值必须是 true/false，不要字符串。
            readyTasks 和 pendingTasks 必须是数组，parameters 必须是对象，missingFields 必须是数组。

            type 只能是：
            - TICKET_INFO：查车次/余票/票价。参数只允许 departure, arrival, date, trainNumber。
            - ORDER_QUERY：查当前登录用户本人的订单。参数包括 date, count，可选参数 trainNumber, departure, arrival, passengerName。date 是下单日期 yyyy-MM-dd，count 是返回条数。用户要求"所有"/"全部"/"所有的"时 count 填 0。
            - GENERAL_CHAT：闲聊或非实时咨询。GENERAL_CHAT 也必须作为 task 输出，但不要回答用户内容。parameters 输出空对象，missingFields 输出空数组。

            输出 schema：
            {"clarification":"","needAggregation":false,"readyTasks":[{"type":"TICKET_INFO","intent":"","parameters":{"departure":"","arrival":"","date":"yyyy-MM-dd","trainNumber":""},"missingFields":[]}],"pendingTasks":[{"type":"TICKET_INFO","intent":"","parameters":{"departure":"","arrival":"","date":"","trainNumber":""},"missingFields":["departure","arrival","date"]}]}

            规则：
            1. 参数完整、可立即执行的任务放入 readyTasks；缺少必要参数但意图明确的任务放入 pendingTasks。
            2. 多意图场景下，不能因为某个任务缺参而放弃其他已完整任务。
            3. clarification 只围绕 pendingTasks 缺的字段来写，必须说明缺什么；如果 pendingTasks 为空，则 clarification 为空字符串。
            4. 如果 readyTasks 和 pendingTasks 同时存在，这是合法 mixed plan。
            5. ORDER_QUERY 不要追问用户 ID；用户没给 date/count 也路由 ORDER_QUERY，由子 Agent 默认查最近一次买票日期当天的票。用户说"所有"/"全部"/"所有的"时 count 填 0。
            6. 相对日期基于今天日期解析为 yyyy-MM-dd，例如"明天""后天""下周一""周五"。
            7. 用户说星期几时，默认指未来最近的该星期几；日期无法唯一确定时，放入 pendingTasks，并在 clarification 中说明是查车票还是查订单的日期。
            8. "五一"等节日日期如果可由未来最近日期唯一确定，则转换为 yyyy-MM-dd；否则放入 pendingTasks，并在 clarification 中说明缺什么。
            9. needAggregation 在以下情况为 true：readyTasks 数量大于 1，或同时存在 readyTasks 和 pendingTasks；否则为 false。
            10. 多线路并行：每一条不同的"出发地→到达地"是独立的 TICKET_INFO 任务。用户一次询问多条线路（如"北京到杭州、广州到湖北的票"）时，必须为每条线路各输出一个 TICKET_INFO 任务，不要合并、不要遗漏、不要因为某条缺日期而丢弃其他线路。
            11. 上下文合并：若提供了"已知线路"或"待补全线路"上下文，请把用户本轮新信息（如只补了日期或只补了地点）合并到语义对应的那条线路；无法判断归属时，按线路分别处理。不要重复输出上下文里已完成的线路。
            12. 同一 plan 内不要输出出发地、到达地完全相同的重复 TICKET_INFO 任务。
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
        List<LlmRequest.Message> messages = new ArrayList<>(
                baseRequest.getMessages() == null ? List.of() : baseRequest.getMessages());
        String slotContext = buildSlotContextHint(context);
        if (slotContext != null) {
            messages.add(LlmRequest.Message.of("system", slotContext));
        }
        LlmRequest request = LlmRequest.builder()
                .systemPrompt(String.format(MASTER_SYSTEM_PROMPT, LocalDate.now()))
                .userMessage(baseRequest.getUserMessage())
                .messages(messages)
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
     * 将工作记忆中已知的票务线路 / 订单查询槽位整理为上下文提示，供 Master 合并本轮新信息。
     * 没有任何已知槽位时返回 null，不注入冗余上下文。
     */
    private String buildSlotContextHint(AiChatRequestContext context) {
        WorkingMemoryState wm = context == null ? null : context.getWorkingMemory();
        SessionSlotState slot = wm == null ? null : wm.getTicketSlot();
        if (slot == null) {
            return null;
        }
        List<SessionSlotState.TicketSlot> routes = slot.getTicketRoutes();
        boolean hasRoutes = routes != null && !routes.isEmpty();
        SessionSlotState.OrderQuerySlot order = slot.getOrderQuery();
        boolean hasOrder = order != null
                && (order.getDate() != null || order.getCount() != null
                || order.getTrainNumber() != null || order.getDeparture() != null
                || order.getArrival() != null || order.getPassengerName() != null);
        if (!hasRoutes && !hasOrder) {
            return null;
        }
        StringBuilder hint = new StringBuilder("会话已知上下文（请据此把本轮补充信息合并到对应线路，已完成线路不要重复输出）：\n");
        if (hasRoutes) {
            hint.append("已知票务线路：").append(JSON.toJSONString(routes)).append('\n');
        }
        if (hasOrder) {
            hint.append("已知订单查询条件：").append(JSON.toJSONString(order)).append('\n');
        }
        return hint.toString();
    }

    /**
     * 解析 LLM 返回的 JSON 字符串为 AgentPlan 对象
     */
    private AgentPlan parsePlan(String content, AiChatRequestContext context, LlmRequest baseRequest) {
        try {
            JSONObject jsonObject = JSON.parseObject(extractJson(content));
            AgentPlan plan = AgentPlan.builder()
                    .clarification(jsonObject.getString("clarification"))
                    .needAggregation(jsonObject.getBooleanValue("needAggregation"))
                    .readyTasks(parseTasks(jsonObject.getJSONArray("readyTasks"), context, baseRequest, false))
                    .pendingTasks(parseTasks(jsonObject.getJSONArray("pendingTasks"), context, baseRequest, true))
                    .build()
                    .normalize();
            if (!plan.hasReadyTasks() && !plan.hasPendingTasks()) {
                return buildFallbackPlan(context, baseRequest);
            }
            if (!plan.hasPendingTasks()) {
                return plan;
            }
            String clarification = plan.hasClarification()
                    ? plan.getClarification()
                    : buildClarificationForPendingTasks(plan.getPendingTasks());
            return AgentPlan.builder()
                    .readyTasks(plan.getReadyTasks())
                    .pendingTasks(plan.getPendingTasks())
                    .clarification(clarification)
                    .needAggregation(plan.isNeedAggregation())
                    .build()
                    .normalize();
        } catch (Exception ex) {
            log.warn("Master Agent 计划解析失败，使用回退路由: content={}", content, ex);
            return buildFallbackPlan(context, baseRequest);
        }
    }

    /**
     * 解析 JSON 数组为 AgentTask 列表
     */
    private List<AgentTask> parseTasks(JSONArray tasks, AiChatRequestContext context, LlmRequest baseRequest, boolean pending) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<AgentTask> result = new ArrayList<>();
        String taskGroup = buildTaskGroup(baseRequest);
        for (Object item : tasks) {
            if (!(item instanceof JSONObject taskJson)) {
                continue;
            }
            AgentType type = parseType(taskJson.getString("type"));
            if (type == null) {
                continue;
            }
            JSONObject parameters = taskJson.getJSONObject("parameters");
            List<String> missingFields = parseMissingFields(taskJson.getJSONArray("missingFields"), type, parameters);
            result.add(AgentTask.builder()
                    .type(type)
                    .intent(taskJson.getString("intent"))
                    .parameters(parameters == null ? Map.of() : parameters)
                    .missingFields(missingFields)
                    .dependsOnClarification(pending || !missingFields.isEmpty())
                    .resumeKey(buildResumeKey(baseRequest, type, missingFields))
                    .taskGroup(taskGroup)
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
                .missingFields(defaultMissingFields(type, parameters))
                .dependsOnClarification(type == AgentType.TICKET_INFO)
                .resumeKey(buildResumeKey(baseRequest, type, defaultMissingFields(type, parameters)))
                .taskGroup(buildTaskGroup(baseRequest))
                .originalMessage(baseRequest.getUserMessage())
                .context(context)
                .llmRequest(baseRequest)
                .build();
        if (task.hasMissingFields()) {
            return AgentPlan.clarificationOnly(buildClarificationForPendingTasks(List.of(task)), List.of(task));
        }
        return AgentPlan.readyOnly(List.of(task));
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
            return Map.of(
                    DATE_PARAM, firstMatch(ORDER_DATE_PATTERN, message),
                    COUNT_PARAM, firstMatch(ORDER_COUNT_PATTERN, message),
                    TRAIN_NUMBER_PARAM, firstMatch(TRAIN_NUMBER_PATTERN, message));
        }
        if (type == AgentType.TICKET_INFO) {
            return Map.of(
                    TRAIN_NUMBER_PARAM, firstMatch(TRAIN_NUMBER_PATTERN, message),
                    DATE_PARAM, "",
                    DEPARTURE_PARAM, "",
                    ARRIVAL_PARAM, "");
        }
        return Map.of();
    }

    private List<String> parseMissingFields(JSONArray missingFields, AgentType type, JSONObject parameters) {
        List<String> parsed = new ArrayList<>();
        if (missingFields != null) {
            for (Object field : missingFields) {
                if (field != null) {
                    String value = String.valueOf(field).trim();
                    if (!value.isEmpty() && !parsed.contains(value)) {
                        parsed.add(value);
                    }
                }
            }
        }
        for (String fallback : defaultMissingFields(type, parameters == null ? Map.of() : parameters)) {
            if (!parsed.contains(fallback)) {
                parsed.add(fallback);
            }
        }
        return parsed;
    }

    private List<String> defaultMissingFields(AgentType type, Map<String, Object> parameters) {
        if (type != AgentType.TICKET_INFO) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(parameters.get(DEPARTURE_PARAM))) {
            missing.add(DEPARTURE_PARAM);
        }
        if (isBlank(parameters.get(ARRIVAL_PARAM))) {
            missing.add(ARRIVAL_PARAM);
        }
        if (isBlank(parameters.get(DATE_PARAM))) {
            missing.add(DATE_PARAM);
        }
        return missing;
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }

    private String buildClarificationForPendingTasks(List<AgentTask> pendingTasks) {
        if (pendingTasks == null || pendingTasks.isEmpty()) {
            return null;
        }
        List<String> prompts = new ArrayList<>();
        for (AgentTask task : pendingTasks) {
            if (task == null || !task.hasMissingFields()) {
                continue;
            }
            if (task.getType() == AgentType.TICKET_INFO) {
                prompts.add("查车票还需要补充" + joinTicketMissingFields(task.getMissingFields()));
            } else if (task.getType() == AgentType.ORDER_QUERY) {
                prompts.add("查订单还需要补充" + String.join("、", task.getMissingFields()));
            }
        }
        return prompts.isEmpty() ? "请补充任务所需信息。" : String.join("；", prompts) + "。";
    }

    private String joinTicketMissingFields(List<String> missingFields) {
        List<String> labels = new ArrayList<>();
        for (String field : missingFields) {
            labels.add(switch (field) {
                case DEPARTURE_PARAM -> "出发地";
                case ARRIVAL_PARAM -> "目的地";
                case DATE_PARAM -> "出行日期";
                default -> field;
            });
        }
        return String.join("、", labels);
    }

    private String buildResumeKey(LlmRequest baseRequest, AgentType type, List<String> missingFields) {
        return baseRequest.getSessionId() + ":" + type.name() + ":" + String.join("-", missingFields == null ? List.of() : missingFields);
    }

    private String buildTaskGroup(LlmRequest baseRequest) {
        return "session-" + baseRequest.getSessionId();
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
     * 从 LLM 响应中提取第一个配平的 JSON 对象块
     */
    private String extractJson(String content) {
        return JsonExtractor.firstJsonObject(content);
    }

    /**
     * 获取主代理 LLM 最大输出 Token
     */
    private int getMasterMaxTokens() {
        AiProperties.Agent agent = aiProperties.getAgent();
        return agent == null ? 512 : agent.getMasterMaxTokens();
    }
}
