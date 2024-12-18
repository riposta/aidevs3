package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.client.FileClient;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.eu.dabrowski.aidev.model.xyz.ArxivContent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CentralaArxivTask extends AbstractTask {


    private ChatClient chatClient;
    private final FileClient fileClient;

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;

    private final OpenAiAudioTranscriptionModel transcriptionModel;


    private final OpenAiAudioTranscriptionOptions transcriptionOptions;

    private List<ArxivContent> contents;

    @Qualifier("arvix")
    private final VectorStore arvixVectorStore;


    public CentralaArxivTask(OpenAiChatModel chatModel, FileClient fileClient, CentralaClient centralaClient,
                             OpenAiAudioTranscriptionModel transcriptionModel, VectorStore arvixVectorStore) {
        super(chatModel);
        this.fileClient = fileClient;
        this.centralaClient = centralaClient;
        this.transcriptionModel = transcriptionModel;
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor())
                .build();
        this.arvixVectorStore = arvixVectorStore;
        OpenAiAudioApi.TranscriptResponseFormat responseFormat = OpenAiAudioApi.TranscriptResponseFormat.TEXT;
        this.transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
                .withLanguage("pl")
                .withPrompt("")
                .withTemperature(0f)
                .withResponseFormat(responseFormat)
                .build();
        contents = new ArrayList<>();
    }


    @SneakyThrows
    private String getTranscriptionOfAudioFile(String url) {
        Resource audioFile = new UrlResource(new URI(url).toURL());
        AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(audioFile, transcriptionOptions);
        AudioTranscriptionResponse response = transcriptionModel.call(transcriptionRequest);
        return "Audio: " + response.getResult().getOutput();
    }

    @SneakyThrows
    private String getDescriptionOfImageFile(String url, String caption) {

        var userMessage = new UserMessage("Describe object on photo in Polish. " +
                "Focus on information from the attached caption. " +
                "Based on that please write down paragraph about photo. " +
                "If it's object in the city include also city name." +
                "If photo represent food try to figure out dish name.\n\n" +
                "Caption:\n" + caption,
                new Media(MimeTypeUtils.IMAGE_PNG, new URI(url).toURL()));
        ChatResponse chatResponse = chatClient.prompt()
                .messages(userMessage)
                .call()
                .chatResponse();
        return "Obrazek: " + chatResponse.getResult().getOutput().getContent();
    }


    public List<ArxivContent> parseHtml(String html) {
        Document document = Jsoup.parse(html);
        Elements sections = document.select(".container > h1, .container > h2, .container > div#abstract," +
                " .container > p, .container > figure, h2, .chicago-bibliography > p,  .container > p, " +
                ".container > div.authors, .container > audio");
        String currentTitle = null;
        StringBuilder currentContent = new StringBuilder();

        for (Element element : sections) {
            if (element.tagName().equals("h1") || element.tagName().equals("h2")) {
                if (currentTitle != null) {
                    contents.add(ArxivContent.builder().title(currentTitle).content(currentContent.toString()).build());
                    currentContent = new StringBuilder();
                }
                currentTitle = element.text();
            } else if (element.tagName().equals("audio")) {
                Element source = element.selectFirst("source");
                if (source != null) {
                    currentContent.append(getTranscriptionOfAudioFile(centralaUrl + "/dane/" + source.attr("src")))
                            .append("\n");
                }
                currentContent.append(element.text()).append("\n");
            } else if (element.tagName().equals("div") && element.className().equals("authors")) {
                currentContent.append(element.text()).append("\n");
            } else if (element.tagName().equals("div") && element.id().equals("abstract")) {
                currentContent.append(element.text()).append("\n");
            } else if (element.tagName().equals("p") && element.parents().hasClass("chicago-bibliography")) {
                currentContent.append(element.text()).append("\n");
            } else if (element.tagName().equals("p")) {
                currentContent.append(element.text()).append("\n");
            } else if (element.tagName().equals("figure")) {
                Element img = element.selectFirst("img");
                if (img != null) {
                    currentContent.append(getDescriptionOfImageFile(centralaUrl + "/dane/" + img.attr("src"),
                                    element.select("figcaption").text()))
                            .append("\n");
                }

            }
        }

        if (currentTitle != null) {
            contents.add(ArxivContent.builder().title(currentTitle).content(currentContent.toString()).build());
        }
        return contents;
    }

    @SneakyThrows
    public void init() {
        String unparsedContent = fileClient.getFileContent(centralaUrl + "/dane/arxiv-draft.html");
        contents = parseHtml(unparsedContent);
        ByteArrayResource resource = new ByteArrayResource(objectMapper.writeValueAsString(contents).getBytes(StandardCharsets.UTF_8));
        JsonReader jsonReader = new JsonReader(resource,
                "title", "content");
        List<org.springframework.ai.document.Document> documents = jsonReader.get();

        arvixVectorStore.write(documents);

    }


    @Override
    @SneakyThrows
    Object compute(Object object) {
        init();
        String questionsFileString = fileClient.getFileContent(centralaUrl + "/data/" + centralaApiKey + "/arxiv.txt");
        ObjectNode responseNode = objectMapper.createObjectNode();
        for (String line : questionsFileString.split("\\r?\\n")) {
            String questionName = line.split("=")[0];
            String question = line.split("=")[1];
            List<org.springframework.ai.document.Document> results = arvixVectorStore.similaritySearch(SearchRequest.query(question).withTopK(2));
            ChatResponse chatResponse = chatClient.prompt()
                    .user(question)
                    .system("Odpowiedz na pytanie na podstawie poniższej treści. " +
                            "Wyświetl samą odpowiedź bez dodatkowych słow:\n\n" + results.stream().map(org.springframework.ai.document.Document::getContent)
                            .collect(Collectors.joining()))
                    .call()
                    .chatResponse();
            responseNode.put(questionName,chatResponse.getResult().getOutput().getContent());
        }


            JsonNode response = centralaClient.report(ReportRequest.builder()
                    .task("arxiv")
                    .apikey(centralaApiKey)
                    .answer(responseNode)
                    .build());

        return getFlag(response.toString());
    }



}
