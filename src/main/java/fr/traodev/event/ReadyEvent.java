package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.model.User;

public record ReadyEvent(Bot bot, int shardId, User self, int guildCount, String sessionId) implements Event {
}
