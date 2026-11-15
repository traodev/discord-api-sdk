package fr.traodev.model;

public record Attachment(long id, String filename, String description, String contentType, long size,
                         String url, String proxyUrl, Integer height, Integer width, boolean ephemeral) {
}
