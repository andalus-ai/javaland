package tech.kayys.javaland.protocol.jupyter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/** Standard Jupyter message header. */
public record JupyterMessageHeader(
        @JsonProperty("msg_id") String messageId,
        String username,
        String session,
        String date,
        @JsonProperty("msg_type") String messageType,
        String version) {

    public JupyterMessageHeader {
        requireText(messageId, "msg_id");
        requireText(username, "username");
        requireText(session, "session");
        requireText(date, "date");
        requireText(messageType, "msg_type");
        requireText(version, "version");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
