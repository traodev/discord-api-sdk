package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.component.MessageBuilder;
import fr.traodev.model.Interaction;
import fr.traodev.model.Message;

import java.util.concurrent.CompletableFuture;

public abstract class ComponentEvent extends InteractionEvent {
    protected ComponentEvent(Bot bot, Interaction i) {
        super(bot, i);
    }

    public String customId() {
        return interaction.data().customId();
    }

    public Message message() {
        return interaction.message();
    }

    public CompletableFuture<Void> updateMessage(MessageBuilder m) {
        return ackWith(7, m.toJson());
    }

    public CompletableFuture<Void> updateMessage(String content) {
        return updateMessage(MessageBuilder.of(content));
    }

    public CompletableFuture<Void> deferUpdate() {
        return ackWith(6, null);
    }
}
