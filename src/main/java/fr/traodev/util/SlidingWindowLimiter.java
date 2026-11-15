package fr.traodev.util;

import java.util.ArrayDeque;

public final class SlidingWindowLimiter {
    private final int max;
    private final long windowMs;
    private final ArrayDeque<Long> stamps = new ArrayDeque<>();

    public SlidingWindowLimiter(int max, long windowMs) {
        this.max = max;
        this.windowMs = windowMs;
    }

    public synchronized long acquire() {
        long now = System.currentTimeMillis();
        while (!stamps.isEmpty() && now - stamps.peekFirst() >= windowMs) stamps.pollFirst();
        if (stamps.size() < max) {
            stamps.addLast(now);
            return 0;
        }
        return Math.max(1, windowMs - (now - stamps.peekFirst()));
    }
}
