package org.eu.dabrowski.aidev.model.centrala;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryLoopRequest {
    private String apikey;
    private String query;
}
