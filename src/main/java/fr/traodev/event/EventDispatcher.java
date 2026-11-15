package fr.traodev.event;

import com.fasterxml.jackson.databind.JsonNode;
import fr.traodev.Bot;
import fr.traodev.model.*;
import fr.traodev.util.Json;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public final class EventDispatcher {
    private record Decoder(Class<? extends Event> type, BiFunction<Bot, JsonNode, Event> fn) {
    }

    private final Bot bot;
    private final EventBus bus;
    private final Map<String, Decoder> decoders = new HashMap<>();

    public EventDispatcher(Bot bot, EventBus bus) {
        this.bot = bot;
        this.bus = bus;
        add("GUILD_CREATE", GuildCreateEvent.class, (b, d) -> new GuildCreateEvent(b, Json.convert(d, Guild.class)));
        add("GUILD_UPDATE", GuildUpdateEvent.class, (b, d) -> new GuildUpdateEvent(b, Json.convert(d, Guild.class)));
        add("GUILD_DELETE", GuildDeleteEvent.class, (b, d) -> new GuildDeleteEvent(b, id(d, "id"), d.path("unavailable").asBoolean()));
        add("GUILD_MEMBER_ADD", MemberJoinEvent.class, (b, d) -> new MemberJoinEvent(b, id(d, "guild_id"), Json.convert(d, Member.class)));
        add("GUILD_MEMBER_UPDATE", MemberUpdateEvent.class, (b, d) -> new MemberUpdateEvent(b, id(d, "guild_id"), Json.convert(d, Member.class)));
        add("GUILD_MEMBER_REMOVE", MemberLeaveEvent.class, (b, d) -> new MemberLeaveEvent(b, id(d, "guild_id"), Json.convert(d.get("user"), User.class)));
        add("MESSAGE_CREATE", MessageCreateEvent.class, (b, d) -> new MessageCreateEvent(b, Json.convert(d, Message.class)));
        add("MESSAGE_UPDATE", MessageUpdateEvent.class, (b, d) -> new MessageUpdateEvent(b, Json.convert(d, Message.class)));
        add("MESSAGE_DELETE", MessageDeleteEvent.class, (b, d) -> new MessageDeleteEvent(b, id(d, "id"), id(d, "channel_id"), id(d, "guild_id")));
        add("MESSAGE_REACTION_ADD", ReactionAddEvent.class, (b, d) -> new ReactionAddEvent(b, id(d, "user_id"), id(d, "channel_id"),
            id(d, "message_id"), id(d, "guild_id"), Json.convert(d.get("emoji"), Emoji.class), Json.convert(d.get("member"), Member.class)));
        add("MESSAGE_REACTION_REMOVE", ReactionRemoveEvent.class, (b, d) -> new ReactionRemoveEvent(b, id(d, "user_id"), id(d, "channel_id"),
            id(d, "message_id"), id(d, "guild_id"), Json.convert(d.get("emoji"), Emoji.class)));
        add("CHANNEL_CREATE", ChannelCreateEvent.class, (b, d) -> new ChannelCreateEvent(b, Json.convert(d, Channel.class)));
        add("CHANNEL_UPDATE", ChannelUpdateEvent.class, (b, d) -> new ChannelUpdateEvent(b, Json.convert(d, Channel.class)));
        add("CHANNEL_DELETE", ChannelDeleteEvent.class, (b, d) -> new ChannelDeleteEvent(b, Json.convert(d, Channel.class)));
        add("INTERACTION_CREATE", null, (b, d) -> interaction(b, Json.convert(d, Interaction.class)));
    }

    private void add(String name, Class<? extends Event> type, BiFunction<Bot, JsonNode, Event> fn) {
        decoders.put(name, new Decoder(type, fn));
    }

    private static long id(JsonNode d, String field) {
        JsonNode n = d.get(field);
        return n == null || n.isNull() ? 0 : Long.parseUnsignedLong(n.asText());
    }

    private static Event interaction(Bot b, Interaction i) {
        InteractionData d = i.data();
        return switch (i.type()) {
            case Interaction.APPLICATION_COMMAND ->
                d.type() == null || d.type() == 1 ? new SlashCommandEvent(b, i) : new ContextMenuEvent(b, i);
            case Interaction.MESSAGE_COMPONENT ->
                d.componentType() != null && d.componentType() == 2 ? new ButtonEvent(b, i) : new SelectMenuEvent(b, i);
            case Interaction.AUTOCOMPLETE -> {
                OptionData focused = null;
                String path = d.name();
                var opts = d.options();
                if (!opts.isEmpty() && opts.get(0).type() == 2) {
                    path += " " + opts.get(0).name();
                    opts = opts.get(0).options();
                }
                if (!opts.isEmpty() && opts.get(0).type() == 1) {
                    path += " " + opts.get(0).name();
                    opts = opts.get(0).options();
                }
                for (OptionData o : opts) if (o.focused()) focused = o;
                yield new AutocompleteEvent(b, i, path, focused);
            }
            case Interaction.MODAL_SUBMIT -> new ModalSubmitEvent(b, i);
            default -> null;
        };
    }

    public void dispatch(int shardId, String name, JsonNode data) {
        Decoder dec = decoders.get(name);
        if (dec != null && (dec.type() == null || bus.wants(dec.type()))) {
            Event e = dec.fn().apply(bot, data);
            if (e != null) bus.publish(e);
        }
        if (bus.wantsRaw(name)) bus.publishRaw(new RawEvent(bot, shardId, name, data));
    }
}
