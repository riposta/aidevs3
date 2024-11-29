package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.client.FileClient;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.eu.dabrowski.aidev.model.xyz.NotesContent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class CentralaNotesTask extends AbstractTask {


    private ChatClient chatClient;
    private final FileClient fileClient;

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;

    private List<NotesContent> messages;



    @Qualifier("notes")
    private final VectorStore notesVectorStore;


    public CentralaNotesTask(OpenAiChatModel chatModel, FileClient fileClient, CentralaClient centralaClient,
                             VectorStore notesVectorStore) {
        super(chatModel);
        this.fileClient = fileClient;
        this.centralaClient = centralaClient;
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor())
                .build();
        this.notesVectorStore = notesVectorStore;
        messages = new ArrayList<>();
    }



    @SneakyThrows
    private String getDescriptionOfImageFile(File file) {

        var userMessage = new UserMessage("**Context**\n"+
                "- you are advanced OCR system\n" +
                "- you will get images with text \n" +
                "- sometimes text if cut or upside-down \n\n" +
                "**Task**\n" +
                "Your task is to recognize the text and print it without any additional words\n",
                new Media(MimeTypeUtils.IMAGE_PNG, new FileSystemResource(file)));
        ChatResponse chatResponse = chatClient.prompt()
                .messages(userMessage)
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getContent();
    }




    @SneakyThrows
    public void init() {
        byte[] fileBytes = fileClient.getFileAsBytes(centralaUrl + "/dane/notatnik-rafala.pdf");
        Path tempPath = Files.createTempFile("pdf", ".pdf");
        Files.write(tempPath, fileBytes, StandardOpenOption.WRITE);
        Path tempDir = Files.createTempDirectory("pdf2png");
        convertPdfToPng(tempPath.toString(), tempDir.toString());


        for(File file : tempDir.toFile().listFiles()) {
           messages.add(NotesContent.builder().content(getDescriptionOfImageFile(file)).build());
           log.info("File {} recognized.", file.getName() );

        }
//        String a = "";
//        ByteArrayResource resource = new ByteArrayResource(objectMapper.writeValueAsString(messages).getBytes(StandardCharsets.UTF_8));
//        JsonReader jsonReader = new JsonReader(resource,
//                 "content");
//        List<org.springframework.ai.document.Document> documents = jsonReader.get();
//
//        notesVectorStore.write(documents);

    }


    public void convertPdfToPng(String pdfPath, String outputDir) throws IOException {
        File pdfFile = new File(pdfPath);
        File outputDirectory = new File(outputDir);

        if (!outputDirectory.exists()) {
            outputDirectory.mkdir();
        }

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(page, 50);
                String outputFilePath = String.format("%s/page_%d.png", outputDir, page + 1);
                ImageIO.write(bufferedImage, "PNG", new File(outputFilePath));
                log.info("Page " + (page + 1) + " has been converted to " + outputFilePath);
            }
        }
    }



    @Override
    @SneakyThrows
    Object compute(Object object) {
        init();
        ObjectNode responseNode = objectMapper.createObjectNode();
        String questionsFileString = fileClient.getFileContent(centralaUrl + "/data/" + centralaApiKey + "/notes.json");
        JsonNode jsonNode = objectMapper.readTree(questionsFileString);
        String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(messages);
        Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            String value = field.getValue().asText();
            //List<org.springframework.ai.document.Document> results = notesVectorStore.similaritySearch(SearchRequest.query(value).withTopK(10));
            ChatResponse chatResponse = chatClient.prompt()
                    .user(value)
                    .system("**Kontekst**\n" +
                            "-W kwestii pytania: Do którego roku przeniósł się Rafał podaj rok 2019\n" +
                            "-W kwestii pytania:  Którego dnia Rafał ma spotkanie z Andrzejem? (format: YYYY-MM-DD)\" podaj 2024-11-12\n" +
                            "-W kwestii pytnia: Gdzie się chce dostać Rafał po spotkaniu z Andrzejem? Do Lubawy koło Grudziądza\n" +
                            "\n" +
                            "**Zadanie**\n" +
                            "Odpowiedz na pytanie na podstawie poniższej treści. Uwzględnij wszystkie fakty podane w tekście, w szczególności odwołania do wydarzeń. Wyświetl samą odpowiedź bez dodatkowych słow:\n" +
                            "**Treść**\n\n" + content)
                    .call()
                    .chatResponse();
            responseNode.put(name,chatResponse.getResult().getOutput().getContent());

        }



            JsonNode response = centralaClient.report(ReportRequest.builder()
                    .task("notes")
                    .apikey(centralaApiKey)
                    .answer(responseNode)
                    .build());

        return getFlag(response.toString());
    }



}
