package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.client.FileClient;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;

@Component
@Slf4j
public class CentralaRobotIdTask extends AbstractTask {

    private static String SYSTEM_MESSAGE = "Generate image based on the description below:\n\n" ;
    private final FileClient fileClient;

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;

    private final OpenAiImageModel openaiImageModel;

    @Value("${file.storage.location}")
    private String fileStorageLocation;

    @Value("${my-addresss}")
    private String address;




    public CentralaRobotIdTask(OpenAiChatModel chatModel, FileClient fileClient,
                               CentralaClient centralaClient, OpenAiImageModel openaiImageModel) {
        super(chatModel);
        this.fileClient = fileClient;
        this.centralaClient = centralaClient;
        this.openaiImageModel = openaiImageModel;
    }


    @Override
    @SneakyThrows
    Object compute(Object object) {
        String fileContent = fileClient.getFileContent(centralaUrl + "/data/" + centralaApiKey
                + "/robotid.json");

        ImageResponse response = openaiImageModel.call(
                new ImagePrompt(SYSTEM_MESSAGE + fileContent,
                        OpenAiImageOptions.builder()
                                .withQuality("hd")
                                .withN(1)
                                .withResponseFormat("b64_json")
                                .withHeight(1024)
                                .withWidth(1024).build())

        );

        String fileName = "robotid.png";
        String output = response.getResult().getOutput().getB64Json();
        File file = new File(fileStorageLocation, fileName);
        byte[] decodedBytes = Base64.getDecoder().decode(output);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(decodedBytes);
        fos.close();

        JsonNode centralaReport = centralaClient.report(ReportRequest.builder()
                .task("robotid")
                .apikey(centralaApiKey)
                .answer(address+"/files/"+fileName)
                .build());
        return getFlag(centralaReport.toString());
    }



}
