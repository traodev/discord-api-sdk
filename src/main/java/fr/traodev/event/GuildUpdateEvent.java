package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.model.Guild;

public record GuildUpdateEvent(Bot bot, Guild guild) implements Event {
}
