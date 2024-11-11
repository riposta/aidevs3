package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.client.FileClient;
import org.eu.dabrowski.aidev.model.centrala.xyz.ReportRequest;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
@Slf4j
public class CentralaPrzesluchanieTask extends AbstractTask {
    private static String TASK_NAME = "CentralaPrzesluchanie";

    private static String SYSTEM_MESSAGE = "Jesteś detektywem. Poniżej są zeznania świadków. Pamiętaj, że zeznania świadków mogą być sprzeczne, niektórzy z nich mogą się mylić, a inni odpowiadać w dość dziwaczny sposób.\n" +
            "Dowiedz się na  jakim wydziale i uczelni pracował Andrzej Maj.\n" +
            "Twoją odpowiedzią ma być sam adres wydziału. Zwóć sam adres, bez dodatkowych przemyśleń.";
    private ChatClient chatClient;
    private final FileClient fileClient;

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;

    private final OpenAiAudioTranscriptionModel transcriptionModel;


    private final Encoder encoder;

    public CentralaPrzesluchanieTask(OpenAiChatModel chatModel, FileClient fileClient, CentralaClient centralaClient,
                                     OpenAiAudioTranscriptionModel transcriptionModel, OpenAiAudioTranscriptionModel transcriptionModel1) {
        super(chatModel);
        this.fileClient = fileClient;
        this.centralaClient = centralaClient;
        this.transcriptionModel = transcriptionModel1;
        this.encoder = new Encoder();
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()),
                        new SimpleLoggerAdvisor())
                .build();
    }


    @Override
    @SneakyThrows
    Object compute(Object object) {
        Map<String, String> audioMap = new HashMap<>();
        OpenAiAudioApi.TranscriptResponseFormat responseFormat = OpenAiAudioApi.TranscriptResponseFormat.TEXT;

        OpenAiAudioTranscriptionOptions transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
                .withLanguage("pl")
                .withPrompt("")
                .withTemperature(0f)
                .withResponseFormat(responseFormat)
                .build();

        byte[] zipFileInBytes = fileClient.getFileAsBytes(centralaUrl + "/dane/przesluchania.zip");
        Path tempPath = Files.createTempFile("temp", ".zip");
        Files.write(tempPath, zipFileInBytes, StandardOpenOption.WRITE);
        ZipFile zipFile = new ZipFile(tempPath.toFile());
        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        Path tempDir = Files.createTempDirectory("audio");

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                try (InputStream entryStream = zipFile.getInputStream(entry)) {
                    byte[] fileInBytes = entryStream.readAllBytes();
                    Path tempFilePath = tempDir.resolve(entry.getName());
                    Files.write(tempFilePath, fileInBytes, StandardOpenOption.CREATE);
                    File mp3File = convertFromM4aToMp3(tempFilePath.toFile());
                    Resource audioFile = new FileSystemResource(mp3File);
                    AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(audioFile, transcriptionOptions);
                    AudioTranscriptionResponse response = transcriptionModel.call(transcriptionRequest);
                    String output = response.getResult().getOutput();
                    audioMap.put(entry.getName().replace(".m4a", ""), output);
                }
            }
        }
        String jsonOutput = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(audioMap);


            ChatResponse chatResponse = chatClient.prompt()
                    .user(jsonOutput)
                    .system(SYSTEM_MESSAGE)
                    .call()
                    .chatResponse();
            String responseContent = chatResponse.getResult().getOutput().getContent();

            JsonNode response = centralaClient.report(ReportRequest.builder()
                    .task("MP3")
                    .apikey(centralaApiKey)
                    .answer(responseContent)
                    .build());


        return getFlag(response.toString());
    }

    @Override
    public boolean accept(String taskName) {
        return taskName.equals(TASK_NAME);
    }


    @SneakyThrows
    private File convertFromM4aToMp3(File source){
        File target = new File(source.getAbsolutePath().replace(".m4a", ".mp3"));
        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("libmp3lame");
        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("mp3");
        attrs.setAudioAttributes(audio);
        encoder.encode(new MultimediaObject(source), target, attrs);
        return target;
    }


}
