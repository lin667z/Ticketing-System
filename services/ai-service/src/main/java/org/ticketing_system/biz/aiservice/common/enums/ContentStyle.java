package org.ticketing_system.biz.aiservice.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 内容渲染风格，用于提示前端对文本进行差异化渲染
 */
public enum ContentStyle {

    GREETING,
    NORMAL,
    CLARIFICATION,
    INFO,
    WARNING,
    SUCCESS,
    ERROR,
    SUMMARY,
    SUGGESTION;

    @JsonValue
    public String value() {
        return name().toLowerCase();
    }
}
