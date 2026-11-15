package fr.traodev.event;

import com.fasterxml.jackson.databind.JsonNode;
import fr.traodev.Bot;
import fr.traodev.model.Attachment;
import fr.traodev.model.Channel;
import fr.traodev.model.Interaction;
import fr.traodev.model.Member;
import fr.traodev.model.OptionData;
import fr.traodev.model.Role;
import fr.traodev.model.User;

import java.util.List;

public final class SlashCommandEvent extends InteractionEvent {
    private final String path;
    private final String subcommandGroup;
    private final String subcommand;
    private final List<OptionData> options;

    public SlashCommandEvent(Bot bot, Interaction i) {
        super(bot, i);
        String name = i.data().name();
        String group = null, sub = null;
        List<OptionData> opts = i.data().options();
        if (!opts.isEmpty() && opts.get(0).type() == 2) {
            group = opts.get(0).name();
            opts = opts.get(0).options();
        }
        if (!opts.isEmpty() && opts.get(0).type() == 1) {
            sub = opts.get(0).name();
            opts = opts.get(0).options();
        }
        this.subcommandGroup = group;
        this.subcommand = sub;
        this.options = opts;
        this.path = name + (group != null ? " " + group : "") + (sub != null ? " " + sub : "");
    }

    public String name() {
        return interaction.data().name();
    }

    public String subcommandGroup() {
        return subcommandGroup;
    }

    public String subcommand() {
        return subcommand;
    }

    public String path() {
        return path;
    }

    public List<OptionData> options() {
        return options;
    }

    public boolean has(String name) {
        return raw(name) != null;
    }

    private JsonNode raw(String name) {
        for (OptionData o : options) if (o.name().equals(name)) return o.value();
        return null;
    }

    public String string(String name) {
        JsonNode v = raw(name);
        return v == null ? null : v.asText();
    }

    public String string(String name, String def) {
        String s = string(name);
        return s == null ? def : s;
    }

    public Long integer(String name) {
        JsonNode v = raw(name);
        return v == null ? null : v.asLong();
    }

    public long integer(String name, long def) {
        Long l = integer(name);
        return l == null ? def : l;
    }

    public Double number(String name) {
        JsonNode v = raw(name);
        return v == null ? null : v.asDouble();
    }

    public Boolean bool(String name) {
        JsonNode v = raw(name);
        return v == null ? null : v.asBoolean();
    }

    public boolean bool(String name, boolean def) {
        Boolean b = bool(name);
        return b == null ? def : b;
    }

    public long id(String name) {
        JsonNode v = raw(name);
        return v == null ? 0 : Long.parseUnsignedLong(v.asText());
    }

    public User user(String name) {
        return interaction.data().resolved().users().get(id(name));
    }

    public Member member(String name) {
        return interaction.data().resolved().members().get(id(name));
    }

    public Role role(String name) {
        return interaction.data().resolved().roles().get(id(name));
    }

    public Channel channel(String name) {
        return interaction.data().resolved().channels().get(id(name));
    }

    public Attachment attachment(String name) {
        return interaction.data().resolved().attachments().get(id(name));
    }
}
