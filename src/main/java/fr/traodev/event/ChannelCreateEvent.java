package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.model.Channel;

public record ChannelCreateEvent(Bot bot, Channel channel) implements Event {
}
