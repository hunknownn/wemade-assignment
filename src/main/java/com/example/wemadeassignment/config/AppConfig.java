package com.example.wemadeassignment.config;

import com.example.wemadeassignment.domain.IpInfo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
@EnableConfigurationProperties({IpInfoProperties.class, AnalysisProperties.class})
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(IpInfoProperties properties) {
        return new RestTemplateBuilder()
                .connectTimeout(Duration.ofMillis(properties.timeout()))
                .readTimeout(Duration.ofMillis(properties.timeout()))
                .build();
    }

    /** IP 정보 조회 결과 로컬 캐시 — 반복 API 호출 방지 */
    @Bean
    public Cache<String, IpInfo> ipInfoCache(IpInfoProperties properties) {
        return Caffeine.newBuilder()
                .maximumSize(properties.cache().maxSize())
                .expireAfterWrite(properties.cache().expireAfterWrite(), TimeUnit.SECONDS)
                .build();
    }

    /** CSV 분석 비동기 처리용 스레드 풀 */
    @Bean(name = "analysisExecutor")
    public Executor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("analysis-");
        executor.initialize();
        return executor;
    }
}
