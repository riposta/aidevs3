package org.eu.dabrowski.aidev.task;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.UUID;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Component
@Slf4j
public class CentralaMapaTask extends AbstractTask {
    private static String TASK_NAME = "CentralaMapa";

    private ChatClient chatClient;

    @Value("classpath:/files/mapa.jpeg")
    private Resource mapFile;



    public CentralaMapaTask(OpenAiChatModel chatModel) {
        super(chatModel);
        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()),
                        new SimpleLoggerAdvisor())
                .build();
    }


    @Override
    @SneakyThrows
    Object compute(Object object) {
        String msgId = UUID.randomUUID().toString();

        var userMessage = new UserMessage("Explain what do you see on this pictures.  Each picture shows the map of town in poland. Describe briefly what you see to help user to find a proper city which map fragment is.\n" +
                "Each picture should be described by you as separate thread.",
                new Media(MimeTypeUtils.IMAGE_JPEG, mapFile));

        ChatResponse chatResponse = chatClient.prompt()
                .messages(userMessage)
                .advisors(advisorSpec -> advisorSpec
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, msgId))
                .call()
                .chatResponse();
        String responseContent = chatResponse.getResult().getOutput().getContent();

        chatResponse = chatClient.prompt()
                .user("Three of four pictures describe the same city. One is incorrect." +
                        "Please return which city it is. Please analyze carefully descriptions. " +
                        "Described streets must exist is the city." +
                        " Tip: The city has \"spichlerze i twierdze\". " +
                        "Return only the city name without any additional description.")
                .advisors(advisorSpec -> advisorSpec
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, msgId))
                .call()
                .chatResponse();
        responseContent = chatResponse.getResult().getOutput().getContent();


        return responseContent;
    }


}
