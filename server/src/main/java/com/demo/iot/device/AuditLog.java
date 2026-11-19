package com.demo.iot.device;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 内存版操作审计，保留最近 50 条，服务重启后清空。
 */
@Component
public class AuditLog {

    private static final int MAX_ENTRIES = 50;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final Deque<Entry> entries = new ArrayDeque<>();

    public synchronized void record(String deviceId, String action, boolean ok, String detail) {
        entries.addFirst(new Entry(LocalDateTime.now().format(TIME), deviceId, action, ok, detail));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    public synchronized List<Entry> recent() {
        return new ArrayList<>(entries);
    }

    public record Entry(String time, String deviceId, String action, boolean ok, String detail) {
    }
}
