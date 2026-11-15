package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.model.Interaction;

public final class ButtonEvent extends ComponentEvent {
    public ButtonEvent(Bot bot, Interaction i) {
        super(bot, i);
    }
}
