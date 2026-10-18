package com.uco.ucopetinventory.util.gson;

import java.util.Optional;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class MapperJsonObjectJackson implements MapperJsonObject {

    private final ObjectMapper objectMapper;

    public MapperJsonObjectJackson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<String> execute(Object object) {
        try {
            return Optional.ofNullable(objectMapper.writeValueAsString(object));
        } catch (RuntimeException notSerializable) {
            return Optional.empty();
        }
    }

    @Override
    public <T> Optional<T> execute(String json, Class<T> destinationClass) {
        try {
            return Optional.ofNullable(objectMapper.readValue(json, destinationClass));
        } catch (RuntimeException notDeserializable) {
            return Optional.empty();
        }
    }

    public <T> Optional<T> execute(JsonNode node, Class<T> destinationClass) {
        try {
            return Optional.ofNullable(objectMapper.treeToValue(node, destinationClass));
        } catch (RuntimeException notDeserializable) {
            return Optional.empty();
        }
    }
}
