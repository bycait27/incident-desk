package com.incidentdesk.config;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FixedWindowRateLimiter {

    private static final long WINDOW_NANOS = 60_000_000_000L;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Window> windows = new LinkedHashMap<>(128, 0.75f, true);
    private long lastCleanupNanos;

    public synchronized Decision tryAcquire(String key, int limit, long nowNanos) {
        Window current = windows.get(key);
        if (current == null || nowNanos - current.startedAtNanos() >= WINDOW_NANOS) {
            if (current == null && (windows.size() >= MAX_TRACKED_KEYS
                    || nowNanos - lastCleanupNanos >= WINDOW_NANOS)) {
                removeExpiredWindows(nowNanos);
                lastCleanupNanos = nowNanos;
            }
            if (current == null && windows.size() >= MAX_TRACKED_KEYS) {
                Iterator<String> keys = windows.keySet().iterator();
                if (keys.hasNext()) {
                    keys.next();
                    keys.remove();
                }
            }
            windows.put(key, new Window(nowNanos, 1));
            return new Decision(true, 0);
        }

        if (current.requestCount() >= limit) {
            long remainingNanos = WINDOW_NANOS - (nowNanos - current.startedAtNanos());
            long retryAfterSeconds = Math.max(1, (remainingNanos + 999_999_999L) / 1_000_000_000L);
            return new Decision(false, retryAfterSeconds);
        }

        windows.put(key, new Window(current.startedAtNanos(), current.requestCount() + 1));
        return new Decision(true, 0);
    }

    private void removeExpiredWindows(long nowNanos) {
        windows.entrySet().removeIf(entry ->
                nowNanos - entry.getValue().startedAtNanos() >= WINDOW_NANOS);
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private record Window(long startedAtNanos, int requestCount) {
    }
}
