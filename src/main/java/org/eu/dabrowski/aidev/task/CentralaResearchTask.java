package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.client.FileClient;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
@Slf4j
public class CentralaResearchTask extends AbstractTask {

    private ChatClient chatClient;
    private final FileClient fileClient;

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;



    public CentralaResearchTask(OpenAiChatModel chatModel, FileClient fileClient, CentralaClient centralaClient) {
        super(chatModel);
        this.fileClient = fileClient;
        this.centralaClient = centralaClient;
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()),
                        new SimpleLoggerAdvisor())
                .build();
    }


    @Override
    @SneakyThrows
    Object compute(Object object) {
        String checkContent = "";
        StringBuilder fineTuningContent = new StringBuilder();
        byte[] zipFileInBytes = fileClient.getFileAsBytes(centralaUrl + "/dane/lab_data.zip");
        Path tempPath = Files.createTempFile("temp", ".zip");
        Files.write(tempPath, zipFileInBytes, StandardOpenOption.WRITE);
        ZipFile zipFile = new ZipFile(tempPath.toFile());
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        Path tempDir = Files.createTempDirectory("fine-tuning");
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                try (InputStream entryStream = zipFile.getInputStream(entry)) {
                    if(entry.getName().contains("correct")) {
                        byte[] fileInBytes = entryStream.readAllBytes();
                        String content = new String(fileInBytes);
                        boolean correct = entry.getName().contains("incorrect")? false : true;
                        fineTuningContent.append(prepareContent(content, correct));

                    }else{
                        checkContent =  new String(entryStream.readAllBytes());

                    }

                }
            }
        }
        Path tempFilePath = tempDir.resolve("finetuning.jsonl");
        Files.write(tempFilePath, fineTuningContent.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
        //Get the fileName for fine-tuning and use it using OpenAI web console
        //Remember to set it application.yml proper fine-tuned model
        log.info("Saved file {}", tempFilePath.toFile().getAbsolutePath());

        ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
        for(String line: checkContent.split("\n")){
            String id = line.split("=")[0];
            String content = line.split("=")[1];
            ChatResponse chatResponse = chatClient.prompt()
                    .user(content)
                    .call()
                    .chatResponse();
            String responseContent = chatResponse.getResult().getOutput().getContent();
            if(responseContent.equals("1")){
                arrayNode.add(id);
            }

        }


            JsonNode response = centralaClient.report(ReportRequest.builder()
                    .task("research")
                    .apikey(centralaApiKey)
                    .answer(arrayNode)
                    .build());


        return getFlag(response.toString());
    }

    public String prepareContent(String content, boolean correct) {
        StringBuffer output = new StringBuffer();
        for (String line: content.split("\n")){
            output.append(String.format("{\"messages\":[{\"content\":\"validate numbers\",\"role\":\"system\"},{\"content\":\"%s\",\"role\":\"user\"},{\"content\":\"%s\",\"role\":\"assistant\"}]}",
                    line, correct ? "1" : "0"));
            output.append("\n");
        }
        return output.toString();

    }


}
