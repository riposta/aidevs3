package org.eu.dabrowski.aidev.task;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.FileClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class XyzTask extends AbstractTask {
    private static String TASK_NAME = "xyz";

    private static String LOGIN = "tester";
    private static String PASSWORD = "574e112a";

    private final FileClient fileClient;

    public XyzTask(OpenAiChatModel chatModel, FileClient fileClient) {
        super(chatModel);
        this.fileClient = fileClient;
    }


    @Override
    @SneakyThrows
    Object compute(Object object) {
        String pageContent = null;
        String url = "https://xyz.ag3nts.org";
        Playwright playwright = Playwright.create();
        Browser browser = playwright.webkit().launch();
        while (true) {
            Page page = browser.newPage();
            page.navigate("https://xyz.ag3nts.org");
            Locator login = page.getByPlaceholder("Login");
            login.fill(LOGIN);
            Locator password = page.getByPlaceholder("Password");
            password.fill(PASSWORD);
            Locator humanQuestion = page.locator("#human-question");
            humanQuestion.textContent();


            ChatResponse response = ChatClient.builder(chatModel)
                    .build().prompt()
                    .user(humanQuestion.textContent())
                    .system("Response only with the year for the question. Do not add any additional text.")
                    .call()
                    .chatResponse();
            String year = response.getResult().getOutput().getContent();
            Locator answer = page.getByPlaceholder("Answer");
            answer.fill(year);
            Locator submitButton = page.locator("#submit");
            submitButton.click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            pageContent = page.content();
            if (!pageContent.contains("Anty-human captcha incorrect!")) {
               break;
            }

        }
        playwright.close();

        if(Objects.nonNull(pageContent)){
            Pattern pattern = Pattern.compile("<a href=\"(.*?)\">Version 0\\.13\\.4b<\\/a>", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(pageContent);
            if (matcher.find()) {
                url = url + matcher.group(1);
                log.info("\n" + fileClient.getFileContent(url));
            }
        }

        return null;
    }

    @Override
    public boolean accept(String taskName) {
        return taskName.equals(TASK_NAME);
    }
}
