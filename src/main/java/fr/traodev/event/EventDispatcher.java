
package fr.traodev.event;

import com.fasterxml.jackson.databind.JsonNode;
import fr.traodev.Bot;
import fr.traodev.model.*;
import fr.traodev.util.Json;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

public final class EventDispatcher {

    private record Decoder(Class<? extends Event> type, BiFunction<Bot, JsonNode, Event> factory) {
        private Decoder {
            Objects.requireNonNull(factory, "factory");
        }
    }

    private final Bot bot;
    private final EventBus bus;
    private final Map<String, Decoder> decoders;

    public EventDispatcher(Bot bot, EventBus bus) {
        this.bot = Objects.requireNonNull(bot, "bot");
        this.bus = Objects.requireNonNull(bus, "bus");

        Map<String, Decoder> registry = new HashMap<>();

        register(registry);

        this.decoders = Map.copyOf(registry);
    }

    private void register(Map<String, Decoder> registry) {
        add(registry, "GUILD_CREATE", GuildCreateEvent.class,
            (b, d) -> new GuildCreateEvent(b, Json.convert(d, Guild.class)));

        add(registry, "GUILD_UPDATE", GuildUpdateEvent.class,
            (b, d) -> new GuildUpdateEvent(b, Json.convert(d, Guild.class)));

        add(registry, "GUILD_DELETE", GuildDeleteEvent.class,
            (b, d) -> new GuildDeleteEvent(
                b,
                id(d, "id"),
                d.path("unavailable").asBoolean()
            ));

        add(registry, "GUILD_MEMBER_ADD", MemberJoinEvent.class,
            (b, d) -> new MemberJoinEvent(
                b,
                id(d, "guild_id"),
                Json.convert(d, Member.class)
            ));

        add(registry, "GUILD_MEMBER_UPDATE", MemberUpdateEvent.class,
            (b, d) -> new MemberUpdateEvent(
                b,
                id(d, "guild_id"),
                Json.convert(d, Member.class)
            ));

        add(registry, "GUILD_MEMBER_REMOVE", MemberLeaveEvent.class,
            (b, d) -> new MemberLeaveEvent(
                b,
                id(d, "guild_id"),
                Json.convert(required(d, "user"), User.class)
            ));

        add(registry, "MESSAGE_CREATE", MessageCreateEvent.class,
            (b, d) -> new MessageCreateEvent(b, Json.convert(d, Message.class)));

        add(registry, "MESSAGE_UPDATE", MessageUpdateEvent.class,
            (b, d) -> new MessageUpdateEvent(b, Json.convert(d, Message.class)));

        add(registry, "MESSAGE_DELETE", MessageDeleteEvent.class,
            (b, d) -> new MessageDeleteEvent(
                b,
                id(d, "id"),
                id(d, "channel_id"),
                id(d, "guild_id")
            ));

        add(registry, "MESSAGE_REACTION_ADD", ReactionAddEvent.class,
            (b, d) -> new ReactionAddEvent(
                b,
                id(d, "user_id"),
                id(d, "channel_id"),
                id(d, "message_id"),
                id(d, "guild_id"),
                Json.convert(required(d, "emoji"), Emoji.class),
                optional(d, "member", Member.class)
            ));

        add(registry, "MESSAGE_REACTION_REMOVE", ReactionRemoveEvent.class,
            (b, d) -> new ReactionRemoveEvent(
                b,
                id(d, "user_id"),
                id(d, "channel_id"),
                id(d, "message_id"),
                id(d, "guild_id"),
                Json.convert(required(d, "emoji"), Emoji.class)
            ));

        add(registry, "CHANNEL_CREATE", ChannelCreateEvent.class,
            (b, d) -> new ChannelCreateEvent(b, Json.convert(d, Channel.class)));

        add(registry, "CHANNEL_UPDATE", ChannelUpdateEvent.class,
            (b, d) -> new ChannelUpdateEvent(b, Json.convert(d, Channel.class)));

        add(registry, "CHANNEL_DELETE", ChannelDeleteEvent.class,
            (b, d) -> new ChannelDeleteEvent(b, Json.convert(d, Channel.class)));

        add(registry, "INTERACTION_CREATE", null,
            EventDispatcher::interaction);
    }

    private static void add(
        Map<String, Decoder> registry,
        String name,
        Class<? extends Event> type,
        BiFunction<Bot, JsonNode, Event> factory
    ) {
        Decoder previous = registry.putIfAbsent(
            name,
            new Decoder(type, factory)
        );

        if (previous != null) {
            throw new IllegalStateException("Duplicate event decoder: " + name);
        }
    }

    private static JsonNode required(JsonNode data, String field) {
        JsonNode node = data.get(field);

        if (node == null || node.isNull()) {
            throw new IllegalArgumentException(
                "Missing required event field: " + field
            );
        }

        return node;
    }

    private static <T> T optional(JsonNode data, String field, Class<T> type) {
        JsonNode node = data.get(field);

        if (node == null || node.isNull()) {
            return null;
        }

        return Json.convert(node, type);
    }

    private static long id(JsonNode data, String field) {
        JsonNode node = data.get(field);

        if (node == null || node.isNull()) {
            return 0L;
        }

        return Long.parseUnsignedLong(node.asText());
    }

    private static Event interaction(Bot bot, JsonNode data) {
        Interaction interaction = Json.convert(data, Interaction.class);

        if (interaction == null) {
            return null;
        }

        InteractionData interactionData = interaction.data();

        return switch (interaction.type()) {
            case Interaction.APPLICATION_COMMAND -> {
                if (interactionData == null) {
                    yield null;
                }

                yield interactionData.type() == null
                    || interactionData.type() == 1
                    ? new SlashCommandEvent(bot, interaction)
                    : new ContextMenuEvent(bot, interaction);
            }

            case Interaction.MESSAGE_COMPONENT -> {
                if (interactionData == null) {
                    yield null;
                }

                yield interactionData.componentType() != null
                    && interactionData.componentType() == 2
                    ? new ButtonEvent(bot, interaction)
                    : new SelectMenuEvent(bot, interaction);
            }

            case Interaction.AUTOCOMPLETE -> {
                if (interactionData == null) {
                    yield null;
                }

                String path = interactionData.name();
                OptionData focused = findFocused(interactionData.options());

                yield new AutocompleteEvent(
                    bot,
                    interaction,
                    path,
                    focused
                );
            }

            case Interaction.MODAL_SUBMIT -> new ModalSubmitEvent(bot, interaction);

            default -> null;
        };
    }

    private static OptionData findFocused(Iterable<OptionData> options) {
        if (options == null) {
            return null;
        }

        for (OptionData option : options) {
            if (option.focused()) {
                return option;
            }

            OptionData nested = findFocused(option.options());

            if (nested != null) {
                return nested;
            }
        }

        return null;
    }

    public void dispatch(int shardId, String name, JsonNode data) {
        Decoder decoder = decoders.get(name);

        if (decoder != null &&
            (decoder.type() == null || bus.wants(decoder.type()))) {

            Event event = decoder.factory().apply(bot, data);

            if (event != null) {
                bus.publish(event);
            }
        }

        if (bus.wantsRaw(name)) {
            bus.publishRaw(new RawEvent(bot, shardId, name, data));
        }
    }
}
