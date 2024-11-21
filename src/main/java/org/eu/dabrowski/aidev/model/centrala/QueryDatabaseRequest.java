package org.eu.dabrowski.aidev.model.centrala;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryDatabaseRequest {
    private String task;
    private String apikey;
    private Object query;
}
