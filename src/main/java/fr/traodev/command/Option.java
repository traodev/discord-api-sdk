package fr.traodev.command;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.event.AutocompleteEvent;
import fr.traodev.util.Json;

import java.util.function.Consumer;

public final class Option {
    private final ObjectNode json = Json.object();
    private Consumer<AutocompleteEvent> autocomplete;

    private Option(int type, String name, String description) {
        json.put("type", type).put("name", name).put("description", description);
    }

    public static Option string(String name, String description) {
        return new Option(3, name, description);
    }

    public static Option integer(String name, String description) {
        return new Option(4, name, description);
    }

    public static Option bool(String name, String description) {
        return new Option(5, name, description);
    }

    public static Option user(String name, String description) {
        return new Option(6, name, description);
    }

    public static Option channel(String name, String description) {
        return new Option(7, name, description);
    }

    public static Option role(String name, String description) {
        return new Option(8, name, description);
    }

    public static Option mentionable(String name, String description) {
        return new Option(9, name, description);
    }

    public static Option number(String name, String description) {
        return new Option(10, name, description);
    }

    public static Option attachment(String name, String description) {
        return new Option(11, name, description);
    }

    public Option required() {
        json.put("required", true);
        return this;
    }

    public Option choices(String... values) {
        ArrayNode a = json.putArray("choices");
        for (String v : values) a.addObject().put("name", v).put("value", v);
        return this;
    }

    public Option choice(String name, String value) {
        ArrayNode a = json.has("choices") ? (ArrayNode) json.get("choices") : json.putArray("choices");
        a.addObject().put("name", name).put("value", value);
        return this;
    }

    public Option choice(String name, long value) {
        ArrayNode a = json.has("choices") ? (ArrayNode) json.get("choices") : json.putArray("choices");
        a.addObject().put("name", name).put("value", value);
        return this;
    }

    public Option min(long v) {
        json.put("min_value", v);
        return this;
    }

    public Option max(long v) {
        json.put("max_value", v);
        return this;
    }

    public Option minLength(int v) {
        json.put("min_length", v);
        return this;
    }

    public Option maxLength(int v) {
        json.put("max_length", v);
        return this;
    }

    public Option channelTypes(int... types) {
        ArrayNode a = json.putArray("channel_types");
        for (int t : types) a.add(t);
        return this;
    }

    public Option autocomplete(Consumer<AutocompleteEvent> handler) {
        json.put("autocomplete", true);
        this.autocomplete = handler;
        return this;
    }

    public String name() {
        return json.get("name").asText();
    }

    Consumer<AutocompleteEvent> autocompleteHandler() {
        return autocomplete;
    }

    ObjectNode toJson() {
        return json.deepCopy();
    }
}
