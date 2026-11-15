package fr.traodev.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record InteractionData(long id, String name, Integer type, Resolved resolved, List<OptionData> options,
                              long guildId, long targetId, String customId, Integer componentType,
                              List<String> values, JsonNode components) {
    public InteractionData {
        options = options == null ? List.of() : options;
        values = values == null ? List.of() : values;
        resolved = resolved == null ? new Resolved(null, null, null, null, null, null) : resolved;
    }
}
