package org.eu.dabrowski.aidev.task;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Data
public abstract class AbstractTask {
    protected final ChatModel chatModel;
    @Autowired
    protected ObjectMapper objectMapper;

    protected AbstractTask(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public void process(String taskName) {
        log.info("Task {} compute", taskName);
        Object taskOutput = compute(taskName);
        log.info("Task {} end", taskOutput);

    }

    abstract Object compute(Object taskResponse);

    public abstract boolean accept(String taskName);
}
