package org.eu.dabrowski.aidev.model.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;


@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Node("User")
public class User {
    @Id
    private String id;
    private String username;
    private String accessLevel;
    private boolean isActive;
    private String lastlog;

    @Relationship(type = "KNOWS", direction = Relationship.Direction.OUTGOING)
    private Set<User> knows = new HashSet<>();

}