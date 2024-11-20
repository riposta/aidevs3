package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.client.FileClient;
import org.eu.dabrowski.aidev.client.XyzClient;
import org.eu.dabrowski.aidev.model.centrala.QueryRequest;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Component
@Slf4j
public class CentralaDatabaseTask extends AbstractTask {

    private static String SYSTEM_MESSAGE = "Masz do dyspozycji bazę danych. Twoim zadaniem jest otrzymanie odpowiedzi na pytanie:\n" +
            "które aktywne datacenter (DC_ID) są zarządzane przez pracowników, którzy są na urlopie (is_active=0)\n" +
            "W user prompt otrzymywać będziesz odpowiedzi na wywołanie narzędzi. Musisz zwracać tylko komendy, nic poza tym. W momencie osiągnięcia celu wyświetl treść:\n" +
            "{\n" +
            "\"status\": \"DONE\",\n" +
            "\"DC_ID\": [\"<dc_id1>\",\"<dc_id2>\",\"<dc_id3>\"]\n" +
            "}\n" +
            "\n" +
            "Do dyspozycji masz następujące narzędzia:\n" +
            "- dowolny SELECT, np. \"select * from users limit 1\"\n" +
            "- show tables = zwraca listę tabel\n" +
            "- show create table NAZWA_TABELI = pokazuje, jak zbudowana jest konkretna tabela";
    private ChatClient chatClient;
    private final FileClient fileClient;
    private final XyzClient xyzClient;

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;

    public CentralaDatabaseTask(OpenAiChatModel chatModel, FileClient fileClient, XyzClient xyzClient, CentralaClient centralaClient) {
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
        String id= UUID.randomUUID().toString();
        String userMsg = "empty";
        boolean loopContinue = true;

        while(loopContinue) {
            ChatResponse chatResponse = chatClient.prompt()
                    .user(userMsg)
                    .system(SYSTEM_MESSAGE)
                    .advisors(advisorSpec -> advisorSpec
                            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, id))
                    .call()
                    .chatResponse();
            userMsg = chatResponse.getResult().getOutput().getContent();
            if(userMsg.contains("DONE")) {
                break;
            }else{
                userMsg = centralaClient.apidb(QueryRequest.builder()
                                .apikey(centralaApiKey)
                                .task("database")
                                .query(userMsg)
                        .build()).toPrettyString();
            }

        }
        JsonNode rootNode = objectMapper.readTree(userMsg);
        JsonNode dcIdArray = rootNode.get("DC_ID");

        JsonNode response = centralaClient.report(ReportRequest.builder()
                .task("database")
                .apikey(centralaApiKey)
                .answer(dcIdArray)
                .build());

        return getFlag(response.toString());
    }

}
