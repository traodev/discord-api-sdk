package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.model.Message;

public record MessageUpdateEvent(Bot bot, Message message) implements Event {
}
