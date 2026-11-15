package fr.traodev.component;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.rest.FileUpload;
import fr.traodev.util.Json;

import java.util.ArrayList;
import java.util.List;

public final class MessageBuilder {
    public static final int SUPPRESS_EMBEDS = 1 << 2, EPHEMERAL = 1 << 6, SUPPRESS_NOTIFICATIONS = 1 << 12,
        COMPONENTS_V2 = 1 << 15;

    private final ObjectNode json = Json.object();
    private final List<FileUpload> files = new ArrayList<>();
    private int flags;

    public static MessageBuilder create() {
        return new MessageBuilder();
    }

    public static MessageBuilder of(String content) {
        return new MessageBuilder().content(content);
    }

    public static MessageBuilder of(EmbedBuilder... embeds) {
        return new MessageBuilder().embeds(embeds);
    }

    public MessageBuilder content(String c) {
        json.put("content", c);
        return this;
    }

    public MessageBuilder tts(boolean t) {
        json.put("tts", t);
        return this;
    }

    public MessageBuilder embeds(EmbedBuilder... embeds) {
        ArrayNode arr = json.has("embeds") ? (ArrayNode) json.get("embeds") : json.putArray("embeds");
        for (EmbedBuilder e : embeds) arr.add(e.toJson());
        return this;
    }

    public MessageBuilder embed(EmbedBuilder e) {
        return embeds(e);
    }

    public MessageBuilder components(Component... rows) {
        ArrayNode arr = json.has("components") ? (ArrayNode) json.get("components") : json.putArray("components");
        for (Component c : rows) arr.add(c.json());
        return this;
    }

    public MessageBuilder row(Component... children) {
        return components(Component.row(children));
    }

    public MessageBuilder componentsV2(Component... top) {
        flags |= COMPONENTS_V2;
        return components(top);
    }

    public MessageBuilder file(FileUpload f) {
        files.add(f);
        return this;
    }

    public MessageBuilder ephemeral() {
        flags |= EPHEMERAL;
        return this;
    }

    public MessageBuilder ephemeral(boolean e) {
        if (e) flags |= EPHEMERAL;
        else flags &= ~EPHEMERAL;
        return this;
    }

    public MessageBuilder suppressEmbeds() {
        flags |= SUPPRESS_EMBEDS;
        return this;
    }

    public MessageBuilder silent() {
        flags |= SUPPRESS_NOTIFICATIONS;
        return this;
    }

    public MessageBuilder replyTo(long messageId) {
        json.putObject("message_reference").put("message_id", String.valueOf(messageId)).put("fail_if_not_exists", false);
        return this;
    }

    public MessageBuilder noMentions() {
        json.putObject("allowed_mentions").putArray("parse");
        return this;
    }

    public MessageBuilder safeMentions(boolean pingRepliedUser) {
        ObjectNode am = json.putObject("allowed_mentions");
        am.putArray("parse").add("users");
        am.put("replied_user", pingRepliedUser);
        return this;
    }

    public ObjectNode toJson() {
        ObjectNode out = json.deepCopy();
        if (flags != 0) out.put("flags", flags);
        return out;
    }

    public List<FileUpload> files() {
        return files;
    }

    public boolean hasFiles() {
        return !files.isEmpty();
    }
}
