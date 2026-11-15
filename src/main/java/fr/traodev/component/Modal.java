package fr.traodev.component;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.util.Json;

public final class Modal {
    private final ObjectNode data = Json.object();
    private final ArrayNode components = data.putArray("components");

    private Modal(String customId, String title) {
        data.put("custom_id", customId).put("title", title);
    }

    public static Modal create(String customId, String title) {
        return new Modal(customId, title);
    }

    public Modal add(String label, Component input) {
        components.add(Component.label(label, null, input).json());
        return this;
    }

    public Modal add(String label, String description, Component input) {
        components.add(Component.label(label, description, input).json());
        return this;
    }

    public Modal shortText(String customId, String label) {
        return add(label, Component.textInput(customId, Component.SHORT));
    }

    public Modal paragraph(String customId, String label) {
        return add(label, Component.textInput(customId, Component.PARAGRAPH));
    }

    public Modal text(String markdown) {
        components.add(Component.text(markdown).json());
        return this;
    }

    public ObjectNode data() {
        return data;
    }
}
