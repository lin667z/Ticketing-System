package org.ticketing_system.biz.aiservice.agent.support;

import org.springframework.stereotype.Component;
import org.ticketing_system.biz.aiservice.config.AiProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 格式化下游票务/订单数据，用于子 Agent 的结果总结及前端组件渲染
 */
@Component
public class AiBusinessResultFormatter {

    private static final int DEFAULT_RESULT_MAX_CHARS = 500;
    private static final int DEFAULT_MAX_DISPLAY_COUNT = 5;
    private static final int ORDER_SN_MASK_VISIBLE = 6;
    private static final int PRICE_SCALE = 2;
    private static final String UNKNOWN = "未知";

    private final AiProperties aiProperties;

    public AiBusinessResultFormatter(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    /**
     * 格式化列车时刻/余票查询结果为文本摘要
     *
     * @param data        原始查询数据
     * @param trainNumber 可选的车次过滤
     * @return 格式化后的车次总结字符串
     */
    public String formatTrainSchedule(Map<String, Object> data, String trainNumber) {
        List<?> trainList = filterTrainList(getTrainList(data), trainNumber);
        if (trainList.isEmpty()) {
            return "未查询到符合条件的车次信息，请确认出发站、到达站和日期是否正确";
        }
        StringBuilder builder = new StringBuilder(getResultMaxChars());
        builder.append("查询到以下车次信息：\n");
        int maxDisplayCount = getMaxDisplayCount();
        int displayCount = Math.min(trainList.size(), maxDisplayCount);
        for (int index = 0; index < displayCount; index++) {
            Object item = trainList.get(index);
            if (item instanceof Map<?, ?> train) {
                appendTrainSummary(builder, train, index + 1);
            }
        }
        if (trainList.size() > maxDisplayCount) {
            builder.append("...还有 ").append(trainList.size() - maxDisplayCount).append(" 条结果");
        }
        return truncate(builder.toString());
    }

    /**
     * 格式化单个订单详情为文本摘要
     *
     * @param data 订单数据
     * @return 格式化后的订单摘要
     */
    public String formatOrder(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "未查询到该订单信息，请确认订单号是否正确";
        }
        StringBuilder builder = new StringBuilder(getResultMaxChars());
        builder.append("订单号：").append(maskOrderSn(safeGet(data, "orderSn", UNKNOWN)));
        builder.append("\n车次：").append(safeGet(data, "trainNumber", UNKNOWN));
        builder.append("\n行程：").append(safeGet(data, "departure", UNKNOWN));
        builder.append(" -> ").append(safeGet(data, "arrival", UNKNOWN));
        builder.append("\n乘车日期：").append(safeGet(data, "ridingDate", UNKNOWN));
        builder.append("\n出发时间：").append(safeGet(data, "departureTime", UNKNOWN));
        builder.append(" 到达时间：").append(safeGet(data, "arrivalTime", UNKNOWN));
        String status = safeGet(data, "status", "");
        if (!status.isBlank()) {
            builder.append("\n订单状态：").append(status);
        }
        return truncate(builder.toString());
    }

