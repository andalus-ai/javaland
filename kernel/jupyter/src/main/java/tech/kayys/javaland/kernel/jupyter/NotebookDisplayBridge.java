package tech.kayys.javaland.kernel.jupyter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Java-facing display API injected into a persistent notebook JShell session. */
public final class NotebookDisplayBridge {
    private static final Map<String, NotebookDisplayBridge> SESSIONS = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<NotebookDisplayEvent> events = new ConcurrentLinkedQueue<>();
    private final String sessionId;

    private NotebookDisplayBridge(String sessionId) { this.sessionId = sessionId; }

    /** Resolves the bridge instance installed for a JShell session. */
    public static NotebookDisplayBridge attach(String sessionId) {
        NotebookDisplayBridge bridge = SESSIONS.get(Objects.requireNonNull(sessionId, "sessionId"));
        if (bridge == null) throw new IllegalStateException("Notebook display session is no longer active");
        return bridge;
    }

    static NotebookDisplayBridge create() {
        String id = UUID.randomUUID().toString();
        NotebookDisplayBridge bridge = new NotebookDisplayBridge(id);
        SESSIONS.put(id, bridge);
        return bridge;
    }

    /** Publishes a MIME bundle and returns its display identifier. */
    public String display(Map<String, ?> data) { return display(data, Map.of()); }

    /** Publishes a MIME bundle with metadata and returns its display identifier. */
    public String display(Map<String, ?> data, Map<String, ?> metadata) {
        String id = UUID.randomUUID().toString();
        events.add(new NotebookDisplayEvent(NotebookDisplayEvent.Type.DISPLAY, id, immutableMap(data), immutableMap(metadata), false));
        return id;
    }

    /** Publishes a plain-text representation of a Java value. */
    public String display(Object value) { return display(Map.of("text/plain", String.valueOf(value))); }

    /** Updates a display previously created in this kernel session. */
    public void update(String id, Map<String, ?> data) { update(id, data, Map.of()); }

    /** Updates a display previously created in this kernel session. */
    public void update(String id, Map<String, ?> data, Map<String, ?> metadata) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("displayId must not be blank");
        events.add(new NotebookDisplayEvent(NotebookDisplayEvent.Type.UPDATE, id, immutableMap(data), immutableMap(metadata), false));
    }

    /** Requests clearing the current frontend output area. */
    public void clear(boolean wait) { events.add(new NotebookDisplayEvent(NotebookDisplayEvent.Type.CLEAR, "", Map.of(), Map.of(), wait)); }

    List<NotebookDisplayEvent> drainEvents() {
        List<NotebookDisplayEvent> result = new ArrayList<>();
        NotebookDisplayEvent event;
        while ((event = events.poll()) != null) result.add(event);
        return List.copyOf(result);
    }

    void close() { SESSIONS.remove(sessionId, this); events.clear(); }

    private static Map<String, Object> immutableMap(Map<String, ?> source) {
        Objects.requireNonNull(source, "map must not be null");
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "MIME type must not be null"),
                Objects.requireNonNull(value, "MIME value must not be null")));
        return Map.copyOf(copy);
    }

}
