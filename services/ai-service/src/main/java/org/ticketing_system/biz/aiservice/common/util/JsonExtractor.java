package org.ticketing_system.biz.aiservice.common.util;

/**
 * 从 LLM 文本响应中提取首个完整的 JSON 对象。
 *
 * <p>取代原先各处的贪婪正则 {@code \{[\s\S]*\}}：贪婪匹配会一路吞到文本里最后一个 {@code '}'}，
 * 当模型输出包含多个 JSON 块、尾随说明文字或 Markdown 围栏时极易截出错误片段。
 * 这里以括号配平方式从第一个 {@code '{'} 起精确截取到与之匹配的 {@code '}'}，
 * 并正确跳过字符串字面量内的括号与转义字符。</p>
 */
public final class JsonExtractor {

    private JsonExtractor() {
    }

    /**
     * 提取首个配平的 JSON 对象子串；找不到完整对象时返回 {@code "{}"}。
     */
    public static String firstJsonObject(String content) {
        if (content == null || content.isBlank()) {
            return "{}";
        }
        int start = content.indexOf('{');
        if (start < 0) {
            return "{}";
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(start, i + 1);
                }
            }
        }
        // 没有配平的闭合括号，返回原文交由下游解析器报错并走各自的回退逻辑
        return content.substring(start);
    }
}
