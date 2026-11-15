package fr.traodev.model;

public record Emoji(long id, String name, boolean animated) {
    public String asReaction() {
        return id == 0 ? name : name + ":" + id;
    }

    public String mention() {
        return id == 0 ? name : "<" + (animated ? "a" : "") + ":" + name + ":" + id + ">";
    }
}
