package fr.traodev.component;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.model.Emoji;
import fr.traodev.util.Json;

public final class Component {
    private final ObjectNode json;

    private Component(ObjectNode json) {
        this.json = json;
    }

    public ObjectNode json() {
        return json;
    }

    private static Component of(int type) {
        return new Component(Json.object().put("type", type));
    }

    public static final int PRIMARY = 1, SECONDARY = 2, SUCCESS = 3, DANGER = 4, LINK = 5;

    public static Component button(int style, String customId, String label) {
        Component c = of(2);
        c.json.put("style", style).put("custom_id", customId);
        if (label != null) c.json.put("label", label);
        return c;
    }

    public static Component primary(String customId, String label) {
        return button(PRIMARY, customId, label);
    }

    public static Component secondary(String customId, String label) {
        return button(SECONDARY, customId, label);
    }

    public static Component success(String customId, String label) {
        return button(SUCCESS, customId, label);
    }

    public static Component danger(String customId, String label) {
        return button(DANGER, customId, label);
    }

    public static Component link(String url, String label) {
        Component c = of(2);
        c.json.put("style", LINK).put("url", url).put("label", label);
        return c;
    }

    public Component emoji(String unicode) {
        json.putObject("emoji").put("name", unicode);
        return this;
    }

    public Component emoji(Emoji e) {
        ObjectNode n = json.putObject("emoji").put("name", e.name());
        if (e.id() != 0) n.put("id", String.valueOf(e.id())).put("animated", e.animated());
        return this;
    }

    public Component disabled(boolean d) {
        json.put("disabled", d);
        return this;
    }

    public Component disabled() {
        return disabled(true);
    }

    public static Component row(Component... children) {
        Component c = of(1);
        ArrayNode arr = c.json.putArray("components");
        for (Component ch : children) arr.add(ch.json);
        return c;
    }

    public static Component stringSelect(String customId) {
        Component c = of(3);
        c.json.put("custom_id", customId);
        c.json.putArray("options");
        return c;
    }

    public Component option(String label, String value) {
        return option(label, value, null);
    }

    public Component option(String label, String value, String description) {
        ObjectNode o = ((ArrayNode) json.get("options")).addObject().put("label", label).put("value", value);
        if (description != null) o.put("description", description);
        return this;
    }

    public static Component userSelect(String customId) {
        return entitySelect(5, customId);
    }

    public static Component roleSelect(String customId) {
        return entitySelect(6, customId);
    }

    public static Component mentionableSelect(String customId) {
        return entitySelect(7, customId);
    }

    public static Component channelSelect(String customId, int... channelTypes) {
        Component c = entitySelect(8, customId);
        if (channelTypes.length > 0) {
            ArrayNode a = c.json.putArray("channel_types");
            for (int t : channelTypes) a.add(t);
        }
        return c;
    }

    private static Component entitySelect(int type, String customId) {
        Component c = of(type);
        c.json.put("custom_id", customId);
        return c;
    }

    public Component placeholder(String p) {
        json.put("placeholder", p);
        return this;
    }

    public Component range(int min, int max) {
        json.put("min_values", min).put("max_values", max);
        return this;
    }

    public Component required(boolean r) {
        json.put("required", r);
        return this;
    }

    public static final int SHORT = 1, PARAGRAPH = 2;

    public static Component textInput(String customId, int style) {
        Component c = of(4);
        c.json.put("custom_id", customId).put("style", style);
        return c;
    }

    public Component length(int min, int max) {
        json.put("min_length", min).put("max_length", max);
        return this;
    }

    public Component value(String v) {
        json.put("value", v);
        return this;
    }

    public static Component label(String label, String description, Component input) {
        Component c = of(18);
        c.json.put("label", label);
        if (description != null) c.json.put("description", description);
        c.json.set("component", input.json);
        return c;
    }

    public static Component text(String markdown) {
        Component c = of(10);
        c.json.put("content", markdown);
        return c;
    }

    public static Component separator() {
        return of(14);
    }

    public static Component separator(boolean divider, boolean largeSpacing) {
        Component c = of(14);
        c.json.put("divider", divider).put("spacing", largeSpacing ? 2 : 1);
        return c;
    }

    public static Component container(Integer accentColor, Component... children) {
        Component c = of(17);
        if (accentColor != null) c.json.put("accent_color", accentColor);
        ArrayNode arr = c.json.putArray("components");
        for (Component ch : children) arr.add(ch.json);
        return c;
    }

    public static Component section(Component accessory, Component... texts) {
        Component c = of(9);
        ArrayNode arr = c.json.putArray("components");
        for (Component t : texts) arr.add(t.json);
        c.json.set("accessory", accessory.json);
        return c;
    }

    public static Component thumbnail(String url) {
        Component c = of(11);
        c.json.putObject("media").put("url", url);
        return c;
    }

    public static Component mediaGallery(String... urls) {
        Component c = of(12);
        ArrayNode items = c.json.putArray("items");
        for (String u : urls) items.addObject().putObject("media").put("url", u);
        return c;
    }

    public static Component file(String attachmentUrl) {
        Component c = of(13);
        c.json.putObject("file").put("url", attachmentUrl);
        return c;
    }
}
