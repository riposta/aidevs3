package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.client.FileClient;
import org.eu.dabrowski.aidev.client.XyzClient;
import org.eu.dabrowski.aidev.configuration.CustomFeignException;
import org.eu.dabrowski.aidev.model.centrala.QueryLoopRequest;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Component
@Slf4j
public class CentralaLoopTask extends AbstractTask {

    private static String SYSTEM_MESSAGE = "Masz do dyspozycji bazę danych. Twoim zadaniem jest otrzymanie odpowiedzi na pytanie: \n" +
            "         Gdzie znajduje się w tej chwili Barbara Nowadzka (nie w przeszłości) \n" +
            "\n" +
            "            W user prompt otrzymywać będziesz odpowiedzi na wywołanie narzędzi. Musisz zwracać tylko komendy, nic poza tym. Poruszaj się tylko w ramach imion i miast zwróconych z narzędzi. W momencie osiągnięcia celu wyświetl treść: \n" +
            "             DONE|<miasto>\n\n" +
            "            Do dyspozycji masz następujące narzędzia: \n" +
            "            - GET|people|<imie> - Pierwsze narzędzie to wyszukiwarka członków ruchu oporu. Możemy wyszukiwać ich z użyciem imienia podanego w formie mianownika, a w odpowiedzi otrzymamy listę miejsc, w których ich widziano. Mogą być to dane z przeszłości. Wyślij samo imię bez nazwiska.\n" +
            "            - GET|places|<nazwa miasta> - Drugie narzędzie to wyszukiwarka miejsc odwiedzonych przez konkretne osoby. Podajesz nazwę miasta do sprawdzenia (bez polskich znaków) i w odpowiedzi dowiadujesz się, których z członków ruchu oporu tam widziano. Mogą być to dane z przeszłości\n\n\n" +
            "###\nPoniżej treść która może Ci pomóc:";
    private ChatClient chatClient;
    private final FileClient fileClient;
    private final XyzClient xyzClient;

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;

    public CentralaLoopTask(OpenAiChatModel chatModel, FileClient fileClient, XyzClient xyzClient, CentralaClient centralaClient) {
        super(chatModel);
        this.fileClient = fileClient;
        this.xyzClient = xyzClient;
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new PromptChatMemoryAdvisor(new InMemoryChatMemory()),
                        new SimpleLoggerAdvisor())
                .build();


        this.centralaClient = centralaClient;
    }


    @Override
    @SneakyThrows
    Object compute(Object object) {
        String id = UUID.randomUUID().toString();
        String story = fileClient.getFileContent(centralaUrl + "/dane/barbara.txt");
        List<String> resultList = Lists.newArrayList();
        String userMsg = "empty";

        while (true) {

            ChatResponse chatResponse = chatClient.prompt()
                    .user(userMsg)
                    .system(SYSTEM_MESSAGE + story)
                    .advisors(advisorSpec -> advisorSpec
                            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, id))
                    .call()
                    .chatResponse();
            userMsg = chatResponse.getResult().getOutput().getContent();
            resultList = Arrays.asList(userMsg.split("\\|"));
            Thread.sleep(500);
            try {
                if (resultList.get(0).equals("DONE")) {
                    JsonNode response = centralaClient.report(ReportRequest.builder()
                            .task("loop")
                            .apikey(centralaApiKey)
                            .answer(resultList.get(1))
                            .build());
                    String flag = getFlag(response.toString());
                    if(Objects.nonNull(flag)){
                        return flag;
                    }else{
                        userMsg = response.toPrettyString();
                    }
                } else if (resultList.get(0).equals("GET")) {

                    if (resultList.get(1).equals("people")) {

                        userMsg = centralaClient.people(QueryLoopRequest.builder()
                                .apikey(centralaApiKey)
                                .query(resultList.get(2))
                                .build()).toPrettyString();
                    } else if (resultList.get(1).equals("places")) {
                        userMsg = centralaClient.places(QueryLoopRequest.builder()
                                .apikey(centralaApiKey)
                                .query(resultList.get(2))
                                .build()).toPrettyString();
                    } else {
                        throw new InvalidParameterException("Wrong tool response");
                    }


                } else {
                    throw new InvalidParameterException("Wrong response");
                }
            }catch (CustomFeignException e){
                JsonNode errorBody = e.getErrorBody();
                userMsg = errorBody.toPrettyString();

            }


        }


    }

}
