package fr.traodev;

import com.fasterxml.jackson.databind.JsonNode;
import fr.traodev.command.Option;
import fr.traodev.command.Router;
import fr.traodev.command.SlashCommand;
import fr.traodev.component.Component;
import fr.traodev.component.EmbedBuilder;
import fr.traodev.component.MessageBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandsTest {
    @Test
    void slashCommandJsonWithSubcommandsAndGroups() {
        SlashCommand c = SlashCommand.of("economy", "Money")
                .sub("balance", "Show", e -> {}, Option.user("user", "Whose?"))
                .group("admin", "Admin", g -> g.sub("give", "Give", e -> {}, Option.integer("amount", "How many").required().min(1)))
                .requirePermissions(Permission.MANAGE_GUILD).guildOnly();
        JsonNode j = c.toJson();
        assertEquals(1, j.get("type").asInt());
        assertEquals(String.valueOf(Permission.MANAGE_GUILD.bit()), j.get("default_member_permissions").asText());
        assertEquals(2, j.get("options").size());
        assertEquals(1, j.get("options").get(0).get("type").asInt());
        assertEquals(2, j.get("options").get(1).get("type").asInt());
        assertEquals("give", j.get("options").get(1).get("options").get(0).get("name").asText());
        assertEquals(1, j.get("options").get(1).get("options").get(0).get("options").get(0).get("min_value").asInt());
    }

    @Test
    void routerExactAndPrefix() {
        Router<String> r = new Router<>();
        List<String> hits = new ArrayList<>();
        r.register("ok", e -> hits.add("exact:" + e));
        r.register("vote:*", e -> hits.add("prefix:" + e));
        assertTrue(r.route("ok", "a"));
        assertTrue(r.route("vote:42", "b"));
        assertFalse(r.route("nope", "c"));
        assertEquals(List.of("exact:a", "prefix:b"), hits);
    }

    @Test
    void messageBuilderPayload() {
        JsonNode j = MessageBuilder.of("hi")
                .embed(new EmbedBuilder().title("T").field("a", "b", true).color(0xFF0000))
                .row(Component.primary("id", "Click"), Component.link("https://x.y", "Docs"))
                .ephemeral().replyTo(5).toJson();
        assertEquals("hi", j.get("content").asText());
        assertEquals(64, j.get("flags").asInt());
        assertEquals("T", j.get("embeds").get(0).get("title").asText());
        assertEquals(2, j.get("components").get(0).get("components").size());
        assertEquals("5", j.get("message_reference").get("message_id").asText());
    }

    @Test
    void componentsV2SetsFlag() {
        JsonNode j = MessageBuilder.create().componentsV2(Component.container(0x5865F2, Component.text("# Hello"), Component.separator())).toJson();
        assertEquals(1 << 15, j.get("flags").asInt());
        assertEquals(17, j.get("components").get(0).get("type").asInt());
    }
}