    /**
     * 格式化个人订单列表为文本摘要
     *
     * @param pageData 分页订单数据
     * @param date     可选的下单日期
     * @param count    可选的返回条数
     * @return 格式化后的个人订单总结
     */
    public String formatSelfOrders(Map<String, Object> pageData, String date, Long count) {
        List<?> records = getRecords(pageData);
        String backendMessage = pageData == null ? null : (String) pageData.get("message");
        String actualDate = resolveOrderQueryDate(records, date);

        if (records.isEmpty()) {
            if (backendMessage != null) {
                return backendMessage;
            }
            if (!UNKNOWN.equals(actualDate)) {
                return "未查询到你在 " + actualDate + " 买的车票。你可以提供其他日期或条数再查一次";
            }
            return "未查询到你的近期车票订单。你可以提供具体日期或条数再查一次";
        }

        StringBuilder builder = new StringBuilder(getResultMaxChars());
        if (!UNKNOWN.equals(actualDate)) {
            builder.append("查询到你在 ").append(actualDate).append(" 买的车票");
        } else {
            builder.append("查询到你的车票订单");
        }
        if (count != null && count > 0) {
            builder.append("，展示 ").append(count).append(" 条");
        } else if (count != null && count == 0) {
            builder.append("，展示全部 ").append(records.size()).append(" 条");
        }
        builder.append("：\n");

        int userRequestedCount;
        if (count != null && count > 0) {
            userRequestedCount = Math.toIntExact(count);
        } else if (count != null && count == 0) {
            userRequestedCount = records.size();
        } else {
            userRequestedCount = getOrderDisplayCount();
        }
        int displayCount = Math.min(records.size(), userRequestedCount);

        for (int index = 0; index < displayCount; index++) {
            Object item = records.get(index);
            if (item instanceof Map<?, ?> order) {
                builder.append(index + 1).append(". ");
                appendSelfOrderSummary(builder, order);
            }
        }
        if (records.size() > displayCount) {
            builder.append("...还有 ").append(records.size() - displayCount).append(" 条订单未展示");
        }
        if (backendMessage != null) {
            builder.append('\n').append(backendMessage);
        }
        builder.append("\n如需进一步查询，可以告诉我具体日期或要查看的条数");
        return truncate(builder.toString());
    }

    /**
     * 构建用于前端渲染的车次卡片组件数据
     */
    public Map<String, Object> buildTrainComponent(Map<String, Object> data, String trainNumber, String date) {
        Map<?, ?> train = findTrain(data, trainNumber);
        if (train.isEmpty()) {
            List<?> trainList = getTrainList(data);
            if (!trainList.isEmpty() && trainList.get(0) instanceof Map<?, ?> firstTrain) {
                train = firstTrain;
            }
        }
        if (train.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("trainNumber", safeGet(train, "trainNumber", fallback(trainNumber)));
        component.put("date", fallback(date));
        component.put("departure", safeGet(train, "departure", safeGet(train, "fromStation", UNKNOWN)));
        component.put("arrival", safeGet(train, "arrival", safeGet(train, "toStation", UNKNOWN)));
        component.put("departureTime", safeGet(train, "departureTime", UNKNOWN));
        component.put("arrivalTime", safeGet(train, "arrivalTime", UNKNOWN));
        component.put("duration", safeGet(train, "duration", UNKNOWN));
        component.put("seats", buildSeatComponents(train));
        return component;
    }

    /**
     * 构建用于前端渲染的订单详情组件数据
     */
    public Map<String, Object> buildOrderComponent(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("orderSn", safeGet(data, "orderSn", ""));
        component.put("trainNumber", safeGet(data, "trainNumber", UNKNOWN));
        component.put("status", safeGet(data, "status", UNKNOWN));
        component.put("departure", safeGet(data, "departure", UNKNOWN));
        component.put("arrival", safeGet(data, "arrival", UNKNOWN));
        component.put("departureTime", safeGet(data, "departureTime", UNKNOWN));
        component.put("arrivalTime", safeGet(data, "arrivalTime", UNKNOWN));
        component.put("ridingDate", safeGet(data, "ridingDate", UNKNOWN));
        return component;
    }

    /**
     * 构建用于前端渲染的个人订单卡片组件数据
     */
    public Map<String, Object> buildSelfOrderComponent(Map<String, Object> pageData) {
        List<?> records = getRecords(pageData);
        if (records.isEmpty() || !(records.get(0) instanceof Map<?, ?> order)) {
            return Map.of();
        }
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("orderSn", safeGet(order, "orderSn", ""));
        component.put("trainNumber", safeGet(order, "trainNumber", UNKNOWN));
        component.put("status", safeGet(order, "status", "已查询"));
        component.put("departure", safeGet(order, "departure", UNKNOWN));
        component.put("arrival", safeGet(order, "arrival", UNKNOWN));
        component.put("departureTime", safeGet(order, "departureTime", UNKNOWN));
        component.put("arrivalTime", safeGet(order, "arrivalTime", UNKNOWN));
        component.put("ridingDate", safeGet(order, "ridingDate", UNKNOWN));
        component.put("passengers", buildPassengerNames(order));
        return component;
    }

    /**
     * 拼接单条车次总结
     */
    private void appendTrainSummary(StringBuilder builder, Map<?, ?> train, int number) {
        builder.append(number).append(". ");
        builder.append(safeGet(train, "trainNumber", UNKNOWN));
        builder.append(" 出发：").append(safeGet(train, "departureTime", UNKNOWN));
        builder.append(" 到达：").append(safeGet(train, "arrivalTime", UNKNOWN));
        builder.append(" 历时：").append(safeGet(train, "duration", UNKNOWN));
        Object seatListObj = train.get("seatClassList");
        if (seatListObj instanceof List<?> seatList && !seatList.isEmpty()) {
            builder.append(" 余票：");
            for (int index = 0; index < seatList.size(); index++) {
                Object seatObj = seatList.get(index);
                if (seatObj instanceof Map<?, ?> seat) {
                    if (index > 0) {
                        builder.append("，");
                    }
                    builder.append(seatTypeName(safeGet(seat, "type", "")));
                    builder.append(" ").append(safeGet(seat, "quantity", "0")).append(" 张");
                    String price = safeGet(seat, "price", "");
                    if (!price.isBlank()) {
                        builder.append(" ").append(formatPrice(price)).append(" 元");
                    }
                }
            }
        }
        builder.append('\n');
    }

    /**
     * 构建席别信息列表
     */
    private List<Map<String, Object>> buildSeatComponents(Map<?, ?> train) {
        Object seatListObj = train.get("seatClassList");
        if (!(seatListObj instanceof List<?> seatList) || seatList.isEmpty()) {
            return List.of();
        }
        return seatList.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::buildSeatComponent)
                .toList();
    }

