package fr.traodev.event;

import fr.traodev.Bot;

public record ShardDisconnectEvent(Bot bot, int shardId, int code, String reason,
                                   boolean willReconnect) implements Event {
}
