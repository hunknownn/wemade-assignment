package com.example.wemadeassignment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ipinfo")
public record IpInfoProperties(
        String token,
        String baseUrl,
        int timeout,
        int maxRetries,
        CacheProperties cache
) {
    public record CacheProperties(
            long maxSize,
            long expireAfterWrite
    ) {
    }
}
