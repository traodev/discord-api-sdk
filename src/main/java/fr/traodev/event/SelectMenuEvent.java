package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.model.Interaction;
import fr.traodev.model.Resolved;

import java.util.List;

public final class SelectMenuEvent extends ComponentEvent {
    public SelectMenuEvent(Bot bot, Interaction i) {
        super(bot, i);
    }

    public List<String> values() {
        return interaction.data().values();
    }

    public String firstValue() {
        return values().isEmpty() ? null : values().get(0);
    }

    public Resolved resolved() {
        return interaction.data().resolved();
    }
}
