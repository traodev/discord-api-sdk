package fr.traodev.model;

public record Interaction(long id, long applicationId, int type, InteractionData data, long guildId, long channelId,
                          Channel channel, Member member, User user, String token, int version, Message message,
                          String appPermissions, String locale, String guildLocale) {
    public static final int PING = 1, APPLICATION_COMMAND = 2, MESSAGE_COMPONENT = 3, AUTOCOMPLETE = 4, MODAL_SUBMIT = 5;

    public User invoker() {
        return member != null && member.user() != null ? member.user() : user;
    }
}
