package org.ticketing_system.biz.aiservice.session.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 提取的原始 slot 补丁，需经 SlotStateMerger 合并和 TaskValidator 验证后才可执行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotExtractionResult {

    @Builder.Default
    private Map<String, Object> slotPatch = new HashMap<>();

    @Builder.Default
    private List<String> clearSlots = new ArrayList<>();

    @Builder.Default
    private Map<String, Double> confidence = new HashMap<>();

    private String intentHint;

    public static SlotExtractionResult empty() {
        return SlotExtractionResult.builder()
                .slotPatch(new HashMap<>())
                .clearSlots(new ArrayList<>())
                .confidence(new HashMap<>())
                .build();
    }
}
