package org.eu.dabrowski.aidev.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

@Getter
public class CustomFeignException extends RuntimeException {
    private final JsonNode errorBody;

    public CustomFeignException(JsonNode errorBody) {
        super(errorBody.toString());
        this.errorBody = errorBody;
    }

}
