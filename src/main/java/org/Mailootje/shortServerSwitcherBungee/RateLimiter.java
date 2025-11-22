package org.Mailootje.shortServerSwitcherBungee;

import java.util.ArrayDeque;
import java.util.Deque;

public class RateLimiter {
    private final Deque<Long> hits = new ArrayDeque<>();
    private long blockedUntil = 0;

    public boolean tryHit(int maxHits, int perSeconds, int blockSeconds) {
        long now = System.currentTimeMillis();
        if (now < blockedUntil) return false;

        long window = perSeconds * 1000L;
        while (!hits.isEmpty() && now - hits.peekFirst() > window) {
            hits.pollFirst();
        }

        if (hits.size() >= maxHits) {
            blockedUntil = now + blockSeconds * 1000L;
            hits.clear();
            return false;
        }

        hits.addLast(now);
        return true;
    }

    public boolean isBlocked(int blockSeconds) {
        return System.currentTimeMillis() < blockedUntil;
    }

    public int blockSecondsLeft(int blockSeconds) {
        long leftMs = blockedUntil - System.currentTimeMillis();
        return (int) Math.max(0, leftMs / 1000L);
    }
}
