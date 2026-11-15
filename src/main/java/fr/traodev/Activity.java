package fr.traodev;

import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.util.Json;

public record Activity(int type, String name, String state, String url) {
    public static Activity playing(String name) {
        return new Activity(0, name, null, null);
    }

    public static Activity streaming(String name, String url) {
        return new Activity(1, name, null, url);
    }

    public static Activity listening(String name) {
        return new Activity(2, name, null, null);
    }

    public static Activity watching(String name) {
        return new Activity(3, name, null, null);
    }

    public static Activity competing(String name) {
        return new Activity(5, name, null, null);
    }

    public static Activity custom(String text) {
        return new Activity(4, "Custom Status", text, null);
    }

    ObjectNode toJson() {
        ObjectNode n = Json.object().put("type", type).put("name", name);
        if (state != null) n.put("state", state);
        if (url != null) n.put("url", url);
        return n;
    }
}
