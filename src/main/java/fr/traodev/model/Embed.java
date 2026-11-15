package fr.traodev.model;

import java.util.List;

public record Embed(String title, String type, String description, String url, String timestamp, Integer color,
                    Footer footer, Media image, Media thumbnail, Author author, List<Field> fields) {
    public Embed {
        fields = fields == null ? List.of() : fields;
    }

    public record Footer(String text, String iconUrl) {
    }

    public record Media(String url, String proxyUrl, Integer height, Integer width) {
    }

    public record Author(String name, String url, String iconUrl) {
    }

    public record Field(String name, String value, boolean inline) {
    }
}
