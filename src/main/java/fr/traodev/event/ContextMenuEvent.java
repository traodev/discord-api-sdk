package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.model.Interaction;
import fr.traodev.model.Message;
import fr.traodev.model.User;

public final class ContextMenuEvent extends InteractionEvent {
    public ContextMenuEvent(Bot bot, Interaction i) {
        super(bot, i);
    }

    public String name() {
        return interaction.data().name();
    }

    public boolean isUserCommand() {
        return interaction.data().type() != null && interaction.data().type() == 2;
    }

    public long targetId() {
        return interaction.data().targetId();
    }

    public User targetUser() {
        return interaction.data().resolved().users().get(targetId());
    }

    public Message targetMessage() {
        return interaction.data().resolved().messages().get(targetId());
    }
}
