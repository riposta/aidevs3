package org.eu.dabrowski.aidev.model.centrala.xyz;


import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequest {
    private String task;
    private String apikey;
    private JsonNode answer;
}