    /**
     * 拼接单条个人订单总结
     */
    private void appendSelfOrderSummary(StringBuilder builder, Map<?, ?> order) {
        String orderSn = safeGet(order, "orderSn", "");
        if (!orderSn.isBlank()) {
            builder.append("订单号 ").append(maskOrderSn(orderSn)).append("，");
        }
        builder.append(safeGet(order, "trainNumber", UNKNOWN));
        builder.append(" ");
        builder.append(safeGet(order, "departure", UNKNOWN)).append(" -> ").append(safeGet(order, "arrival", UNKNOWN));
        builder.append("，乘车日期 ").append(safeGet(order, "ridingDate", UNKNOWN));
        builder.append("，出发 ").append(safeGet(order, "departureTime", UNKNOWN));
        String seatType = safeGet(order, "seatType", "");
        if (!seatType.isBlank()) {
            builder.append("，席别 ").append(seatTypeName(seatType));
        }
        String amount = safeGet(order, "amount", "");
        if (!amount.isBlank()) {
            builder.append("，金额 ").append(formatPrice(amount)).append(" 元");
        }
        String realName = safeGet(order, "realName", "");
        if (!realName.isBlank()) {
            builder.append("，乘车人 ").append(realName);
        }
        builder.append('\n');
    }

    /**
     * 获取记录列表
     */
    private List<?> getRecords(Map<String, Object> pageData) {
        if (pageData == null || pageData.isEmpty()) {
            return List.of();
        }
        Object recordsObj = pageData.get("records");
        if (recordsObj instanceof List<?> records) {
            return records;
        }
        return List.of();
    }

    /**
     * 解析订单查询日期。
     */
    private String resolveOrderQueryDate(List<?> records, String requestedDate) {
        if (requestedDate != null && !requestedDate.isBlank()) {
            return requestedDate;
        }
        if (!records.isEmpty() && records.get(0) instanceof Map<?, ?> order) {
            return safeGet(order, "orderTime", UNKNOWN);
        }
        return UNKNOWN;
    }

