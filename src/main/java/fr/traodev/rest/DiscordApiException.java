package fr.traodev.rest;

import com.fasterxml.jackson.databind.JsonNode;

public class DiscordApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int status;
    private final int code;
    private final transient JsonNode errors;

    public DiscordApiException(int status, int code, String message, JsonNode errors) {
        super("HTTP " + status + " / code " + code + ": " + message + (errors != null ? " " + errors : ""));
        this.status = status;
        this.code = code;
        this.errors = errors;
    }

    public int status() {
        return status;
    }

    public int code() {
        return code;
    }

    public JsonNode errors() {
        return errors;
    }
}
