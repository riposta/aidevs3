package org.eu.dabrowski.aidev.repository;

import org.eu.dabrowski.aidev.model.graph.User;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends Neo4jRepository<User, String> {
    User findByUsername(String username);
}