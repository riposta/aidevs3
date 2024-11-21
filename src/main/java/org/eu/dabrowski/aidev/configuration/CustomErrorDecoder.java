package org.eu.dabrowski.aidev.configuration;

import feign.Response;
import feign.codec.ErrorDecoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

public class CustomErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new Default();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        try {
            JsonNode errorBody = objectMapper.readTree(response.body().asInputStream());
            return new CustomFeignException(errorBody);
        } catch (Exception e) {
            return defaultErrorDecoder.decode(methodKey, response);
        }
    }
}

