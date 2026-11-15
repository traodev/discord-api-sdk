package fr.traodev.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public record Message(long id, long channelId, long guildId, User author, Member member, String content,
                      String timestamp, String editedTimestamp, boolean tts, boolean mentionEveryone,
                      List<User> mentions, List<Long> mentionRoles, List<Attachment> attachments, List<Embed> embeds,
                      boolean pinned, int type, int flags, Message referencedMessage, MessageReference messageReference,
                      long webhookId, JsonNode components) {
    public Message {
        content = content == null ? "" : content;
        mentions = mentions == null ? List.of() : mentions;
        mentionRoles = mentionRoles == null ? List.of() : mentionRoles;
        attachments = attachments == null ? List.of() : attachments;
        embeds = embeds == null ? List.of() : embeds;
    }

    public record MessageReference(long messageId, long channelId, long guildId) {
    }

    public boolean isFromBot() {
        return author != null && author.bot();
    }

    public boolean isWebhook() {
        return webhookId != 0;
    }

    public boolean isDm() {
        return guildId == 0;
    }

    public boolean mentions(long userId) {
        return mentions.stream().anyMatch(u -> u.id() == userId);
    }

    public Instant createdAt() {
        return OffsetDateTime.parse(timestamp).toInstant();
    }

    public String jumpUrl() {
        return "https://discord.com/channels/" + (guildId == 0 ? "@me" : guildId) + "/" + channelId + "/" + id;
    }
}
