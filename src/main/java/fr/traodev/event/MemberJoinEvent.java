package fr.traodev.event;

import fr.traodev.Bot;
import fr.traodev.model.Member;

public record MemberJoinEvent(Bot bot, long guildId, Member member) implements Event {
}
