package edu.szu.agent.util;

import java.util.concurrent.TimeUnit;

/**
 * A minimal token-bucket rate limiter.
 *
 * <p>Provides smooth throttling without adding a dependency such as Guava.
 * The limiter is thread-safe and blocks the caller until the requested
 * number of permits is available.
 *
 * // 编程技术: token bucket / synchronized / nanoTime
 *
 * @since 0.7.0
 * @author 王子豪
 */
public final class SimpleRateLimiter {

    private final double maxPermits;
    private final Object lock = new Object();

    private double storedPermits;
    private long lastNanos;

    /**
     * Creates a limiter that issues {@code permitsPerSecond} permits per second.
     *
     * @param permitsPerSecond positive rate
     * @since 0.7.0
     * @author 王子豪
     */
    public SimpleRateLimiter(double permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be positive");
        }
        this.maxPermits = permitsPerSecond;
        this.storedPermits = permitsPerSecond;
        this.lastNanos = System.nanoTime();
    }

    /**
     * Acquires a single permit, blocking until it is available.
     *
     * @since 0.7.0
     * @author 王子豪
     */
    public void acquire() {
        acquire(1);
    }

    /**
     * Acquires {@code permits} permits, blocking until they are available.
     *
     * @param permits number of permits to acquire
     * @since 0.7.0
     * @author 王子豪
     */
    public void acquire(int permits) {
        if (permits <= 0) {
            return;
        }

        long waitNanos;
        synchronized (lock) {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastNanos) / 1_000_000_000.0;
            storedPermits = Math.min(maxPermits, storedPermits + elapsedSeconds * maxPermits);
            lastNanos = now;

            double deficit = permits - storedPermits;
            if (deficit <= 0) {
                storedPermits -= permits;
                return;
            }

            waitNanos = (long) (deficit / maxPermits * 1_000_000_000.0);
            storedPermits = 0;
            lastNanos = now + waitNanos;
        }

        sleepNanos(waitNanos);
    }

    private static void sleepNanos(long nanos) {
        long millis = TimeUnit.NANOSECONDS.toMillis(nanos);
        int remainingNanos = (int) (nanos - TimeUnit.MILLISECONDS.toNanos(millis));
        try {
            Thread.sleep(millis, remainingNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rate limiter wait interrupted", e);
        }
    }
}
