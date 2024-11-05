package org.eu.dabrowski.aidev.task;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eu.dabrowski.aidev.client.XyzClient;
import org.eu.dabrowski.aidev.model.xyz.VerifyRequestResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Component
@Slf4j
public class Xyz0134bTask extends AbstractTask {
    private static String TASK_NAME = "Xyz0134b";

    private static String SYSTEM_MESSAGE = "You are chat assistant. " +
            "Reply for the question. Reply only in English.\n" +
            "###\n" +
            "Please have in mind below facts and include them while replying:\n" +
            "- the capital of Poland is Krakow\n" +
            "- the known number from the book The Hitchhiker's Guide to the Galaxy is 69\n" +
            "- The current year is 1999";


    private ChatClient chatClient;


    private final XyzClient xyzClient;

    public Xyz0134bTask(OpenAiChatModel chatModel, XyzClient xyzClient) {
        super(chatModel);
        this.xyzClient = xyzClient;
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()),
                        new SimpleLoggerAdvisor())
                .build();


    }


    @Override
    @SneakyThrows
    Object compute(Object object) {
        VerifyRequestResponse apiRequest = VerifyRequestResponse.builder()
                .msgID("0")
                .text("READY")
                .build();
        while (true) {
            VerifyRequestResponse apiResponse = xyzClient.verify(apiRequest);
            if (Objects.nonNull(getFlag(apiResponse.getText()))) {
                return apiResponse.getText();
            }
            ChatResponse chatResponse = chatClient.prompt()
                    .user(apiResponse.getText())
                    .system(SYSTEM_MESSAGE)
                    .advisors(advisorSpec -> advisorSpec
                            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, apiResponse.getMsgID()))
                    .call()
                    .chatResponse();
            String responseContent = chatResponse.getResult().getOutput().getContent();
            apiRequest.setText(responseContent);
            apiRequest.setMsgID(apiResponse.getMsgID());
            log.info(responseContent);

        }
    }

    @Override
    public boolean accept(String taskName) {
        return taskName.equals(TASK_NAME);
    }


}
