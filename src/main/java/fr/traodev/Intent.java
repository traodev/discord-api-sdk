package fr.traodev;

import java.util.Collection;
import java.util.EnumSet;

public enum Intent {
    GUILDS(0), GUILD_MEMBERS(1), GUILD_MODERATION(2), GUILD_EXPRESSIONS(3), GUILD_INTEGRATIONS(4),
    GUILD_WEBHOOKS(5), GUILD_INVITES(6), GUILD_VOICE_STATES(7), GUILD_PRESENCES(8), GUILD_MESSAGES(9),
    GUILD_MESSAGE_REACTIONS(10), GUILD_MESSAGE_TYPING(11), DIRECT_MESSAGES(12), DIRECT_MESSAGE_REACTIONS(13),
    DIRECT_MESSAGE_TYPING(14), MESSAGE_CONTENT(15), GUILD_SCHEDULED_EVENTS(16),
    AUTO_MODERATION_CONFIGURATION(20), AUTO_MODERATION_EXECUTION(21),
    GUILD_MESSAGE_POLLS(24), DIRECT_MESSAGE_POLLS(25);

    private final long bit;

    Intent(int shift) {
        this.bit = 1L << shift;
    }

    public long bit() {
        return bit;
    }

    public boolean privileged() {
        return this == GUILD_MEMBERS || this == GUILD_PRESENCES || this == MESSAGE_CONTENT;
    }

    public static long mask(Collection<Intent> intents) {
        long m = 0;
        for (Intent i : intents) m |= i.bit;
        return m;
    }

    public static EnumSet<Intent> all() {
        return EnumSet.allOf(Intent.class);
    }

    public static EnumSet<Intent> nonPrivileged() {
        EnumSet<Intent> set = EnumSet.allOf(Intent.class);
        set.removeIf(Intent::privileged);
        return set;
    }
}
