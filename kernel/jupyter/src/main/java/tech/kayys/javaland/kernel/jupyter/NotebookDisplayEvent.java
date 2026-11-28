package tech.kayys.javaland.kernel.jupyter;

import java.util.Map;

/** A display operation emitted by Java code during one notebook cell. */
public record NotebookDisplayEvent(
        Type type, String displayId, Map<String, Object> data,
        Map<String, Object> metadata, boolean wait) {
    public enum Type { DISPLAY, UPDATE, CLEAR }
    public NotebookDisplayEvent {
        data = Map.copyOf(data);
        metadata = Map.copyOf(metadata);
    }
}
