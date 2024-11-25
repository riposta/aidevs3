package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.client.FileClient;
import org.eu.dabrowski.aidev.client.XyzClient;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.List;
import java.util.UUID;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Component
@Slf4j
public class CentralaPhotosTask extends AbstractTask {
    private final String SYSTEM_MESSAGE = "Return urlList from userprompt. " +
            "You must return JSON List containing absolute url to photo files. " +
            "Try to remember the base url if you will get only fileName. " +
            "Return them as JSON array without any additional characters. " +
            "If there are not urls return only phrase ERROR";

    private ChatClient chatClient;
    private final FileClient fileClient;
    private final XyzClient xyzClient;

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;

    public CentralaPhotosTask(OpenAiChatModel chatModel, FileClient fileClient, XyzClient xyzClient, CentralaClient centralaClient) {
        super(chatModel);
        this.fileClient = fileClient;
        this.xyzClient = xyzClient;
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new PromptChatMemoryAdvisor(new InMemoryChatMemory()),
                        new SimpleLoggerAdvisor())
                .build();


        this.centralaClient = centralaClient;
    }

    private static String generateUserPhotoMessage(String imageName) {
        return String.format(
                "Opisz zdjęcie o nazwie %s, jeśli zdjęcie jest nieczytelne mogę je poprawić. " +
                        "W takim wypadku zwróć jedno z poniższych poleceń. " +
                        "Poszukuję zdjęcia na którym jest sama kobieta. " +
                        "Jeśli będzie miała ciemne włosy napisz czarne włosy\n" +
                        "\n" +
                        "Oto polecenia, które możesz zwrócić. Nie zwracaj nic poza poleceniami:\n" +
                        "FUNCTION|REPAIR NAZWA_PLIKU\n" +
                        "FUNCTION|DARKEN NAZWA_PLIKU\n" +
                        "FUNCTION|BRIGHTEN NAZWA_PLIKU\n" +
                        "RESULT|WRONG_PHOTO\n" +
                        "RESULT|BARBARA_PHOTO|<Opis wyglądu Barbary>",
                imageName
        );
    }

    public boolean isValidJSON(String jsonString) {
        try {
            objectMapper.readTree(jsonString);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    @SneakyThrows
    public List<String> getLinks(String toolInput, String id) {
        String toolOutput = centralaClient.report(ReportRequest.builder()
                .apikey(centralaApiKey)
                .task("photos")
                .answer(toolInput)
                .build()).toPrettyString();

        String chatResponse = chatClient.prompt()
                .user(toolOutput)
                .system(SYSTEM_MESSAGE)
                .advisors(advisorSpec -> advisorSpec
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, id))
                .call()
                .chatResponse().getResult().getOutput().getContent();
        if (chatResponse.equals("ERROR")) {
            return List.of();
        } else if (isValidJSON(chatResponse)) {
            return objectMapper.readValue(chatResponse, new TypeReference<List<String>>() {
            });
        } else {
            throw new RuntimeException("Something went wrong");
        }
    }

    public static String extractFileName(String url) {
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex != -1 && lastSlashIndex < url.length() - 1) {
            return url.substring(lastSlashIndex + 1);
        } else {
            throw new RuntimeException("Wrong url: " + url);
        }
    }

    @SneakyThrows
    private String getDescriptionFromImage(String url) {

        var userMessage = new UserMessage(generateUserPhotoMessage(extractFileName(url)),
                new Media(MimeTypeUtils.IMAGE_PNG, new UrlResource(url)));
        ChatResponse chatResponse = chatClient.prompt()
                .messages(userMessage)
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getContent();
    }


    @Override
    @SneakyThrows
    Object compute(Object object) {
        String id = UUID.randomUUID().toString();

        List<String> links = getLinks("START", id);
        while (true) {
            if(links.isEmpty()){
                throw new RuntimeException("Semething went wrong as list of urls is empty." +
                        "Barbara wasn't recognized");
            }
            String link = links.remove(0);
            String description = getDescriptionFromImage(link);
            if (description.startsWith("RESULT|WRONG_PHOTO")) {
                log.info("Link {} doesn't contain Barbara", link);
            } else if (description.startsWith("RESULT|BARBARA_PHOTO")) {
                log.info("Link {} contains Barbara. Description:\n{}", link, description);
                return getFlag(centralaClient.report(ReportRequest.builder()
                        .apikey(centralaApiKey)
                        .task("photos")
                        .answer(description.replace("RESULT|BARBARA_PHOTO|", ""))
                        .build()).toPrettyString());
            } else if (description.startsWith("FUNCTION")) {
                List<String> returnLinks = getLinks(description.replace("FUNCTION|", ""), id);
                if (!returnLinks.isEmpty()) {
                    links.addAll(returnLinks);
                }

            }
        }

    }

}
