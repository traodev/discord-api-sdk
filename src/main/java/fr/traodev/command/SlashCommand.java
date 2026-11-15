package fr.traodev.command;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.Permission;
import fr.traodev.event.AutocompleteEvent;
import fr.traodev.event.SlashCommandEvent;
import fr.traodev.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class SlashCommand {
    private final String name;
    private final String description;
    private final ObjectNode extra = Json.object();
    private Consumer<SlashCommandEvent> handler;
    private final List<Option> options = new ArrayList<>();
    private final Map<String, Sub> subs = new LinkedHashMap<>();
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private long guildId;

    private SlashCommand(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static SlashCommand of(String name, String description) {
        return new SlashCommand(name, description);
    }

    public static SlashCommand of(String name, String description, Consumer<SlashCommandEvent> handler, Option... options) {
        SlashCommand c = new SlashCommand(name, description);
        c.handler = handler;
        c.options.addAll(List.of(options));
        return c;
    }

    public SlashCommand option(Option o) {
        options.add(o);
        return this;
    }

    public SlashCommand handler(Consumer<SlashCommandEvent> h) {
        this.handler = h;
        return this;
    }

    public SlashCommand sub(String name, String description, Consumer<SlashCommandEvent> h, Option... opts) {
        subs.put(name, new Sub(name, description, h, List.of(opts)));
        return this;
    }

    public SlashCommand group(String name, String description, Consumer<Group> config) {
        Group g = new Group(name, description);
        config.accept(g);
        groups.put(name, g);
        return this;
    }

    public SlashCommand requirePermissions(Permission... perms) {
        extra.put("default_member_permissions", Long.toString(Permission.of(perms)));
        return this;
    }

    public SlashCommand nsfw() {
        extra.put("nsfw", true);
        return this;
    }

    public SlashCommand guildOnly() {
        extra.putArray("contexts").add(0);
        return this;
    }

    public SlashCommand localizeName(String locale, String value) {
        ((ObjectNode) (extra.has("name_localizations") ? extra.get("name_localizations") : extra.putObject("name_localizations"))).put(locale, value);
        return this;
    }

    public SlashCommand localizeDescription(String locale, String value) {
        ((ObjectNode) (extra.has("description_localizations") ? extra.get("description_localizations") : extra.putObject("description_localizations"))).put(locale, value);
        return this;
    }

    public SlashCommand inGuild(long guildId) {
        this.guildId = guildId;
        return this;
    }

    public long guildId() {
        return guildId;
    }

    public String name() {
        return name;
    }

    public record Sub(String name, String description, Consumer<SlashCommandEvent> handler,
                      List<Option> options) {
    }

    public static final class Group {
        private final String name, description;
        private final Map<String, Sub> subs = new LinkedHashMap<>();

        private Group(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public Group sub(String name, String description, Consumer<SlashCommandEvent> h, Option... opts) {
            subs.put(name, new Sub(name, description, h, List.of(opts)));
            return this;
        }
    }

    Map<String, Consumer<SlashCommandEvent>> handlers() {
        Map<String, Consumer<SlashCommandEvent>> m = new LinkedHashMap<>();
        if (handler != null) m.put(name, handler);
        subs.values().forEach(s -> m.put(name + " " + s.name, s.handler));
        groups.values().forEach(g -> g.subs.values().forEach(s -> m.put(name + " " + g.name + " " + s.name, s.handler)));
        return m;
    }

    Map<String, Consumer<AutocompleteEvent>> autocompletes() {
        Map<String, Consumer<AutocompleteEvent>> m = new LinkedHashMap<>();
        options.forEach(o -> reg(m, name, o));
        subs.values().forEach(s -> s.options.forEach(o -> reg(m, name + " " + s.name, o)));
        groups.values().forEach(g -> g.subs.values().forEach(s -> s.options.forEach(o -> reg(m, name + " " + g.name + " " + s.name, o))));
        return m;
    }

    private static void reg(Map<String, Consumer<AutocompleteEvent>> m, String path, Option o) {
        if (o.autocompleteHandler() != null) m.put(path + "/" + o.name(), o.autocompleteHandler());
    }

    public ObjectNode toJson() {
        ObjectNode n = Json.object().put("type", 1).put("name", name).put("description", description);
        n.setAll(extra.deepCopy());
        ArrayNode opts = Json.array();
        for (Option o : options) opts.add(o.toJson());
        for (Sub s : subs.values()) opts.add(subJson(s));
        for (Group g : groups.values()) {
            ObjectNode gj = Json.object().put("type", 2).put("name", g.name).put("description", g.description);
            ArrayNode ga = gj.putArray("options");
            g.subs.values().forEach(s -> ga.add(subJson(s)));
            opts.add(gj);
        }
        if (!opts.isEmpty()) n.set("options", opts);
        return n;
    }

    private static ObjectNode subJson(Sub s) {
        ObjectNode sj = Json.object().put("type", 1).put("name", s.name).put("description", s.description);
        if (!s.options.isEmpty()) {
            ArrayNode a = sj.putArray("options");
            s.options.forEach(o -> a.add(o.toJson()));
        }
        return sj;
    }
}
