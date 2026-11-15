package fr.traodev.util;

import java.time.Instant;

public final class Snowflake {
    public static final long DISCORD_EPOCH = 1420070400000L;

    private Snowflake() {
    }

    public static long timestampMillis(long id) {
        return (id >>> 22) + DISCORD_EPOCH;
    }

    public static Instant toInstant(long id) {
        return Instant.ofEpochMilli(timestampMillis(id));
    }

    public static long fromInstant(Instant t) {
        return (t.toEpochMilli() - DISCORD_EPOCH) << 22;
    }

    public static int shardFor(long guildId, int shardCount) {
        return (int) ((guildId >>> 22) % shardCount);
    }

    public static long parse(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.isEmpty() ? 0 : Long.parseUnsignedLong(sb.toString());
    }
}
