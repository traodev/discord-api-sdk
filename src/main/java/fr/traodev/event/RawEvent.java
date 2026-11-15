package fr.traodev.event;

import com.fasterxml.jackson.databind.JsonNode;
import fr.traodev.Bot;

public record RawEvent(Bot bot, int shardId, String name, JsonNode data) implements Event {
}
