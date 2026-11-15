package fr.traodev.rest;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.util.Json;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

final class Multipart {
    final byte[] body;
    final String contentType;

    private Multipart(byte[] body, String contentType) {
        this.body = body;
        this.contentType = contentType;
    }

    static Multipart build(ObjectNode payload, List<FileUpload> files) {
        ObjectNode json = payload == null ? Json.object() : payload;
        ArrayNode attachments = Json.array();
        for (int i = 0; i < files.size(); i++) {
            ObjectNode a = attachments.addObject();
            a.put("id", i).put("filename", files.get(i).name());
            if (files.get(i).description() != null) a.put("description", files.get(i).description());
        }
        json.set("attachments", attachments);

        String boundary = "jdisc-" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024 + files.stream().mapToInt(f -> f.data().length).sum());
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"payload_json\"\r\n"
            + "Content-Type: application/json\r\n\r\n");
        out.writeBytes(Json.toBytes(json));
        for (int i = 0; i < files.size(); i++) {
            FileUpload f = files.get(i);
            String safe = f.name().replace("\"", "_").replace("\r", "").replace("\n", "");
            write(out, "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"files[" + i
                + "]\"; filename=\"" + safe + "\"\r\nContent-Type: " + f.contentType() + "\r\n\r\n");
            out.writeBytes(f.data());
        }
        write(out, "\r\n--" + boundary + "--\r\n");
        return new Multipart(out.toByteArray(), "multipart/form-data; boundary=" + boundary);
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }
}
