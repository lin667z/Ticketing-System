package org.ticketing_system.biz.aiservice.session.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 压缩后的非结构化上下文，绝不可作为可执行输入使用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummaryContext {

    @Builder.Default
    private List<String> facts = new ArrayList<>();

    public static SessionSummaryContext empty() {
        return SessionSummaryContext.builder()
                .facts(new ArrayList<>())
                .build();
    }
}
