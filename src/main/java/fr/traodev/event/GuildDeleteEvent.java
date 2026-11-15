package fr.traodev.event;

import fr.traodev.Bot;

public record GuildDeleteEvent(Bot bot, long guildId, boolean unavailable) implements Event {
}