    /**
     * 过滤车次列表
     */
    private List<?> filterTrainList(List<?> trainList, String trainNumber) {
        if (trainNumber == null || trainNumber.isBlank()) {
            return trainList;
        }
        return trainList.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(train -> trainNumber.equalsIgnoreCase(safeGet(train, "trainNumber", "")))
                .toList();
    }

    /**
     * 构建乘车人姓名列表
     */
    private List<String> buildPassengerNames(Map<?, ?> order) {
        String realName = safeGet(order, "realName", "");
        return realName.isBlank() ? List.of() : List.of(realName);
    }

    /**
     * 构建席别详情
     */
    private Map<String, Object> buildSeatComponent(Map<?, ?> seat) {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("type", seatTypeName(safeGet(seat, "type", "")));
        component.put("price", formatPrice(safeGet(seat, "price", "0")));
        component.put("remaining", parseInt(safeGet(seat, "quantity", "0")));
        return component;
    }

    /**
     * 在数据中查找特定车次
     */
    private Map<?, ?> findTrain(Map<String, Object> data, String trainNumber) {
        List<?> trainList = getTrainList(data);
        for (Object item : trainList) {
            if (item instanceof Map<?, ?> train) {
                String actualTrainNumber = safeGet(train, "trainNumber", "");
                if (trainNumber == null || trainNumber.isBlank() || trainNumber.equalsIgnoreCase(actualTrainNumber)) {
                    return train;
                }
            }
        }
        return Map.of();
    }

    /**
     * 获取列车列表
     */
    private List<?> getTrainList(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        Object trainListObj = data.get("trainList");
        if (trainListObj instanceof List<?> trainList) {
            return trainList;
        }
        return List.of();
    }

    /**
     * 转换席别类型编码为中文名称
     */
    private String seatTypeName(String typeStr) {
        try {
            int type = Integer.parseInt(typeStr);
            return switch (type) {
                case 0 -> "商务座";
                case 1 -> "一等座";
                case 2 -> "二等座";
                case 3 -> "软卧";
                case 4 -> "硬卧";
                case 5 -> "硬座";
                case 6 -> "无座";
                default -> "其他";
            };
        } catch (NumberFormatException e) {
            return fallback(typeStr);
        }
    }

    /**
     * 格式化价格字符串
     */
    private String formatPrice(String priceStr) {
        try {
            return String.format("%." + PRICE_SCALE + "f", Double.parseDouble(priceStr));
        } catch (NumberFormatException e) {
            return fallback(priceStr);
        }
    }

    /**
     * 字符串转整数
     */
    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * 对订单号进行脱敏处理
     */
    private String maskOrderSn(String orderSn) {
        if (orderSn == null || orderSn.length() <= ORDER_SN_MASK_VISIBLE) {
            return orderSn == null ? UNKNOWN : orderSn;
        }
        return "****" + orderSn.substring(orderSn.length() - ORDER_SN_MASK_VISIBLE);
    }

    /**
     * 截断超长文本
     */
    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        int maxChars = getResultMaxChars();
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...[已截断]";
    }

    /**
     * 安全获取 Map 中的字符串值
     */
    private String safeGet(Map<?, ?> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString();
        return text.isBlank() ? defaultValue : text;
    }

    /**
     * 处理空值回退
     */
    private String fallback(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    /**
     * 获取摘要最大字符数
     */
    private int getResultMaxChars() {
        AiProperties.Agent agentConfig = aiProperties.getAgent();
        return agentConfig == null ? DEFAULT_RESULT_MAX_CHARS : agentConfig.getResultMaxChars();
    }

    /**
     * 获取车次最大展示条数
     */
    private int getMaxDisplayCount() {
        AiProperties.Agent agentConfig = aiProperties.getAgent();
        return agentConfig == null ? DEFAULT_MAX_DISPLAY_COUNT : agentConfig.getMaxDisplayCount();
    }

    /**
     * 获取订单最大展示条数
     */
    private int getOrderDisplayCount() {
        AiProperties.Agent agentConfig = aiProperties.getAgent();
        return agentConfig == null ? 3 : agentConfig.getOrderDisplayCount();
    }
}
