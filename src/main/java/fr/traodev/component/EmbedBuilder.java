package fr.traodev.component;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.util.Json;

import java.time.Instant;

public final class EmbedBuilder {
    private final ObjectNode json = Json.object();
    private ArrayNode fields;

    public EmbedBuilder title(String t) {
        json.put("title", t);
        return this;
    }

    public EmbedBuilder title(String t, String url) {
        json.put("title", t).put("url", url);
        return this;
    }

    public EmbedBuilder description(String d) {
        json.put("description", d);
        return this;
    }

    public EmbedBuilder color(int rgb) {
        json.put("color", rgb);
        return this;
    }

    public EmbedBuilder timestamp(Instant i) {
        json.put("timestamp", i.toString());
        return this;
    }

    public EmbedBuilder now() {
        return timestamp(Instant.now());
    }

    public EmbedBuilder footer(String text) {
        json.putObject("footer").put("text", text);
        return this;
    }

    public EmbedBuilder footer(String text, String iconUrl) {
        json.putObject("footer").put("text", text).put("icon_url", iconUrl);
        return this;
    }

    public EmbedBuilder image(String url) {
        json.putObject("image").put("url", url);
        return this;
    }

    public EmbedBuilder thumbnail(String url) {
        json.putObject("thumbnail").put("url", url);
        return this;
    }

    public EmbedBuilder author(String name) {
        json.putObject("author").put("name", name);
        return this;
    }

    public EmbedBuilder author(String name, String url, String iconUrl) {
        ObjectNode a = json.putObject("author").put("name", name);
        if (url != null) a.put("url", url);
        if (iconUrl != null) a.put("icon_url", iconUrl);
        return this;
    }

    public EmbedBuilder field(String name, String value) {
        return field(name, value, false);
    }

    public EmbedBuilder field(String name, String value, boolean inline) {
        if (fields == null) fields = json.putArray("fields");
        fields.addObject().put("name", name).put("value", value).put("inline", inline);
        return this;
    }

    public ObjectNode toJson() {
        return json.deepCopy();
    }
}
