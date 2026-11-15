package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.model.Guild;

public record GuildCreateEvent(Bot bot, Guild guild) implements Event {
}
