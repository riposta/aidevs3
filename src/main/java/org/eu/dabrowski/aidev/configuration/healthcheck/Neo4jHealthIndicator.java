package org.eu.dabrowski.aidev.configuration.healthcheck;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Neo4jHealthIndicator {
    private final Driver driver;

    public Neo4jHealthIndicator(Driver driver) {
        this.driver = driver;
    }

    @PostConstruct
    public void verifyConnection() {
        try (Session session = driver.session()) {
            session.run("MATCH (n) RETURN count(n) as count")
                    .single()
                    .get("count")
                    .asLong();
            log.info("Successfully connected to Neo4j");
        } catch (Exception e) {
            log.error("Failed to connect to Neo4j: " + e.getMessage());
            throw e;
        }
    }
}