package com.pigeonmq.sdk;

/**
 * Quality of service levels supported by the broker.
 *
 * <ul>
 *     <li>{@link #AT_MOST_ONCE} — fire-and-forget.</li>
 *     <li>{@link #AT_LEAST_ONCE} — broker acknowledges receipt; default for pigeonMQ.</li>
 *     <li>{@link #EXACTLY_ONCE} — accepted by the protocol but pigeonMQ falls back to at-least-once semantics.</li>
 * </ul>
 */
public enum QoS {
    AT_MOST_ONCE(0),
    AT_LEAST_ONCE(1),
    EXACTLY_ONCE(2);

    private final int level;

    QoS(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public static QoS fromLevel(int level) {
        return switch (level) {
            case 0 -> AT_MOST_ONCE;
            case 1 -> AT_LEAST_ONCE;
            case 2 -> EXACTLY_ONCE;
            default -> throw new IllegalArgumentException("Unsupported QoS level: " + level);
        };
    }
}
