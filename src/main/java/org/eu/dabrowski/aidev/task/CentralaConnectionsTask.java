package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.model.centrala.QueryDatabaseRequest;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.eu.dabrowski.aidev.model.graph.Relationship;
import org.eu.dabrowski.aidev.model.graph.User;
import org.eu.dabrowski.aidev.repository.UserRepository;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CentralaConnectionsTask extends AbstractTask {

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;

    private final Driver driver;

    private final UserRepository userRepository;


    public CentralaConnectionsTask(OpenAiChatModel chatModel, CentralaClient centralaClient, Driver driver, UserRepository userRepository) {
        super(chatModel);

        this.centralaClient = centralaClient;
        this.driver = driver;
        this.userRepository = userRepository;
    }

    public List<String> findRelationshipPath(String fromUsername, String toUsername) {
        String query = """
            MATCH path = shortestPath((start:User {username: $fromUsername})-[:KNOWS*]-(end:User {username: $toUsername}))
            RETURN [node IN nodes(path) | node.username] as path
        """;

        try (Session session = driver.session()) {
            return session.run(query,
                            Values.parameters("fromUsername", fromUsername, "toUsername", toUsername))
                    .stream()
                    .map(record -> record.get("path").asList(org.neo4j.driver.Value::asString))
                    .findFirst()
                    .orElse(Collections.emptyList());
        }
    }





    public void importUsers(List<User> users) {
        userRepository.saveAll(users);
    }

    public void createRelationships(List<Relationship> relationships) {
        relationships.forEach(rel -> {
            User user1 = userRepository.findById(rel.getFromUserId()).orElseThrow();
            User user2 = userRepository.findById(rel.getToUserId()).orElseThrow();
            user1.getKnows().add(user2);
            userRepository.save(user1);
        });
    }

    @Override
    @SneakyThrows
    Object compute(Object object) {
        JsonNode connectionsNode = centralaClient.apidb(QueryDatabaseRequest.builder()
                .task("database")
                .query("select * from connections")
                .apikey(centralaApiKey)
                .build()).get("reply");
        List<Relationship> relationships = objectMapper.readValue(
                connectionsNode.toString(),
                new TypeReference<List<Relationship>>() {
                });
        JsonNode usersNode = centralaClient.apidb(QueryDatabaseRequest.builder()
                .task("database")
                .query("select * from users")
                .apikey(centralaApiKey)
                .build()).get("reply");
        List<User> users = objectMapper.readValue(
                usersNode.toString(),
                new TypeReference<List<User>>() {
                });
        importUsers(users);
        createRelationships(relationships);
        String result = findRelationshipPath("Rafał", "Barbara")
                .stream().collect(Collectors.joining(","));

        JsonNode response = centralaClient.report(ReportRequest.builder()
                .task("connections")
                .apikey(centralaApiKey)
                .answer(result)
                .build());

        return getFlag(response.toString());
    }

}
