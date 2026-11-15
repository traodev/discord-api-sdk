package fr.traodev.rest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record FileUpload(String name, byte[] data, String contentType, String description) {
    public static FileUpload of(Path path) {
        try {
            String type = Files.probeContentType(path);
            return new FileUpload(path.getFileName().toString(), Files.readAllBytes(path),
                type != null ? type : "application/octet-stream", null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static FileUpload of(String name, byte[] data) {
        return new FileUpload(name, data, "application/octet-stream", null);
    }

    public static FileUpload text(String name, String text) {
        return new FileUpload(name, text.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8", null);
    }

    public FileUpload withDescription(String d) {
        return new FileUpload(name, data, contentType, d);
    }
}
