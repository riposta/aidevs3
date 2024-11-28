package org.eu.dabrowski.aidev.controller;

import lombok.RequiredArgsConstructor;
import org.eu.dabrowski.aidev.model.webhook.WebhookRequest;
import org.eu.dabrowski.aidev.model.webhook.WebhookResponse;
import org.eu.dabrowski.aidev.task.CentralaWebhookTask;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final CentralaWebhookTask centralaWebhookTask;

    @PostMapping("/webhook")
    public ResponseEntity<WebhookResponse> getFile(@RequestBody WebhookRequest request) throws Exception {
        WebhookResponse response = centralaWebhookTask.handleRequest(request);

        return ResponseEntity.ok()
                .body(response);
    }
}