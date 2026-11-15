package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.model.Emoji;

public record ReactionRemoveEvent(Bot bot, long userId, long channelId, long messageId, long guildId,
                                  Emoji emoji) implements Event {
}
