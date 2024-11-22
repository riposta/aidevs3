package org.eu.dabrowski.aidev.model.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Relationship {
    @JsonProperty("user1_id")
    private String fromUserId;
    @JsonProperty("user2_id")
    private String toUserId;
}
