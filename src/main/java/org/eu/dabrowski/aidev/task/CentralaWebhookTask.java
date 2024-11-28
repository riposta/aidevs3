package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.client.FileClient;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.eu.dabrowski.aidev.model.webhook.WebhookRequest;
import org.eu.dabrowski.aidev.model.webhook.WebhookResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CentralaWebhookTask extends AbstractTask {
    private String SYSTEM_MESSAGE = "**Context**\n" +
            "- You are translator of a instructions\n" +
            "- Possible instructions are: left, right, down, up\n" +
            "- the possible movement is on map 4x4\n" +
            "- you start on upper left corner\n" +
            "- moving to the end right means: right, right,right\n" +
            "\n" +
            "**Your Task**\n" +
            "1. You will get instruction in Polish how the drone will move\n" +
            "2. You need to translate it to simple instructions: left, right, down, up\n" +
            "\n" +
            "**Examples**\n" +
            "Prompt: poleciałem jedno pole w prawo, a później na sam dół”\n" +
            "Your Answer: right,down,down,down\n" +
            "\n" +
            "Prompt: poleciałem do końca w prawo, a później na sam dół”\n" +
            "Your Answer: right,right,right,down,down,dowm\n" +
            "\n" +
            "Prompt: poleciałem dwa pola w prawo, a później jedno pole w dół”\n" +
            "Your Answer: right,right,down\n";




    private ChatClient chatClient;
    private final FileClient fileClient;

    private final CentralaClient centralaClient;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;

    private String[][] grid;

    @Value("${my-addresss}")
    private String address;




    public CentralaWebhookTask(OpenAiChatModel chatModel, FileClient fileClient, CentralaClient centralaClient) {
        super(chatModel);
        this.fileClient = fileClient;
        this.centralaClient = centralaClient;
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor())
                .build();
        grid = new String[4][4];
    }

    private void initializeGrid(){
        grid[0][0] = "start";
        grid[0][1] = "trawa";
        grid[0][2] = "drzewo";
        grid[0][3] = "dom";

        grid[1][0] = "trawa";
        grid[1][1] = "wiatrak";
        grid[1][2] = "trawa";
        grid[1][3] = "trawa";

        grid[2][0] = "trawa";
        grid[2][1] = "trawa";
        grid[2][2] = "skały";
        grid[2][3] = "drzewa";

        grid[3][0] = "góry";
        grid[3][1] = "góry";
        grid[3][2] = "samochód";
        grid[3][3] = "jaskinia";
    }



    @Override
    @SneakyThrows
    Object compute(Object object) {
        initializeGrid();
        JsonNode centralaReport = centralaClient.report(ReportRequest.builder()
                .task("webhook")
                .apikey(centralaApiKey)
                .answer(address+"/webhook")
                .build());
        return getFlag(centralaReport.toString());
    }

    public WebhookResponse handleRequest(WebhookRequest request) {
        ChatResponse chatResponse = chatClient.prompt()
                .user(request.getInstruction())
                .system(SYSTEM_MESSAGE)
                .call()
                .chatResponse();
        String response = parseCommand(chatResponse.getResult().getOutput().getContent());

        return WebhookResponse.builder().description(response).build();
    }

    public String parseCommand( String command) {
        String[] moves = command.split(",");
        int x = 0; // start position (row)
        int y = 0; // start position (column)

        for (String move : moves) {
            switch (move.trim()) {
                case "right":
                    y = Math.min(y + 1, grid[0].length - 1);
                    break;
                case "left":
                    y = Math.max(y - 1, 0);
                    break;
                case "down":
                    x = Math.min(x + 1, grid.length - 1);
                    break;
                case "up":
                    x = Math.max(x - 1, 0);
                    break;
            }
        }

        return grid[x][y];
    }


}
