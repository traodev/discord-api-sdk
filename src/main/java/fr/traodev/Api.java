package fr.traodev;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.component.MessageBuilder;
import fr.traodev.model.*;
import fr.traodev.rest.RestClient;
import fr.traodev.rest.Route;
import fr.traodev.util.Json;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class Api {
    private final RestClient rest;

    Api(RestClient rest) {
        this.rest = rest;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static <T> CompletableFuture<Void> none(CompletableFuture<T> f) {
        return f.thenApply(x -> null);
    }

    public CompletableFuture<User> self() {
        return rest.request(Route.get("/users/@me"), null, User.class);
    }

    public CompletableFuture<User> getUser(long id) {
        return rest.request(Route.get("/users/{}", id), null, User.class);
    }

    public CompletableFuture<Channel> createDm(long userId) {
        return rest.request(Route.post("/users/@me/channels"), Json.object().put("recipient_id", String.valueOf(userId)), Channel.class);
    }

    public CompletableFuture<Message> sendDm(long userId, MessageBuilder m) {
        return createDm(userId).thenCompose(c -> sendMessage(c.id(), m));
    }

    public CompletableFuture<Message> sendDm(long userId, String content) {
        return sendDm(userId, MessageBuilder.of(content));
    }

    public CompletableFuture<Message> sendMessage(long channelId, String content) {
        return sendMessage(channelId, MessageBuilder.of(content));
    }

    public CompletableFuture<Message> sendMessage(long channelId, MessageBuilder m) {
        return rest.request(Route.post("/channels/{}/messages", channelId), m.toJson(), m.files(), null)
            .thenApply(n -> Json.convert(n, Message.class));
    }

    public CompletableFuture<Message> editMessage(long channelId, long messageId, MessageBuilder m) {
        return rest.request(Route.patch("/channels/{}/messages/{}", channelId, messageId), m.toJson(), m.files(), null)
            .thenApply(n -> Json.convert(n, Message.class));
    }

    public CompletableFuture<Void> deleteMessage(long channelId, long messageId) {
        return deleteMessage(channelId, messageId, null);
    }

    public CompletableFuture<Void> deleteMessage(long channelId, long messageId, String reason) {
        return none(rest.request(Route.delete("/channels/{}/messages/{}", channelId, messageId), null, null, reason));
    }

    public CompletableFuture<Message> getMessage(long channelId, long messageId) {
        return rest.request(Route.get("/channels/{}/messages/{}", channelId, messageId), null, Message.class);
    }

    public CompletableFuture<List<Message>> getMessages(long channelId, int limit, long before) {
        Route r = Route.get("/channels/{}/messages", channelId).query("limit", limit);
        if (before != 0) r = r.query("before", before);
        return rest.requestList(r, null, Message.class);
    }

    public CompletableFuture<Void> bulkDelete(long channelId, Collection<Long> messageIds) {
        ArrayNode ids = Json.array();
        messageIds.forEach(id -> ids.add(String.valueOf(id)));
        ObjectNode body = Json.object();
        body.set("messages", ids);
        return none(rest.request(Route.post("/channels/{}/messages/bulk-delete", channelId), body));
    }

    public CompletableFuture<Void> addReaction(long channelId, long messageId, String emoji) {
        return none(rest.request(Route.put("/channels/{}/messages/{}/reactions/{}/@me", channelId, messageId, enc(emoji))));
    }

    public CompletableFuture<Void> removeOwnReaction(long channelId, long messageId, String emoji) {
        return none(rest.request(Route.delete("/channels/{}/messages/{}/reactions/{}/@me", channelId, messageId, enc(emoji))));
    }

    public CompletableFuture<Void> pin(long channelId, long messageId) {
        return none(rest.request(Route.put("/channels/{}/pins/{}", channelId, messageId)));
    }

    public CompletableFuture<Void> unpin(long channelId, long messageId) {
        return none(rest.request(Route.delete("/channels/{}/pins/{}", channelId, messageId)));
    }

    public CompletableFuture<Void> typing(long channelId) {
        return none(rest.request(Route.post("/channels/{}/typing", channelId)));
    }

    public CompletableFuture<Channel> getChannel(long id) {
        return rest.request(Route.get("/channels/{}", id), null, Channel.class);
    }

    public CompletableFuture<Void> deleteChannel(long id, String reason) {
        return none(rest.request(Route.delete("/channels/{}", id), null, null, reason));
    }

    public CompletableFuture<Guild> getGuild(long id) {
        return rest.request(Route.get("/guilds/{}", id).query("with_counts", true), null, Guild.class);
    }

    public CompletableFuture<List<Channel>> getChannels(long guildId) {
        return rest.requestList(Route.get("/guilds/{}/channels", guildId), null, Channel.class);
    }

    public CompletableFuture<List<Role>> getRoles(long guildId) {
        return rest.requestList(Route.get("/guilds/{}/roles", guildId), null, Role.class);
    }

    public CompletableFuture<Member> getMember(long guildId, long userId) {
        return rest.request(Route.get("/guilds/{}/members/{}", guildId, userId), null, Member.class);
    }

    public CompletableFuture<List<Member>> listMembers(long guildId, int limit, long after) {
        Route r = Route.get("/guilds/{}/members", guildId).query("limit", limit);
        if (after != 0) r = r.query("after", after);
        return rest.requestList(r, null, Member.class);
    }

    public CompletableFuture<Void> addRole(long guildId, long userId, long roleId, String reason) {
        return none(rest.request(Route.put("/guilds/{}/members/{}/roles/{}", guildId, userId, roleId), null, null, reason));
    }

    public CompletableFuture<Void> removeRole(long guildId, long userId, long roleId, String reason) {
        return none(rest.request(Route.delete("/guilds/{}/members/{}/roles/{}", guildId, userId, roleId), null, null, reason));
    }

    public CompletableFuture<Void> kick(long guildId, long userId, String reason) {
        return none(rest.request(Route.delete("/guilds/{}/members/{}", guildId, userId), null, null, reason));
    }

    public CompletableFuture<Void> ban(long guildId, long userId, int deleteMessageSeconds, String reason) {
        return none(rest.request(Route.put("/guilds/{}/bans/{}", guildId, userId),
            Json.object().put("delete_message_seconds", deleteMessageSeconds), null, reason));
    }

    public CompletableFuture<Void> unban(long guildId, long userId, String reason) {
        return none(rest.request(Route.delete("/guilds/{}/bans/{}", guildId, userId), null, null, reason));
    }

    public CompletableFuture<Void> timeout(long guildId, long userId, Instant until, String reason) {
        ObjectNode body = Json.object();
        if (until == null) body.putNull("communication_disabled_until");
        else body.put("communication_disabled_until", until.toString());
        return none(rest.request(Route.patch("/guilds/{}/members/{}", guildId, userId), body, null, reason));
    }

    public CompletableFuture<List<ApplicationCommand>> getGlobalCommands(long applicationId) {
        return rest.requestList(Route.get("/applications/{}/commands", applicationId), null, ApplicationCommand.class);
    }

    public CompletableFuture<Void> deleteGlobalCommand(long applicationId, long commandId) {
        return none(rest.request(Route.delete("/applications/{}/commands/{}", applicationId, commandId)));
    }
}
