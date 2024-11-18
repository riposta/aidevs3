package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.client.FileClient;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
@Slf4j
public class CentralaDokumentyTask extends AbstractTask {

    private static String SYSTEM_MESSAGE = "**Prompt Systemowy do Generowania słów kluczowych Raportów**\n" +
            "\n" +
            "**Cel:** Generowanie precyzyjnych i użytecznych słów kluczowych dla raportów dotyczących wydarzeń związanych z bezpieczeństwem " +
            "w różnych sektorach wokół fabryki, w celu ułatwienia wyszukiwania i analizy. " +
            "Słowa kluczowe będą zawierały słowa w języku polskim w mianowniku. " +
            "Słowa kluczowe muszą uwzględniać informacje o uczestnikach na bazie poniższych faktów." +
            "Słowa kluczowe muszą uwzględniać sektor zdarzenia, informacje o uczestnikach na bazie poniższych faktów, takie jak ich zawód, umiejetności, powiązania, itp. \n" +
            "\n" +
            "**Format:**\n" +
            "słowo_kluczowe1,słowo_kluczowe2,słowo_kluczoweN\n";

    private ChatClient chatClient;
    private final FileClient fileClient;

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;

    public CentralaDokumentyTask(OpenAiChatModel chatModel, FileClient fileClient, CentralaClient centralaClient,
                                 OpenAiAudioTranscriptionModel transcriptionModel) {
        super(chatModel);
        this.fileClient = fileClient;
        this.centralaClient = centralaClient;
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor())
                .build();
    }



    @Override
    @SneakyThrows
    Object compute(Object object) {
        String output = "";

        StringBuffer facts = new StringBuffer();
        HashMap<String, String> fileMap = new HashMap<>();
        HashMap<String, String> keywordsMap = new HashMap<>();

        byte[] zipFileInBytes = fileClient.getFileAsBytes(centralaUrl + "/dane/pliki_z_fabryki.zip");
        Path tempPath = Files.createTempFile("temp", ".zip");
        Files.write(tempPath, zipFileInBytes, StandardOpenOption.WRITE);
        ZipFile zipFile = new ZipFile(tempPath.toFile());
        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        Path tempDir = Files.createTempDirectory("text");

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().endsWith(".txt")) {
                try (InputStream entryStream = zipFile.getInputStream(entry)) {
                    byte[] fileInBytes = entryStream.readAllBytes();
                    Path tempFilePath = tempDir.resolve(entry.getName());
                    if(tempFilePath.toFile().getAbsolutePath().contains("/facts/")) {
                        String text = new String(fileInBytes);
                        if(!text.contains("entry deleted")) {
                            facts.append(new String(fileInBytes));
                        }
                    }else{
                        fileMap.put(entry.getName(), new String(fileInBytes));
                    }
                }
            }
        }
        fileMap.forEach((key, value) -> {
            ChatResponse chatResponse = chatClient.prompt()
                    .user(key +": " + value + "\n\n\n<facts>\n" + facts + "\n</facts>")
                    .system(SYSTEM_MESSAGE)
                    .call()
                    .chatResponse();
            keywordsMap.put(key, key + " -> " + chatResponse.getResult().getOutput().getContent());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        });

        String responseContent = objectMapper.writeValueAsString(keywordsMap);

        JsonNode response = centralaClient.report(ReportRequest.builder()
                .task("dokumenty")
                .apikey(centralaApiKey)
                .answer(objectMapper.readTree(responseContent))
                .build());


        return getFlag(response.toString());
    }

    public String extractJson(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');

        if (firstBrace == -1 || lastBrace == -1 || firstBrace > lastBrace) {
            return "";
        }

        return text.substring(firstBrace, lastBrace + 1);
    }

    public static String getFileExtension(File file) {
        String fileName = file.getName();
        int lastIndexOfDot = fileName.lastIndexOf('.');
        if (lastIndexOfDot == -1) {
            return "";
        }
        return fileName.substring(lastIndexOfDot + 1).toLowerCase();
    }


}
