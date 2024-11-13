package org.eu.dabrowski.aidev.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.eu.dabrowski.aidev.configuration.FeignClientConfiguration;
import org.eu.dabrowski.aidev.model.centrala.ReportRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@FeignClient(value = "centrala-client", url = "${client.centrala.url}", configuration = FeignClientConfiguration.class)
public interface CentralaClient {

    @PostMapping(value = "/report")
    JsonNode report(@RequestBody ReportRequest request);

}