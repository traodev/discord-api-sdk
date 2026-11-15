package fr.traodev.rest;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.concurrent.CompletableFuture;

final class PendingRequest {
    final Route route;
    final byte[] body;
    final String contentType;
    final String auditReason;
    final CompletableFuture<JsonNode> result = new CompletableFuture<>();
    int attempts;

    PendingRequest(Route route, byte[] body, String contentType, String auditReason) {
        this.route = route;
        this.body = body;
        this.contentType = contentType;
        this.auditReason = auditReason;
    }
}
