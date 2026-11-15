import fr.traodev.*;
import fr.traodev.command.Option;
import fr.traodev.command.SlashCommand;
import fr.traodev.component.Component;
import fr.traodev.component.EmbedBuilder;
import fr.traodev.component.MessageBuilder;
import fr.traodev.component.Modal;
import fr.traodev.event.MessageCreateEvent;
import fr.traodev.event.ReadyEvent;

import java.util.List;

public class ExampleBot {
    private static final List<String> COLORS = List.of("red", "green", "blue", "orange", "purple", "pink");

    public static void main(String[] args) {
        Bot bot = Bot.builder(System.getenv("DISCORD_TOKEN"))
                .addIntents(Intent.GUILD_MESSAGES, Intent.MESSAGE_CONTENT)
                .presence(Presence.online(Activity.watching("slash commands")))
                .build();

        bot.on(ReadyEvent.class, e -> System.out.println("Ready as " + e.self().tag()));

        bot.commands().register(
                SlashCommand.of("ping", "Show the gateway latency",
                        e -> e.reply("Pong! " + e.bot().latencyMs() + " ms")),

                SlashCommand.of("say", "Repeat a text",
                        e -> e.reply(e.string("text")),
                        Option.string("text", "What to say").required().maxLength(200)),

                SlashCommand.of("userinfo", "About a user", e -> {
                    var user = e.user("user") != null ? e.user("user") : e.user();
                    e.reply(MessageBuilder.of(new EmbedBuilder()
                            .title(user.displayName())
                            .thumbnail(user.avatarUrl())
                            .field("ID", String.valueOf(user.id()), true)
                            .field("Bot", String.valueOf(user.bot()), true)
                            .color(0x5865F2).now()));
                }, Option.user("user", "Who? (default: you)")),

                SlashCommand.of("color", "Pick a color", e -> e.reply("You picked " + e.string("name")),
                        Option.string("name", "Color").required()
                                .autocomplete(ac -> ac.suggest(COLORS.stream()
                                        .filter(c -> c.startsWith(ac.focusedValue().toLowerCase())).toList()))),

                SlashCommand.of("poll", "Start a tiny poll", e -> e.reply(MessageBuilder.of("Pizza or pasta?")
                        .row(Component.success("vote:pizza", "Pizza"),
                             Component.primary("vote:pasta", "Pasta")))),

                SlashCommand.of("feedback", "Send feedback",
                        e -> e.showModal(Modal.create("feedback", "Feedback").paragraph("text", "Your feedback"))),

                SlashCommand.of("slow", "Long task: defer first, answer later", e -> {
                    e.defer();
                    sleep(5000);
                    e.reply("Done after 5 seconds!");
                })
        );

        bot.onButton("vote:*", e -> e.updateMessage("You voted **" + e.customId().substring(5) + "**"));
        bot.onModal("feedback", e -> e.replyEphemeral("Thanks! You wrote: " + e.value("text")));

        bot.on(MessageCreateEvent.class, e -> {
            if (!e.author().bot() && e.content().equalsIgnoreCase("!ping")) e.reply("pong");
        });

        bot.start().awaitShutdown();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
