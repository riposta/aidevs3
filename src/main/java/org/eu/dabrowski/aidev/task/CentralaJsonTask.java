package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.client.FileClient;
import org.eu.dabrowski.aidev.client.XyzClient;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CentralaJsonTask extends AbstractTask {

    private static String SYSTEM_MESSAGE = "You are chat assistant. " +
            "Reply for the question.";
    private ChatClient chatClient;
    private final FileClient fileClient;
    private final XyzClient xyzClient;

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;

    public CentralaJsonTask(OpenAiChatModel chatModel, FileClient fileClient, XyzClient xyzClient, CentralaClient centralaClient) {
        super(chatModel);
        this.fileClient = fileClient;
        this.xyzClient = xyzClient;
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()),
                        new SimpleLoggerAdvisor())
                .build();


        this.centralaClient = centralaClient;
    }


    @Override
    @SneakyThrows
    Object compute(Object object) {
        String fileContent = fileClient.getFileContent(centralaUrl + "/data/" + centralaApiKey
                + "/json.txt");
        JsonNode rootNode = objectMapper.readTree(fileContent);
        ((ObjectNode) rootNode).put("apikey", centralaApiKey);

        ArrayNode testData = (ArrayNode) rootNode.get("test-data");
        for (JsonNode node : testData) {
            String question = node.get("question").asText();
            String[] operands = question.split(" \\+ ");

            if (operands.length == 2) {
                int answer = Integer.parseInt(operands[0]) + Integer.parseInt(operands[1]);
                int oldAswer = node.get("answer").asInt();
                if(answer != oldAswer) {
                    ((ObjectNode) node).put("answer", answer);
                    log.info("Question: {}", question);
                    log.info("Updated answer from {} to {} ", oldAswer, answer);
                }
            }
            if (node.has("test")) {
                JsonNode testNode = node.get("test");
                if (testNode.has("q")) {
                    String testQuestion = testNode.get("q").asText();
                    ChatResponse chatResponse = chatClient.prompt()
                            .user(testQuestion)
                            .system(SYSTEM_MESSAGE)
                            .call()
                            .chatResponse();
                    String responseContent = chatResponse.getResult().getOutput().getContent();
                    ((ObjectNode) testNode).put("a", responseContent);
                }
            }
        }
        JsonNode response = centralaClient.report(ReportRequest.builder()
                .task("JSON")
                .apikey(centralaApiKey)
                .answer(rootNode)
                .build());

        return getFlag(response.toString());
    }



}
