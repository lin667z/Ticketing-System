package org.ticketing_system.biz.aiservice.memory.episodic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话事件收集器，暂存单次会话中产生的事件
 */
public class SessionEventCollector {

    // 内部事件列表
    private final List<SessionEvent> events = new ArrayList<>();

    /**
     * 记录一个事件
     */
    public void record(SessionEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    /**
     * 获取不可修改的事件列表
     */
    public List<SessionEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    /**
     * 事件数量
     */
    public int size() {
        return events.size();
    }

    /**
     * 清空所有事件
     */
    public void clear() {
        events.clear();
    }
}
