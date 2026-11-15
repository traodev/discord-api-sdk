package fr.traodev.model;

public record ApplicationCommand(long id, long applicationId, int type, String name, String description, long guildId,
                                 String version) {
    public String mention() {
        return "</" + name + ":" + id + ">";
    }
}
