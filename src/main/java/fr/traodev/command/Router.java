package fr.traodev.command;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class Router<E> {
    private final Map<String, Consumer<E>> exact = new ConcurrentHashMap<>();
    private final List<Map.Entry<String, Consumer<E>>> prefixes = new CopyOnWriteArrayList<>();

    public void register(String pattern, Consumer<E> handler) {
        if (pattern.endsWith("*")) prefixes.add(Map.entry(pattern.substring(0, pattern.length() - 1), handler));
        else exact.put(pattern, handler);
    }

    public boolean route(String customId, E event) {
        Consumer<E> h = exact.get(customId);
        if (h == null) for (var p : prefixes) if (customId.startsWith(p.getKey())) { h = p.getValue(); break; }
        if (h == null) return false;
        h.accept(event);
        return true;
    }
}
