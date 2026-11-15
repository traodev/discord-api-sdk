package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.model.Emoji;
import fr.traodev.model.Member;

public record ReactionAddEvent(Bot bot, long userId, long channelId, long messageId, long guildId, Emoji emoji,
                               Member member) implements Event {
}
