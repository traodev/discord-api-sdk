package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.component.MessageBuilder;
import fr.traodev.model.Message;
import fr.traodev.model.User;

import java.util.concurrent.CompletableFuture;

public record MessageCreateEvent(Bot bot, Message message) implements Event {
    public User author() {
        return message.author();
    }

    public String content() {
        return message.content();
    }

    public long channelId() {
        return message.channelId();
    }

    public long guildId() {
        return message.guildId();
    }

    public CompletableFuture<Message> reply(String text) {
        return reply(MessageBuilder.of(text));
    }

    public CompletableFuture<Message> reply(MessageBuilder m) {
        return bot.api().sendMessage(message.channelId(), m.replyTo(message.id()).safeMentions(false));
    }

    public CompletableFuture<Message> send(String text) {
        return bot.api().sendMessage(message.channelId(), text);
    }

    public CompletableFuture<Message> send(MessageBuilder m) {
        return bot.api().sendMessage(message.channelId(), m);
    }

    public CompletableFuture<Void> react(String emoji) {
        return bot.api().addReaction(message.channelId(), message.id(), emoji);
    }
}
