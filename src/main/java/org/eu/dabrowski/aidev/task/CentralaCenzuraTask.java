package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.databind.JsonNode;
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
public class CentralaCenzuraTask extends AbstractTask {

    private static String SYSTEM_MESSAGE = "Your task is to identify and replace any personal data present in a sentence with the word \"CENZURA.\" Personal data includes but is not limited to names, addresses, phone numbers, email addresses, social security numbers, age and any other identifiable information. Below are examples to guide you:\n" +
            "\n" +
            "1. Original: \"John Doe lives at 123 Elm Street and his phone number is 555-1234.\"\n" +
            "   Transformed: \"CENZURA lives at CENZURA and his phone number is CENZURA.\"\n" +
            "\n" +
            "2. Original: \"You can contact Jane at jane.doe@example.com for more information.\"\n" +
            "   Transformed: \"You can contact CENZURA at CENZURA for more information.\"\n" +
            "\n" +
            "3. Original: \"Michael's social security number is 123-45-6789.\"\n" +
            "   Transformed: \"CENZURA's social security number is CENZURA.\"\n" +
            "\n" +
            "Please process the following sentences by replacing any personal data with \"CENZURA\":\n";
    private ChatClient chatClient;
    private final FileClient fileClient;
    private final XyzClient xyzClient;

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;

    public CentralaCenzuraTask(OpenAiChatModel chatModel, FileClient fileClient, XyzClient xyzClient, CentralaClient centralaClient) {
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
                + "/cenzura.txt");

        ChatResponse chatResponse = chatClient.prompt()
                .user(fileContent)
                .system(SYSTEM_MESSAGE)
                .call()
                .chatResponse();
        String responseContent = chatResponse.getResult().getOutput().getContent();

        JsonNode response = centralaClient.report(ReportRequest.builder()
                .task("CENZURA")
                .apikey(centralaApiKey)
                .answer(responseContent)
                .build());

        return getFlag(response.toString());
    }

}
