package fr.traodev.event;

import fr.traodev.Bot;

public record MessageDeleteEvent(Bot bot, long messageId, long channelId, long guildId) implements Event {
}
