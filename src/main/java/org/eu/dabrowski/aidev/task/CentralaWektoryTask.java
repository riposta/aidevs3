package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.client.FileClient;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.eu.dabrowski.aidev.model.xyz.ArxivContent;
import org.eu.dabrowski.aidev.model.xyz.WektoryContent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class CentralaWektoryTask extends AbstractTask {


    private ChatClient chatClient;
    private final FileClient fileClient;

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;


    private List<ArxivContent> contents;

    @Qualifier("wektory")
    private final VectorStore wektoryVectorStore;


    public CentralaWektoryTask(OpenAiChatModel chatModel, FileClient fileClient, CentralaClient centralaClient,
                               VectorStore wektoryVectorStore) {
        super(chatModel);
        this.fileClient = fileClient;
        this.centralaClient = centralaClient;
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor())
                .build();
        this.wektoryVectorStore = wektoryVectorStore;
        contents = new ArrayList<>();
    }





    @Override
    @SneakyThrows
    Object compute(Object object) {
        String question = "Którego dnia skradziono prototyp broni?";
        List<WektoryContent> contents = new ArrayList<>();
        byte[] zipFileInBytes = fileClient.getFileAsBytes(centralaUrl + "/dane/pliki_z_fabryki.zip");
        Path tempPath = Files.createTempFile("temp", ".zip");
        Files.write(tempPath, zipFileInBytes, StandardOpenOption.WRITE);

        try (ZipFile zipFile = new ZipFile(tempPath.toFile())) {
            List<? extends FileHeader> entries = zipFile.getFileHeaders().stream().toList();
            Path tempDir = Files.createTempDirectory("text");
            for (FileHeader entry : entries) {
                if (!entry.isDirectory() && entry.getFileName().endsWith(".zip")) {
                    Path tempFilePath = tempDir.resolve(entry.getFileName());
                    zipFile.extractFile(entry, tempDir.toString());

                    try (ZipFile zipFileInner = new ZipFile(tempFilePath.toFile(), "1670".toCharArray())) {
                        for (FileHeader entryInner : zipFileInner.getFileHeaders()) {
                            if (!entryInner.isDirectory() && entryInner.getFileName().endsWith(".txt")) {
                                zipFileInner.extractFile(entryInner, tempDir.toString());
                                Path extractedFilePath = tempDir.resolve(entryInner.getFileName());
                                contents.add(WektoryContent.builder()
                                                .date(entryInner.getFileName().replace("do-not-share/", "")
                                                        .replace(".txt", "").replace("_", "-"))
                                                .content(Files.readString(extractedFilePath))
                                        .build());
                            }
                        }
                    }
                }
            }
        }
        ByteArrayResource resource = new ByteArrayResource(objectMapper.writeValueAsString(contents).getBytes(StandardCharsets.UTF_8));
        JsonReader jsonReader = new JsonReader(resource,
                "date", "content");
        List<org.springframework.ai.document.Document> documents = jsonReader.get();
        wektoryVectorStore.write(documents);
        List<org.springframework.ai.document.Document> results = wektoryVectorStore.similaritySearch(SearchRequest.query(question).withTopK(1));
        String output = results.get(0).getContent().split("\n")[0].replace("date: ", "");
        JsonNode response = centralaClient.report(ReportRequest.builder()
                .task("wektory")
                .apikey(centralaApiKey)
                .answer(output)
                .build());


        return getFlag(response.toString());
    }


}
