package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.client.FileClient;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.Media;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import ws.schild.jave.Encoder;

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
public class CentralaKategorieTask extends AbstractTask {

    private static String SYSTEM_MESSAGE = "Categoryze the text to four categories:\n" +
            "information about captured people or traces of their presence -> PEOPLE\n" +
            "information about repaired hardware faults -> HARDWARE\n" +
            "information about repaired software faults -> SOFTWARE\n" +
            "other information including text about pizza and abandoned towns -> OTHER\n" +
            "Return only category without any additional text.";
    private static String SYSTEM_MESSAGE_CATEGORY = "Change filenames with categories to JSON in format below." +
            "Return only JSON without any additional text." +
            "{\n" +
            "  \"people\": [\"plik1.txt\", \"plik2.mp3\", \"plikN.png\"],\n" +
            "  \"hardware\": [\"plik4.txt\", \"plik5.png\", \"plik6.mp3\"],\n" +
            "}";
    private ChatClient chatClient;
    private final FileClient fileClient;

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;

    private final OpenAiAudioTranscriptionModel transcriptionModel;


    private final Encoder encoder;

    private final OpenAiAudioTranscriptionOptions transcriptionOptions;

    public CentralaKategorieTask(OpenAiChatModel chatModel, FileClient fileClient, CentralaClient centralaClient,
                                 OpenAiAudioTranscriptionModel transcriptionModel) {
        super(chatModel);
        this.fileClient = fileClient;
        this.centralaClient = centralaClient;
        this.transcriptionModel = transcriptionModel;
        this.encoder = new Encoder();
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor())
                .build();
        OpenAiAudioApi.TranscriptResponseFormat responseFormat = OpenAiAudioApi.TranscriptResponseFormat.TEXT;
        this.transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
                .withLanguage("pl")
                .withPrompt("")
                .withTemperature(0f)
                .withResponseFormat(responseFormat)
                .build();
    }

    private String getCategoryFromText(String input){
        ChatResponse chatResponse = chatClient.prompt()
                .user(input)
                .system(SYSTEM_MESSAGE)
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getContent();
    }

    private String getCategoryFromAudioFile(File file){
        Resource audioFile = new FileSystemResource(file);
        AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(audioFile, transcriptionOptions);
        AudioTranscriptionResponse response = transcriptionModel.call(transcriptionRequest);
        String transcription = response.getResult().getOutput();
        String categoryFromText = getCategoryFromText(transcription);
        return file.getName() + ": " + categoryFromText;
    }


    private String getCategoryFromImageFile(File file){
        var userMessage = new UserMessage("You are OCR. Get the text from image. " +
                "Return only OCR text without any additinal text",
                new Media(MimeTypeUtils.IMAGE_PNG, new FileSystemResource(file)));
        ChatResponse chatResponse = chatClient.prompt()
                .messages(userMessage)
                .call()
                .chatResponse();
        String description = chatResponse.getResult().getOutput().getContent();
        String categoryFromText = getCategoryFromText(description);
        return file.getName() + ": " + categoryFromText;
    }

    @SneakyThrows
    private String getCategoryFromTextFile(File file){
        String text = Files.readString(file.toPath());
        String categoryFromText = getCategoryFromText(text);
        return file.getName() + ": " + categoryFromText;
    }


    @Override
    @SneakyThrows
    Object compute(Object object) {
        String output = "";
        Map<String, String> audioMap = new HashMap<>();
        OpenAiAudioApi.TranscriptResponseFormat responseFormat = OpenAiAudioApi.TranscriptResponseFormat.TEXT;

        OpenAiAudioTranscriptionOptions transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
                .withLanguage("pl")
                .withPrompt("")
                .withTemperature(0f)
                .withResponseFormat(responseFormat)
                .build();

        byte[] zipFileInBytes = fileClient.getFileAsBytes(centralaUrl + "/dane/pliki_z_fabryki.zip");
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
                    if(!tempFilePath.toFile().getAbsolutePath().contains("/facts/")) {
                        Files.write(tempFilePath, fileInBytes, StandardOpenOption.CREATE);
                        File tempFile = tempFilePath.toFile();

                    switch (getFileExtension(tempFile)){
                        case "mp3":
                            output += getCategoryFromAudioFile(tempFile) +"\n";
                            break;
                        case "png":
                            output += getCategoryFromImageFile(tempFile) +"\n";
                            break;
                        case "txt":
                            output += getCategoryFromTextFile(tempFile) +"\n";
                            break;
                    }
                    }

                }
            }
        }

            ChatResponse chatResponse = chatClient.prompt()
                    .user(output)
                    .system(SYSTEM_MESSAGE_CATEGORY)
                    .call()
                    .chatResponse();
            String responseContent = extractJson(chatResponse.getResult().getOutput().getContent());

            JsonNode response = centralaClient.report(ReportRequest.builder()
                    .task("kategorie")
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
