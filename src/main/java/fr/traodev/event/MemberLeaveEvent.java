package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.model.User;

public record MemberLeaveEvent(Bot bot, long guildId, User user) implements Event {
}
