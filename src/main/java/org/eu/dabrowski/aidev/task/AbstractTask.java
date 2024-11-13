package org.eu.dabrowski.aidev.task;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        log.info("Task {} end. Result {}",taskName, taskOutput);

    }

    abstract Object compute(Object taskResponse);


    public String getFlag(String text) {
        String patternString = "\\{\\{FLG:(.*?)\\}\\}";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()){
            return matcher.group(1);
        } else {
            return null;
        }
    }

    public boolean accept(String taskName) {
        return this.getClass().getSimpleName()
                .replace("Task", "").equals(taskName);
    }
}
