package fr.traodev;

import com.fasterxml.jackson.databind.JsonNode;
import fr.traodev.event.*;
import fr.traodev.model.*;
import fr.traodev.util.Json;
import fr.traodev.util.Snowflake;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ModelsAndDispatchTest {
    static final String MESSAGE = """
        {"id":"1100000000000000001","channel_id":"222","guild_id":"333","content":"!ping",
         "timestamp":"2026-01-01T10:00:00.123000+00:00","tts":false,"pinned":false,"type":0,"flags":0,
         "author":{"id":"444","username":"alice","discriminator":"0","global_name":"Alice","avatar":"a_abc","bot":false},
         "member":{"nick":"Al","roles":["5","6"],"joined_at":"2025-01-01T00:00:00+00:00","permissions":"8"},
         "mentions":[{"id":"777","username":"bot","discriminator":"0","bot":true}],
         "attachments":[],"embeds":[],"unknown_future_field":{"x":1}}
        """;

    @Test
    void messageRecordParsesSnowflakeStringsAndIgnoresUnknownFields() {
        Message m = Json.convert(Json.parse(MESSAGE), Message.class);
        assertEquals(1100000000000000001L, m.id());
        assertEquals(333L, m.guildId());
        assertEquals("Alice", m.author().displayName());
        assertTrue(m.author().avatarUrl().endsWith(".gif?size=256"));
        assertEquals("Al", m.member().displayName());
        assertTrue(m.member().hasRole(6));
        assertTrue(m.member().hasPermission(Permission.KICK_MEMBERS));
        assertTrue(m.mentions(777));
        assertTrue(m.createdAt().toString().startsWith("2026-01-01T10:00:00"));
    }

    @Test
    void snowflakeHelpers() {
        assertEquals(1420070400000L, Snowflake.timestampMillis(0));
        assertEquals(123456789L, Snowflake.parse("<@!123456789>"));
        assertEquals(0, Snowflake.shardFor(1 << 22, 1));
    }

    @Test
    void dispatchOnlyDecodesWhenListened() throws Exception {
        Bot bot = Bot.builder("fake.token").shutdownHook(false).build();
        CompletableFuture<MessageCreateEvent> got = new CompletableFuture<>();
        bot.on(MessageCreateEvent.class, got::complete);

        JsonNode d = Json.parse(MESSAGE);
        bot.handleDispatch(0, "MESSAGE_CREATE", d);
        MessageCreateEvent e = got.get(5, TimeUnit.SECONDS);
        assertEquals("!ping", e.content());
        assertFalse(bot.events().wants(ReactionAddEvent.class), "no listener => no decoding");
        assertTrue(bot.events().wants(MessageCreateEvent.class));

        bot.handleDispatch(0, "GUILD_CREATE", Json.parse("{\"id\":\"999\",\"name\":\"g\"}"));
        assertTrue(bot.guildIds().contains(999L));
        bot.handleDispatch(0, "GUILD_DELETE", Json.parse("{\"id\":\"999\"}"));
        assertFalse(bot.guildIds().contains(999L));
        bot.close();
    }

    @Test
    void rawEventsReachRawListeners() throws Exception {
        Bot bot = Bot.builder("fake.token").shutdownHook(false).build();
        CompletableFuture<RawEvent> got = new CompletableFuture<>();
        bot.onRaw("TYPING_START", got::complete);
        bot.handleDispatch(0, "TYPING_START", Json.parse("{\"user_id\":\"1\"}"));
        assertEquals("1", got.get(5, TimeUnit.SECONDS).data().path("user_id").asText());
        bot.close();
    }
}
