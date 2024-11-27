package org.eu.dabrowski.aidev.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.CentralaClient;
import org.eu.dabrowski.aidev.client.FileClient;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Component
@Slf4j
public class CentralaSoftoTask extends AbstractTask {

    private static String SYSTEM_MESSAGE = "**Context**\n" +
            "- There is a page containing company informations\n" +
            "- Page have many subpages which can be navigated using links\n" +
            "\n" +
            "\n" +
            "**Access to Additional Information**\n" +
            "You can retrieve error and order details from the CDOM system by calling its API. You have access to two functions: one to retrieve error details and the other to retrieve order details. You should use the function for retrieving order details if you want to access the payload for either a parent order or a sub-order.\n" +
            "\n" +
            "\n" +
            "**Your Task**\n" +
            "You are an advanced page crawler, you need to find answers for questions described below based on HTML page content and return them in format described below. If on the page there is no answer you need return only the link to open and it will be sent to you in the next user messsage.\n" +
            "\n" +
            "**Questions to answer**\n" +
            "%s" +
            "\n" +
            "\n" +
            "**Format of final answer**\n" +
            "\\{\n" +
            "    \"01\": \"zwięzła i konkretna odpowiedź na pierwsze pytanie\",\n" +
            "    \"02\": \"zwięzła i konkretna odpowiedź na drugie pytanie\",\n" +
            "    \"03\": \"zwięzła i konkretna odpowiedź na trzecie pytanie\"\n" +
            "\\}\n" +
            "\n" +
            "**Format of asking for new page content**\n" +
            "GET https://<some-page>\n" +
            "\n" +
            "**IMPORTANT**\n" +
            "Return final answer only when will get answers for all questions.\n" +
            "Base url of page is https://softo.ag3nts.org\n";


    private final CentralaClient centralaClient;

    private final FileClient fileClient;  ;

    @Value("${client.centrala.url}")
    private String centralaUrl;

    @Value("${client.centrala.api-key}")
    private String centralaApiKey;

    private final ChatClient chatClient;

    public CentralaSoftoTask(CentralaClient centralaClient, FileClient fileClient, OpenAiChatModel chatModel) {
        super(chatModel);
        this.centralaClient = centralaClient;
        this.fileClient = fileClient;
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new PromptChatMemoryAdvisor(new InMemoryChatMemory()),
                        new SimpleLoggerAdvisor())
                .build();
    }


    @Override
    @SneakyThrows
    Object compute(Object object) {
        String id = UUID.randomUUID().toString();
        String questions = fileClient.getFileContent(centralaUrl + "/data/" + centralaApiKey + "/softo.json");
        String finalSystemMessage = String.format(SYSTEM_MESSAGE, questions.replace("{", "\\{")
                .replace("}", "\\}"));
        String url = "https://softo.ag3nts.org/";
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch();
            Page page = browser.newPage();
            while (true) {
                page.navigate(url);
                List<String> links = (List<String>) page.evaluate("() => Array.from(document.querySelectorAll('a')).map(a => `${a.href}: ${a.textContent.trim()}`)");
                List<String> paragraphs = (List<String>) page.evaluate("() => Array.from(document.querySelectorAll('p')).map(p => p.textContent)");
                List<String> spans = (List<String>) page.evaluate("() => Array.from(document.querySelectorAll('span')).map(span => span.textContent)");
                ChatResponse chatResponse = chatClient.prompt()
                        .user("Links:\n" + links + "\nParagraphs:\n" + paragraphs +  "\nSpans:\n" + spans)
                        .system(finalSystemMessage)
                        .advisors(advisorSpec -> advisorSpec
                                .param(CHAT_MEMORY_CONVERSATION_ID_KEY, id))
                        .call()
                        .chatResponse();
                Thread.sleep(1000);
                String responseContent = chatResponse.getResult().getOutput().getContent();
                if (responseContent.startsWith("GET")) {
                    url = responseContent.replace("GET", "").trim();
                } else {
                    JsonNode jsonNode = objectMapper.readTree(responseContent);
                    JsonNode result = centralaClient.report(ReportRequest.builder()
                            .task("softo")
                            .apikey(centralaApiKey)
                            .answer(jsonNode)
                            .build());
                    return getFlag(result.toString());
                }
            }

        }
    }

}
