package fr.traodev.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record OptionData(String name, int type, JsonNode value, List<OptionData> options, boolean focused) {
    public OptionData {
        options = options == null ? List.of() : options;
    }
}
