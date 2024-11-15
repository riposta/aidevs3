package org.eu.dabrowski.aidev.model.xyz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArxivContent {
    private String title;
    private String content;
}