package fr.traodev.event;

import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.Bot;
import fr.traodev.component.MessageBuilder;
import fr.traodev.component.Modal;
import fr.traodev.model.Interaction;
import fr.traodev.model.Member;
import fr.traodev.model.Message;
import fr.traodev.model.User;
import fr.traodev.rest.Route;
import fr.traodev.util.Json;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public abstract class InteractionEvent implements Event {
    private enum State {NONE, DEFERRED, REPLIED}

    protected final Bot bot;
    protected final Interaction interaction;
    private final AtomicReference<State> state = new AtomicReference<>(State.NONE);

    protected InteractionEvent(Bot bot, Interaction interaction) {
        this.bot = bot;
        this.interaction = interaction;
    }

    @Override
    public Bot bot() {
        return bot;
    }

    public Interaction interaction() {
        return interaction;
    }

    public User user() {
        return interaction.invoker();
    }

    public Member member() {
        return interaction.member();
    }

    public long guildId() {
        return interaction.guildId();
    }

    public long channelId() {
        return interaction.channelId();
    }

    public String locale() {
        return interaction.locale();
    }

    public boolean isAcknowledged() {
        return state.get() != State.NONE;
    }

    public CompletableFuture<Void> reply(String content) {
        return reply(MessageBuilder.of(content));
    }

    public CompletableFuture<Void> replyEphemeral(String content) {
        return reply(MessageBuilder.of(content).ephemeral());
    }

    public CompletableFuture<Void> reply(MessageBuilder m) {
        State s = state.get();
        if (s == State.NONE) {
            if (m.hasFiles()) {
                boolean eph = (m.toJson().path("flags").asInt() & MessageBuilder.EPHEMERAL) != 0;
                return defer(eph).thenCompose(v -> edit(m));
            }
            if (!state.compareAndSet(State.NONE, State.REPLIED)) return reply(m);
            return callback(4, m.toJson());
        }
        if (s == State.DEFERRED) return edit(m);
        return followUp(m).thenApply(x -> null);
    }

    public CompletableFuture<Void> defer() {
        return defer(false);
    }

    public CompletableFuture<Void> defer(boolean ephemeral) {
        if (!state.compareAndSet(State.NONE, State.DEFERRED)) return CompletableFuture.completedFuture(null);
        ObjectNode data = Json.object();
        if (ephemeral) data.put("flags", MessageBuilder.EPHEMERAL);
        return callback(5, data);
    }

    public CompletableFuture<Void> edit(MessageBuilder m) {
        if (state.get() == State.NONE) return reply(m);
        state.set(State.REPLIED);
        return bot.rest().request(Route.patch("/webhooks/{}/{}/messages/@original", interaction.applicationId(), interaction.token()),
            m.toJson(), m.files(), null).thenApply(n -> null);
    }

    public CompletableFuture<Void> edit(String content) {
        return edit(MessageBuilder.of(content));
    }

    public CompletableFuture<Message> followUp(MessageBuilder m) {
        return bot.rest().request(Route.post("/webhooks/{}/{}", interaction.applicationId(), interaction.token()).query("wait", true),
            m.toJson(), m.files(), null).thenApply(n -> Json.convert(n, Message.class));
    }

    public CompletableFuture<Message> followUp(String content) {
        return followUp(MessageBuilder.of(content));
    }

    public CompletableFuture<Void> deleteReply() {
        return bot.rest().request(Route.delete("/webhooks/{}/{}/messages/@original", interaction.applicationId(), interaction.token()))
            .thenApply(n -> null);
    }

    public CompletableFuture<Void> showModal(Modal modal) {
        if (!state.compareAndSet(State.NONE, State.REPLIED))
            throw new IllegalStateException("Interaction already acknowledged");
        return callback(9, modal.data());
    }

    protected CompletableFuture<Void> callback(int type, ObjectNode data) {
        ObjectNode body = Json.object().put("type", type);
        if (data != null) body.set("data", data);
        return bot.rest().request(Route.post("/interactions/{}/{}/callback", interaction.id(), interaction.token()), body)
            .thenApply(n -> null);
    }

    protected CompletableFuture<Void> ackWith(int type, ObjectNode data) {
        if (!state.compareAndSet(State.NONE, State.REPLIED))
            throw new IllegalStateException("Interaction already acknowledged");
        return callback(type, data);
    }
}
