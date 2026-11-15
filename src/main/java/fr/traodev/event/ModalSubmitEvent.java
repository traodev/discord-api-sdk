package fr.traodev.event;

import com.fasterxml.jackson.databind.JsonNode;
import fr.traodev.Bot;
import fr.traodev.model.Interaction;

import java.util.ArrayList;
import java.util.List;

public final class ModalSubmitEvent extends InteractionEvent {
    public ModalSubmitEvent(Bot bot, Interaction i) {
        super(bot, i);
    }

    public String customId() {
        return interaction.data().customId();
    }

    public String value(String fieldId) {
        JsonNode n = find(interaction.data().components(), fieldId);
        if (n == null) return null;
        JsonNode v = n.get("value");
        return v == null || v.isNull() ? null : v.asText();
    }

    public List<String> values(String fieldId) {
        JsonNode n = find(interaction.data().components(), fieldId);
        List<String> out = new ArrayList<>();
        if (n != null && n.has("values")) n.get("values").forEach(v -> out.add(v.asText()));
        return out;
    }

    private static JsonNode find(JsonNode node, String id) {
        if (node == null) return null;
        if (node.isArray()) {
            for (JsonNode c : node) {
                JsonNode r = find(c, id);
                if (r != null) return r;
            }
            return null;
        }
        if (id.equals(node.path("custom_id").asText(null))) return node;
        JsonNode r = find(node.get("component"), id);
        return r != null ? r : find(node.get("components"), id);
    }
}
