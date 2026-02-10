package com.example.wemadeassignment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analysis")
public record AnalysisProperties(
        long maxFileSize,
        int maxLines,
        int topN
) {
}
