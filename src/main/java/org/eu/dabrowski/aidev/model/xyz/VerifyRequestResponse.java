package org.eu.dabrowski.aidev.model.xyz;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyRequestResponse {
    private String text;
    private String msgID;
}
