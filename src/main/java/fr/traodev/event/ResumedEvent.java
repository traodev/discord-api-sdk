package fr.traodev.event;

import fr.traodev.Bot;

public record ResumedEvent(Bot bot, int shardId) implements Event {
}
