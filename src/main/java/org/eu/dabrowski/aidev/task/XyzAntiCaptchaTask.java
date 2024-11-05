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
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class XyzAntiCaptchaTask extends AbstractTask {
    private static String TASK_NAME = "XyzAntiCaptcha";

    private static String LOGIN = "tester";
    private static String PASSWORD = "574e112a";

    private final FileClient fileClient;

    private final ChatClient chatClient;

    public XyzAntiCaptchaTask(OpenAiChatModel chatModel, FileClient fileClient) {
        super(chatModel);
        this.fileClient = fileClient;
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }


    @Override
    @SneakyThrows
    Object compute(Object object) {
        String output = null;
        String pageContent = null;
        String url = "https://xyz.ag3nts.org";
        Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch();
        while (true) {
            Page page = browser.newPage();
            page.navigate(url);
            Locator login = page.getByPlaceholder("Login");
            login.fill(LOGIN);
            Locator password = page.getByPlaceholder("Password");
            password.fill(PASSWORD);
            Locator humanQuestion = page.locator("#human-question");
            humanQuestion.textContent();


            ChatResponse response = chatClient.prompt()
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
                output = getFlag(pageContent);
               break;
            }
            page.close();

        }
        browser.close();
        playwright.close();

        if(Objects.nonNull(pageContent)){
            Pattern pattern = Pattern.compile("<a href=\"(.*?)\">Version 0\\.13\\.4b<\\/a>", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(pageContent);
            if (matcher.find()) {
                url = url + matcher.group(1);
                log.info("\n" + fileClient.getFileContent(url));
            }
        }

        return output;
    }

    @Override
    public boolean accept(String taskName) {
        return taskName.equals(TASK_NAME);
    }
}
