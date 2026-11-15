package fr.traodev.model;

import java.util.List;

public record Guild(long id, String name, String icon, long ownerId, String description, Integer memberCount,
                    boolean unavailable, boolean large, List<Role> roles, List<Emoji> emojis, List<Channel> channels,
                    List<String> features, String preferredLocale, int premiumTier) {
    public Guild {
        roles = roles == null ? List.of() : roles;
        emojis = emojis == null ? List.of() : emojis;
        channels = channels == null ? List.of() : channels;
        features = features == null ? List.of() : features;
    }

    public String iconUrl() {
        return icon == null ? null : "https://cdn.discordapp.com/icons/" + id + "/" + icon + (icon.startsWith("a_") ? ".gif" : ".png");
    }
}
