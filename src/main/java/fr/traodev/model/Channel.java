package fr.traodev.model;

public record Channel(long id, int type, long guildId, String name, String topic, long parentId, Integer position,
                      boolean nsfw, long ownerId, long lastMessageId, String permissions) {
    public static final int GUILD_TEXT = 0, DM = 1, GUILD_VOICE = 2, GROUP_DM = 3, GUILD_CATEGORY = 4,
        GUILD_ANNOUNCEMENT = 5, ANNOUNCEMENT_THREAD = 10, PUBLIC_THREAD = 11, PRIVATE_THREAD = 12,
        GUILD_STAGE_VOICE = 13, GUILD_FORUM = 15;

    public String mention() {
        return "<#" + id + ">";
    }

    public boolean isThread() {
        return type == ANNOUNCEMENT_THREAD || type == PUBLIC_THREAD || type == PRIVATE_THREAD;
    }
}
