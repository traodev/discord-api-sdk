package fr.traodev.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class EventBus {
    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Consumer<Object>>> listeners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<RawEvent>>> raw = new ConcurrentHashMap<>();
    private final Executor executor;

    public EventBus(Executor executor) {
        this.executor = executor;
    }

    @SuppressWarnings("unchecked")
    public <E extends Event> void register(Class<E> type, Consumer<? super E> listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add((Consumer<Object>) listener);
    }

    public void registerRaw(String eventName, Consumer<RawEvent> listener) {
        raw.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public boolean wants(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
            if (listeners.containsKey(c)) return true;
        return listeners.containsKey(Event.class);
    }

    public boolean wantsRaw(String name) {
        return raw.containsKey(name);
    }

    public void publish(Event event) {
        for (Class<?> c = event.getClass(); c != null && c != Object.class; c = c.getSuperclass())
            run(listeners.get(c), event);
        run(listeners.get(Event.class), event);
    }

    public void publishRaw(RawEvent e) {
        List<Consumer<RawEvent>> l = raw.get(e.name());
        if (l == null) return;
        for (Consumer<RawEvent> c : l)
            executor.execute(() -> {
                try {
                    c.accept(e);
                } catch (Throwable t) {
                    log.error("Raw listener for {} failed", e.name(), t);
                }
            });
    }

    private void run(List<Consumer<Object>> list, Event event) {
        if (list == null) return;
        for (Consumer<Object> l : list)
            executor.execute(() -> {
                try {
                    l.accept(event);
                } catch (Throwable t) {
                    log.error("Listener for {} failed", event.getClass().getSimpleName(), t);
                }
            });
    }
}
