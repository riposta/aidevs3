package org.eu.dabrowski.aidev.configuration;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class Neo4jConfiguration {


    @Bean
    public Driver neo4jDriver() {
        return GraphDatabase.driver("neo4j://localhost:7687",
                AuthTokens.basic("neo4j", "password123"),
                Config.builder()
                        .withMaxConnectionLifetime(30, TimeUnit.MINUTES)
                        .withMaxConnectionPoolSize(50)
                        .withConnectionAcquisitionTimeout(2, TimeUnit.MINUTES)
                        .build()
        );
    }

}
