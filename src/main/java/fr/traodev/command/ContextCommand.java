package fr.traodev.command;

import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.event.ContextMenuEvent;
import fr.traodev.util.Json;

import java.util.function.Consumer;

public final class ContextCommand {
    private final String name;
    private final int type;
    private final Consumer<ContextMenuEvent> handler;
    private long guildId;

    private ContextCommand(String name, int type, Consumer<ContextMenuEvent> handler) {
        this.name = name;
        this.type = type;
        this.handler = handler;
    }

    public static ContextCommand user(String name, Consumer<ContextMenuEvent> handler) {
        return new ContextCommand(name, 2, handler);
    }

    public static ContextCommand message(String name, Consumer<ContextMenuEvent> handler) {
        return new ContextCommand(name, 3, handler);
    }

    public ContextCommand inGuild(long guildId) {
        this.guildId = guildId;
        return this;
    }

    public long guildId() {
        return guildId;
    }

    public String name() {
        return name;
    }

    public int type() {
        return type;
    }

    Consumer<ContextMenuEvent> handler() {
        return handler;
    }

    public ObjectNode toJson() {
        return Json.object().put("type", type).put("name", name);
    }
}
