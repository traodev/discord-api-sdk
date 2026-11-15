package fr.traodev.model;

public record Role(long id, String name, int color, boolean hoist, int position, String permissions,
                   boolean managed, boolean mentionable, String icon, String unicodeEmoji) {
    public String mention() {
        return "<@&" + id + ">";
    }
}
