package org.eu.dabrowski.aidev.client;

import org.eu.dabrowski.aidev.configuration.FeignClientConfiguration;
import org.eu.dabrowski.aidev.model.xyz.VerifyRequestResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@FeignClient(value = "xyz-client", url = "${client.xyz.url}", configuration = FeignClientConfiguration.class)
public interface XyzClient {

    @PostMapping(value = "/verify")
    VerifyRequestResponse verify(@RequestBody VerifyRequestResponse request);

}