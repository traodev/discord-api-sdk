package fr.traodev;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import fr.traodev.command.Option;
import fr.traodev.command.SlashCommand;
import fr.traodev.component.Modal;
import fr.traodev.util.Json;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InteractionFlowTest {
    record Call(String method, String path, JsonNode body) {}

    static HttpServer server;
    static final BlockingQueue<Call> calls = new LinkedBlockingQueue<>();
    static Bot bot;

    @BeforeAll
    static void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            byte[] in = ex.getRequestBody().readAllBytes();
            String path = ex.getRequestURI().toString();
            calls.add(new Call(ex.getRequestMethod(), path, in.length == 0 ? null : Json.parse(in)));
            boolean callback = path.endsWith("/callback");
            byte[] out = callback ? new byte[0] : "{\"id\":\"1\",\"channel_id\":\"2\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(callback ? 204 : 200, callback ? -1 : out.length);
            if (!callback) ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        System.setProperty("jdisc.api.base", "http://127.0.0.1:" + server.getAddress().getPort());
        bot = Bot.builder("fake.token").shutdownHook(false).build();

        bot.commands().register(
                SlashCommand.of("greet", "Greets", e -> {
                    e.reply("Hi " + e.string("who", "?"));
                    e.reply("second message");
                }, Option.string("who", "Name")),
                SlashCommand.of("slow", "Slow", e -> { e.defer(); e.reply("done"); }),
                SlashCommand.of("modal", "Modal", e -> e.showModal(Modal.create("m1", "Title").shortText("name", "Name"))));
        bot.onButton("vote:*", e -> e.updateMessage("thanks " + e.customId()));
        bot.onModal("m1", e -> e.reply("got " + e.value("name")));
    }

    @AfterAll
    static void teardown() { server.stop(0); bot.close(); }

    private static void interaction(String json) { bot.handleDispatch(0, "INTERACTION_CREATE", Json.parse(json)); }

    private static Map<String, Call> take(int n) throws Exception {
        Map<String, Call> m = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            Call c = calls.poll(5, TimeUnit.SECONDS);
            assertNotNull(c, "expected " + n + " REST calls, got " + i);
            m.put(c.method() + " " + c.path(), c);
        }
        return m;
    }

    @Test
    void slashReplyThenFollowUp() throws Exception {
        calls.clear();
        interaction("""
            {"id":"100","application_id":"200","type":2,"token":"tokA","version":1,"guild_id":"1","channel_id":"2",
             "member":{"user":{"id":"3","username":"u"}},
             "data":{"id":"9","name":"greet","type":1,"options":[{"name":"who","type":3,"value":"Bob"}]}}""");
        Map<String, Call> m = take(2);
        Call cb = m.get("POST /interactions/100/tokA/callback");
        assertNotNull(cb, m.keySet().toString());
        assertEquals(4, cb.body().get("type").asInt());
        assertEquals("Hi Bob", cb.body().get("data").get("content").asText());
        Call fu = m.get("POST /webhooks/200/tokA?wait=true");
        assertNotNull(fu, m.keySet().toString());
        assertEquals("second message", fu.body().get("content").asText());
    }

    @Test
    void deferThenReplyEditsOriginal() throws Exception {
        calls.clear();
        interaction("""
            {"id":"101","application_id":"200","type":2,"token":"tokB","version":1,
             "user":{"id":"3","username":"u"},"data":{"id":"9","name":"slow","type":1}}""");
        Map<String, Call> m = take(2);
        assertEquals(5, m.get("POST /interactions/101/tokB/callback").body().get("type").asInt());
        assertEquals("done", m.get("PATCH /webhooks/200/tokB/messages/@original").body().get("content").asText());
    }

    @Test
    void buttonPrefixRoutingUpdatesMessage() throws Exception {
        calls.clear();
        interaction("""
            {"id":"102","application_id":"200","type":3,"token":"tokC","version":1,
             "user":{"id":"3","username":"u"},"message":{"id":"50","channel_id":"2"},
             "data":{"custom_id":"vote:yes","component_type":2}}""");
        Call c = take(1).values().iterator().next();
        assertEquals(7, c.body().get("type").asInt());
        assertEquals("thanks vote:yes", c.body().get("data").get("content").asText());
    }

    @Test
    void modalShownThenSubmitted() throws Exception {
        calls.clear();
        interaction("""
            {"id":"103","application_id":"200","type":2,"token":"tokD","version":1,
             "user":{"id":"3","username":"u"},"data":{"id":"9","name":"modal","type":1}}""");
        Call c = take(1).values().iterator().next();
        assertEquals(9, c.body().get("type").asInt());
        assertEquals(18, c.body().get("data").get("components").get(0).get("type").asInt());

        calls.clear();
        interaction("""
            {"id":"104","application_id":"200","type":5,"token":"tokE","version":1,"user":{"id":"3","username":"u"},
             "data":{"custom_id":"m1","components":[{"type":18,"id":1,"component":{"type":4,"id":2,"custom_id":"name","value":"Zed"}}]}}""");
        Call r = take(1).values().iterator().next();
        assertEquals("got Zed", r.body().get("data").get("content").asText());
    }
}
