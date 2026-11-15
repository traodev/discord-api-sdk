package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.model.Interaction;
import fr.traodev.model.OptionData;
import fr.traodev.util.Json;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class AutocompleteEvent extends InteractionEvent {
    private final String commandPath;
    private final OptionData focused;

    public AutocompleteEvent(Bot bot, Interaction i, String commandPath, OptionData focused) {
        super(bot, i);
        this.commandPath = commandPath;
        this.focused = focused;
    }

    public String commandPath() {
        return commandPath;
    }

    public OptionData focused() {
        return focused;
    }

    public String focusedName() {
        return focused == null ? null : focused.name();
    }

    public String focusedValue() {
        return focused == null || focused.value() == null ? "" : focused.value().asText();
    }

    public CompletableFuture<Void> suggest(Collection<String> values) {
        ArrayNode choices = Json.array();
        int n = 0;
        for (String v : values) {
            if (n++ == 25) break;
            String shown = v.length() > 100 ? v.substring(0, 100) : v;
            choices.addObject().put("name", shown).put("value", shown);
        }
        return send(choices);
    }

    public CompletableFuture<Void> suggest(Map<String, String> nameToValue) {
        ArrayNode choices = Json.array();
        int n = 0;
        for (var e : nameToValue.entrySet()) {
            if (n++ == 25) break;
            choices.addObject().put("name", e.getKey()).put("value", e.getValue());
        }
        return send(choices);
    }

    private CompletableFuture<Void> send(ArrayNode choices) {
        ObjectNode data = Json.object();
        data.set("choices", choices);
        return ackWith(8, data);
    }
}
