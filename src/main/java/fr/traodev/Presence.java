package fr.traodev;

import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.util.Json;

public record Presence(String status, Activity activity) {
    public static Presence online() {
        return new Presence("online", null);
    }

    public static Presence online(Activity a) {
        return new Presence("online", a);
    }

    public static Presence idle(Activity a) {
        return new Presence("idle", a);
    }

    public static Presence dnd(Activity a) {
        return new Presence("dnd", a);
    }

    public static Presence invisible() {
        return new Presence("invisible", null);
    }

    public ObjectNode toJson() {
        ObjectNode n = Json.object();
        n.putNull("since");
        var arr = n.putArray("activities");
        if (activity != null) arr.add(activity.toJson());
        n.put("status", status).put("afk", false);
        return n;
    }
}
