package fr.traodev.model;

public record User(long id, String username, String discriminator, String globalName, String avatar,
                   boolean bot, boolean system, String banner, Integer accentColor) {
    public String displayName() {
        return globalName != null ? globalName : username;
    }

    public String mention() {
        return "<@" + id + ">";
    }

    public String tag() {
        return discriminator == null || discriminator.equals("0") ? username : username + "#" + discriminator;
    }

    public String avatarUrl() {
        return avatarUrl(256);
    }

    public String avatarUrl(int size) {
        if (avatar == null) {
            long idx = "0".equals(discriminator) || discriminator == null ? (id >>> 22) % 6 : Long.parseLong(discriminator) % 5;
            return "https://cdn.discordapp.com/embed/avatars/" + idx + ".png";
        }
        return "https://cdn.discordapp.com/avatars/" + id + "/" + avatar + (avatar.startsWith("a_") ? ".gif" : ".png") + "?size=" + size;
    }
}
