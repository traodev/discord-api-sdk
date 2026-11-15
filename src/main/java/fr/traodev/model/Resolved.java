package fr.traodev.model;

import java.util.Map;

public record Resolved(Map<Long, User> users, Map<Long, Member> members, Map<Long, Role> roles,
                       Map<Long, Channel> channels, Map<Long, Message> messages, Map<Long, Attachment> attachments) {
    public Resolved {
        users = users == null ? Map.of() : users;
        members = members == null ? Map.of() : members;
        roles = roles == null ? Map.of() : roles;
        channels = channels == null ? Map.of() : channels;
        messages = messages == null ? Map.of() : messages;
        attachments = attachments == null ? Map.of() : attachments;
    }
}
